package com.ran.app.core

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToLong

/**
 * Keeps a prize pool's weights readable as percentages.
 *
 * A raw weight number is opaque: "46" means nothing to the person editing it,
 * and it silently depends on what else is in the pool. Treating the weight as a
 * *percentage* and holding the total at [TOTAL] removes both problems — weight
 * 10 then literally reads as "about 10%".
 *
 * Weights carry [DECIMALS] decimal places and every operation lands exactly on
 * a multiple of the quantum, because a pool summing to 99.98 is a pool whose
 * displayed percentages no longer describe the probabilities actually used by
 * the draw.
 *
 * Holding a constant total has a consequence worth naming: when one prize's
 * weight changes, the others must give up the difference. That redistribution is
 * the whole job of this file. It is proportional rather than equal so the
 * shape of the pool survives an edit — nudging one prize must not flatten the
 * deliberate weighting the rest of the catalogue depends on.
 */
object PrizeBalancer {

    /** Weights always sum to this, which is what makes them percentages. */
    const val TOTAL = 100.0

    /** Decimal places kept on every weight. */
    const val DECIMALS = 2

    /** Smallest representable step, i.e. 0.01. */
    const val QUANTUM = 0.01

    /**
     * No prize can be rarer than this, so nothing silently becomes impossible.
     *
     * With 18 prizes in a 100% pool this is far below any usable share; it only
     * bites on absurdly large pools, where over-filling the total beats handing
     * out zeros.
     */
    const val MIN_WEIGHT = 0.01

    private const val UNITS = 10_000L // 100.00 in hundredths

    /**
     * Rescales weights so they sum to [TOTAL], preserving their ratios.
     *
     * Works in hundredths rather than in floating point. Scaling at double
     * precision and rounding at the end accumulates drift across rows; the exact
     * integer form distributes [UNITS] hundredths and cannot drift at all.
     *
     * @param weights weights in any units and of any total
     * @return [weights] rescaled to sum to exactly [TOTAL]
     */
    fun normalize(weights: List<Double>): List<Double> {
        val n = weights.size
        if (n == 0) return emptyList()
        val positive = weights.map { it.coerceAtLeast(0.0) }
        val sourceSum = positive.sum()
        if (sourceSum <= 0.0) return evenSplit(n)

        // Integer hundredths per row, plus the leftover fractional part that
        // flooring threw away.
        val exactUnits = positive.map { w: Double -> w / sourceSum * UNITS.toDouble() }
        val shares = exactUnits
            .map { kotlin.math.floor(it).toLong().coerceAtLeast(1L) }
            .toMutableList()
        var drift = UNITS - shares.sum()

        val order = exactUnits.indices.sortedByDescending { i: Int ->
            exactUnits[i] - kotlin.math.floor(exactUnits[i])
        }
        if (drift > 0) {
            var i = 0
            while (drift > 0) {
                shares[order[i % order.size]]++
                drift--
                i++
            }
        } else if (drift < 0) {
            // Overshot: take hundredths back from the largest rows, never
            // dropping one below the floor.
            var i = 0
            while (drift < 0 && i < order.size * MAX_ROUNDS) {
                val idx = order[i % order.size]
                if (shares[idx] > 1L) {
                    shares[idx]--
                    drift++
                }
                i++
            }
        }
        return shares.map { it.toDouble() / 100.0 }
    }

    /**
     * Applies a new weight to one prize and redistributes the difference across
     * the rest.
     *
     * @param current weights of every prize
     * @param targetIndex index of the prize being edited
     * @param requestedWeight its new weight, clamped to a legal share
     * @return new weights, same length, summing to [TOTAL]
     */
    fun rebalance(current: List<Double>, targetIndex: Int, requestedWeight: Double): List<Double> {
        val n = current.size
        require(n > 0) { "empty pool" }
        require(targetIndex in 0 until n) { "target out of range" }
        if (n == 1) return listOf(TOTAL)

        val target = quantize(requestedWeight)
            .coerceIn(MIN_WEIGHT, TOTAL - (n - 1) * MIN_WEIGHT)
        val budgetUnits = (UNITS - (target * 100).roundToLong()).coerceAtLeast((n - 1).toLong())

        val otherIndices = current.indices.filter { it != targetIndex }
        val others = otherIndices.map { current[it].coerceAtLeast(0.0) }
        val othersSum = others.sum()

        val exactUnits = others.map { w: Double ->
            if (othersSum > 0.0) w / othersSum * budgetUnits.toDouble()
            else budgetUnits.toDouble() / others.size
        }
        val shares = exactUnits
            .map { kotlin.math.floor(it).toLong().coerceAtLeast(1L) }
            .toMutableList()
        var drift = budgetUnits - shares.sum()
        val order = exactUnits.indices.sortedByDescending { i: Int ->
            exactUnits[i] - kotlin.math.floor(exactUnits[i])
        }

        if (drift > 0) {
            var i = 0
            while (drift > 0) {
                shares[order[i % order.size]]++
                drift--
                i++
            }
        } else if (drift < 0) {
            var i = 0
            while (drift < 0 && i < order.size * MAX_ROUNDS) {
                val idx = order[i % order.size]
                if (shares[idx] > 1L) {
                    shares[idx]--
                    drift++
                }
                i++
            }
        }

        val result = MutableList(n) { 0.0 }
        order.forEach { slot -> result[otherIndices[slot]] = shares[slot] / 100.0 }
        // Written last so the rounding pass above can never move it.
        result[targetIndex] = target
        return result
    }

    /**
     * Weights for a newly added prize, with the existing pool giving up the
     * space proportionally.
     *
     * @param existing weights of the current pool
     * @param newWeight requested weight for the incoming prize
     */
    fun admit(existing: List<Double>, newWeight: Double): Pair<Double, List<Double>> {
        if (existing.isEmpty()) {
            return quantize(newWeight).coerceAtLeast(MIN_WEIGHT) to emptyList()
        }
        val granted = quantize(newWeight)
            .coerceIn(MIN_WEIGHT, TOTAL - existing.size * MIN_WEIGHT)
        val others = rebalance(
            current = existing + MIN_WEIGHT,
            targetIndex = existing.size,
            requestedWeight = granted
        ).dropLast(1)
        return granted to others
    }

    /**
     * Weights after one prize leaves, its share folded back into the rest.
     *
     * Without this the pool simply sums to less than 100 and every remaining
     * prize quietly becomes likelier than its number claims.
     *
     * @param remaining weights of the prizes that are staying
     * @return new weights for exactly [remaining], summing to [TOTAL]
     */
    fun release(remaining: List<Double>): List<Double> =
        if (remaining.isEmpty()) emptyList() else normalize(remaining)

    /** A sensible starting share for a newly created prize. */
    fun defaultWeight(): Double = 5.0

    /** Rounds to the representable precision. */
    fun quantize(value: Double): Double = (value * 100).roundToLong() / 100.0

    /** Formats for display, trimming trailing zeros but keeping real decimals. */
    fun format(value: Double): String {
        val quantized = quantize(value)
        return if (abs(quantized - quantized.toLong()) < QUANTUM / 2) {
            quantized.toLong().toString()
        } else {
            String.format("%.2f", quantized)
        }
    }

    private fun evenSplit(n: Int): List<Double> {
        val units = UNITS / n
        val remainder = UNITS - units * n
        return List(n) { i ->
            (if (i < remainder) units + 1 else units).toDouble() / 100.0
        }
    }

    private const val MAX_ROUNDS = 64

    /** True when two weight sets describe the same proportions, for tests. */
    fun sameShape(a: List<Double>, b: List<Double>): Boolean {
        if (a.size != b.size) return false
        val sa = a.sum().coerceAtLeast(1e-9)
        val sb = b.sum().coerceAtLeast(1e-9)
        return a.indices.all { abs(a[it] * sb - b[it] * sa) < sb / 200 }
    }
}
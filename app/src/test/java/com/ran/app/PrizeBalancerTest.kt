package com.ran.app

import com.ran.app.core.PrizeBalancer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for percentage weights and the automatic rebalancing around them.
 *
 * The invariant that matters is simple and absolute: weights always sum to 100.
 * It is easy to satisfy approximately and miss exactly — floor everything and you
 * are two short, round everything and you overshoot — and a pool that sums to 98
 * means the percentages shown in the editor no longer describe the probabilities
 * actually used by the draw.
 */
class PrizeBalancerTest {

    private val shipped = listOf(
        16.0, 16.0, 14.0, 6.0, 6.0, 5.0, 5.0, 4.0, 4.0, 4.0, 3.0, 5.0, 4.0, 2.0, 2.0, 1.0, 1.0, 2.0
    )

    @Test
    fun `the shipped catalogue already sums to one hundred`() {
        assertEquals(PrizeBalancer.TOTAL, shipped.sum(), 1e-6)
    }

    @Test
    fun `normalize lands exactly on one hundred`() {
        val cases = listOf(
            listOf(46.0, 44.0, 40.0, 17.0, 16.0, 15.0, 14.0, 12.0, 11.0, 10.0, 9.0, 14.0, 12.0, 7.0, 5.0, 4.0, 3.0, 2.0),
            listOf(1.0, 1.0, 1.0),
            listOf(50.0, 50.0),
            listOf(3.0, 3.0, 3.0, 3.0),
            listOf(7.0, 11.0, 13.0),
            listOf(1.0, 99.0),
            listOf(100.0),
            listOf(2.0, 2.0, 2.0, 2.0, 2.0, 2.0, 2.0, 2.0, 2.0, 2.0)
        )
        for (input in cases) {
            val out = PrizeBalancer.normalize(input)
            assertEquals("sum for $input", PrizeBalancer.TOTAL, out.sum(), 1e-6)
            assertEquals("length for $input", input.size, out.size)
            assertTrue("a prize fell below the floor: $out", out.all { it >= PrizeBalancer.MIN_WEIGHT })
        }
    }

    @Test
    fun `normalize preserves proportions`() {
        // Halving everything must land in the same place as doubling.
        val a = PrizeBalancer.normalize(listOf(46.0, 44.0, 40.0, 17.0, 16.0))
        val b = PrizeBalancer.normalize(listOf(92.0, 88.0, 80.0, 34.0, 32.0))
        assertTrue("shape changed: $a vs $b", PrizeBalancer.sameShape(a, b))
    }

    @Test
    fun `an all-zero pool does not divide by zero`() {
        val out = PrizeBalancer.normalize(listOf(0.0, 0.0, 0.0))
        assertEquals(PrizeBalancer.TOTAL, out.sum(), 1e-6)
    }

    @Test
    fun `an empty pool is handled`() {
        assertTrue(PrizeBalancer.normalize(emptyList()).isEmpty())
    }

    @Test
    fun `rebalance keeps the total and honours the requested weight`() {
        val out = PrizeBalancer.rebalance(shipped, targetIndex = 3, requestedWeight = 20.0)
        assertEquals(PrizeBalancer.TOTAL, out.sum(), 1e-6)
        assertEquals(20.0, out[3], 1e-9)
    }

    @Test
    fun `raising one prize lowers the others without deleting them`() {
        val before = shipped
        val out = PrizeBalancer.rebalance(before, targetIndex = 0, requestedWeight = 50.0)
        assertEquals(PrizeBalancer.TOTAL, out.sum(), 1e-6)
        assertEquals(50.0, out[0], 1e-9)
        // Every other prize must have given something up.
        val othersBefore = before.drop(1).sum()
        val othersAfter = out.drop(1).sum()
        assertEquals("others must absorb the whole difference",
            (othersBefore - (50 - before[0])).toDouble(), othersAfter, 1e-6)
        assertTrue("a prize vanished", out.drop(1).all { it >= PrizeBalancer.MIN_WEIGHT })
    }

    @Test
    fun `lowering one prize raises the others proportionally`() {
        val out = PrizeBalancer.rebalance(shipped, targetIndex = 0, requestedWeight = 2.0)
        assertEquals(PrizeBalancer.TOTAL, out.sum(), 1e-6)
        assertEquals(2.0, out[0], 1e-9)
        assertTrue("others did not grow", out.drop(1).sum() > shipped.drop(1).sum())
    }

    @Test
    fun `an impossible weight is clamped rather than accepted`() {
        // 100 would starve every other prize to zero.
        val out = PrizeBalancer.rebalance(shipped, targetIndex = 0, requestedWeight = 100.0)
        assertEquals(PrizeBalancer.TOTAL, out.sum(), 1e-6)
        assertTrue("clamped target was ${out[0]}", out[0] < 100)
        assertTrue("others were starved", out.drop(1).all { it >= PrizeBalancer.MIN_WEIGHT })
    }

    @Test
    fun `a zero or negative request is clamped up to the floor`() {
        val out = PrizeBalancer.rebalance(shipped, targetIndex = 0, requestedWeight = -5.0)
        assertEquals(PrizeBalancer.TOTAL, out.sum(), 1e-6)
        assertEquals(PrizeBalancer.MIN_WEIGHT, out[0], 1e-9)
    }

    @Test
    fun `rebalancing preserves the relative order of the untouched prizes`() {
        val out = PrizeBalancer.rebalance(shipped, targetIndex = 0, requestedWeight = 25.0)
        val rest = shipped.indices.drop(1).map { shipped[it] to out[it] }
        // 1元 is excluded; among the rest, a larger before-value should never end
        // up with a strictly smaller after-value than its smaller sibling.
        val sortedBefore = rest.sortedByDescending { it.first }
        for (i in 0 until sortedBefore.size - 1) {
            val (hiBefore, hiAfter) = sortedBefore[i]
            val (loBefore, loAfter) = sortedBefore[i + 1]
            if (hiBefore > loBefore) {
                assertTrue(
                    "ordering inverted: $hiBefore->$hiAfter vs $loBefore->$loAfter",
                    hiAfter >= loAfter || hiAfter == loAfter
                )
            }
        }
    }

    @Test
    fun `admitting a prize makes room for it`() {
        val (granted, others) = PrizeBalancer.admit(shipped, newWeight = 10.0)
        assertEquals(10.0, granted, 1e-9)
        assertEquals(shipped.size, others.size)
        assertEquals(PrizeBalancer.TOTAL, granted + others.sum(), 1e-6)
        assertTrue("existing prizes shrank", others.sum() < shipped.sum())
    }

    @Test
    fun `admitting into an empty pool works`() {
        val (granted, others) = PrizeBalancer.admit(emptyList(), newWeight = 7.0)
        assertEquals(7.0, granted, 1e-9)
        assertTrue(others.isEmpty())
    }

    @Test
    fun `admitting clamps a greedy request`() {
        val (granted, others) = PrizeBalancer.admit(shipped, newWeight = 90.0)
        assertEquals(PrizeBalancer.TOTAL, granted + others.sum(), 1e-6)
        assertTrue("existing prizes were starved", others.all { it >= PrizeBalancer.MIN_WEIGHT })
    }

    @Test
    fun `a pool larger than the total still gets a usable answer`() {
        // 120 prizes cannot each hold 1% of 100 — integer percentages run out.
        // The answer must degrade gracefully rather than return zeros, which
        // would delete 120 prizes from the draw at once.
        val many = List(120) { 1.0 }
        val out = PrizeBalancer.normalize(many)
        assertEquals(120, out.size)
        assertTrue("a prize became impossible", out.all { it >= PrizeBalancer.MIN_WEIGHT })
    }
}
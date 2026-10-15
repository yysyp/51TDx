package com.ran.app.core

import kotlin.math.pow
import kotlin.random.Random

/**
 * Central reward economy.
 *
 * Design notes grounded in behavioural science (each is labelled so the intent
 * survives future edits):
 *
 * - **Variable-ratio reinforcement (Skinner):** the lottery uses a weighted
 *   random draw, so reward frequency is unpredictable. This sustains
 *   responding far better than a fixed schedule.
 * - **Goal-gradient effect (Kivetz):** progress bars accelerate visually near
 *   completion, which is why [progressRatio] is eased rather than linear.
 * - **Endowed-progress effect (Nunes & Drèze):** the daily star goal starts at
 *   a non-zero baseline, so the user feels they already have momentum.
 * - **Streak continuity / loss aversion (Kahneman):** daily streaks are tracked
 *   explicitly because breaking a streak feels like losing something.
 * - **Self-determination (Deci & Ryan):** praise targets competence and
 *   autonomy, never guilt. Messages describe what the user did, not what they failed to do.
 * - **Zeigarnik effect:** open plans stay visible on the home screen so an
 *   unfinished task keeps mild attentional tension until it is closed.
 *
 * Star costs are set so a modest daily effort (three check-ins) funds one draw.
 * The economy must feel generous: a reward that is hard to reach stops
 * behaving as a reward.
 */
object RewardEngine {

    /** Stars granted per plan check-in. */
    const val STAR_CHECK_IN = 3

    /** Bonus granted once a plan reaches its target. */
    const val STAR_PLAN_COMPLETE = 20

    /** Stars granted for finishing a single todo. */
    const val STAR_TODO_DONE = 5

    // ---- Variable-ratio reward on reviews ----
    //
    // A fixed payoff is learnable and therefore boring once known. An
    // unpredictable one keeps behaviour alive long after the novelty of the
    // feature wears off: this is the same variable-ratio schedule that makes
    // slot machines sticky, applied to something worth doing. The chance is
    // deliberately *not* shown as a progress bar, because predictability is
    // exactly what destroys the effect.

    /** Probability that a completed review pays double. */
    const val REVIEW_BONUS_CHANCE = 0.15f

    /** Payoff multiplier when the bonus fires. */
    const val REVIEW_BONUS_MULTIPLIER = 2

    /**
     * Rolls the review bonus.
     *
     * Failure is excluded upstream: "you forgot, but here is a reward anyway"
     * would teach that forgetting is profitable, which corrupts the very signal
     * the scheduler depends on.
     */
    fun rollReviewBonus(random: Random = Random.Default): Boolean =
        random.nextFloat() < REVIEW_BONUS_CHANCE

    /** Baseline stars the daily goal starts from (endowed-progress effect). */
    const val DAILY_GOAL_BASE = 10

    /** Daily star target. Reached easily, then the day reads as "won". */
    const val DAILY_GOAL_TARGET = 60

    /** Weighted pick honouring each prize's [com.ran.app.data.local.PrizeEntity.weight]. */
    fun <T> weightedPick(items: List<T>, weightOf: (T) -> Int, random: Random = Random.Default): T? {
        if (items.isEmpty()) return null
        val sanitized = items.map { weightOf(it).coerceAtLeast(1) }
        val total = sanitized.sum()
        if (total <= 0) return items.random(random)
        var roll = random.nextInt(total)
        items.forEachIndexed { index, item ->
            roll -= sanitized[index]
            if (roll < 0) return item
        }
        return items.last()
    }

    /**
     * Linear ratio for storage/reporting; UI should prefer [easedProgress] to
     * exploit the goal-gradient effect.
     */
    fun progressRatio(current: Int, target: Int): Float {
        if (target <= 0) return 0f
        return (current.toFloat() / target.toFloat()).coerceIn(0f, 1f)
    }

    /**
     * Goal-gradient easing: movement per unit of progress grows as the target
     * approaches, so the bar visibly "runs" toward the finish line.
     */
    fun easedProgress(current: Int, target: Int): Float {
        val raw = progressRatio(current, target)
        return (1f - (1f - raw).pow(2.2f)).coerceIn(0f, 1f)
    }

    /**
     * Daily goal ratio with a free starting endowment (endowed-progress effect).
     *
     * The base is an outright gift: at [starsToday] == 0 the bar is already
     * part-filled, so the day never opens at a blank 0% — the user starts with
     * visible momentum and something to protect.
     */
    fun dailyGoalRatio(starsToday: Int): Float {
        val effective = (starsToday + DAILY_GOAL_BASE).coerceIn(0, DAILY_GOAL_TARGET)
        return progressRatio(effective, DAILY_GOAL_TARGET)
    }

    /**
     * Level curve.
     *
     * A closed-form inverse (`pow` + `toInt`) cannot be made exactly
     * self-consistent after integer truncation: the level a threshold maps back
     * to is always one too low, which shows up as a level bar jumping straight
     * to ~100% right after a level-up. So thresholds are computed once into a
     * table and the level is a binary search over it. Both directions therefore
     * agree by construction.
     *
     * The curve is a power law, so early levels arrive fast (fast early wins)
     * while later ones stay meaningful.
     */
    private const val LEVEL_SCALE = 60.0
    private const val LEVEL_EXPONENT = 0.62
    private const val MAX_LEVEL = 100

    /** Cumulative stars required to reach each level; index 0 is level 1. */
    private val LEVEL_THRESHOLDS: IntArray = IntArray(MAX_LEVEL + 1).also { table ->
        table[0] = 0
        for (level in 1..MAX_LEVEL) {
            val previous = table[level - 1]
            val raw = Math.pow(level.toDouble(), 1.0 / LEVEL_EXPONENT) * LEVEL_SCALE
            // Always strictly greater than the previous threshold, so every
            // level is reachable and progress never divides by zero.
            table[level] = maxOf(raw.toInt(), previous + 1)
        }
    }

    /** Cumulative stars required to reach [level] (level 1 starts at 0). */
    fun starsRequiredFor(level: Int): Int =
        LEVEL_THRESHOLDS[level.coerceIn(1, MAX_LEVEL)]

    /** Inverse of [starsRequiredFor]. */
    fun levelFor(totalStars: Int): Int {
        if (totalStars <= 0) return 1
        // Binary search for the highest level whose threshold is still <= stars.
        var low = 1
        var high = MAX_LEVEL
        while (low < high) {
            val mid = (low + high + 1) / 2
            if (LEVEL_THRESHOLDS[mid] <= totalStars) low = mid else high = mid - 1
        }
        return low
    }

    /** Progress within the current level. */
    fun levelProgress(totalStars: Int): Float {
        val level = levelFor(totalStars)
        val currentFloor = starsRequiredFor(level)
        val nextFloor = starsRequiredFor(level + 1)
        if (nextFloor <= currentFloor) return 0f
        return progressRatio(totalStars - currentFloor, nextFloor - currentFloor)
    }

    /** Encouragement copy, chosen by progress band. Competence-framing only. */
    fun encouragement(ratio: Float, streakDays: Int): String = when {
        streakDays >= 7 -> "$streakDays 天连续达成，习惯已经成型了。"
        ratio >= 1f -> "今日目标已完成，你已经拿到今天全部奖励。"
        ratio >= 0.75f -> "就差一点点，保持这个节奏。"
        ratio >= 0.4f -> "进度过半了，继续保持。"
        ratio > 0f -> "已经开始行动，先完成一次打卡。"
        else -> "从最小的一步开始就行。"
    }

    /** Level-up copy shown when a threshold is crossed. */
    fun levelUpMessage(level: Int): String = when {
        level >= 10 -> "已抵达Lv.$level，长期主义者的领域。"
        level >= 5 -> "已抵达 Lv.$level，节奏非常稳。"
        else -> "升至 Lv.$level，继续推进。"
    }
}
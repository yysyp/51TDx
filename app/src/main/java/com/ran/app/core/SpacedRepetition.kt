package com.ran.app.core

import kotlin.math.roundToInt

/**
 * Spaced-repetition scheduler implementing SuperMemo-2 (SM-2).
 *
 * Why SM-2 rather than a fixed interval ladder:
 *
 * A fixed table (1/2/4/7/15/30 days) assumes every item is equally hard. In
 * practice "光合作用的场所" and "线粒体 vs 叶绿体的区别" do not decay at the
 * same rate, and a single child does not forget at the same rate across
 * subjects. SM-2 tracks a per-item **easiness factor (EF)**: every time the
 * item is successfully retrieved, the next wait grows by EF; every failure
 * proves the item is harder than estimated, so EF drops and the item comes
 * back sooner. The schedule adapts to the learner instead of the other way
 * round.
 *
 * Three deliberate deviations from the canonical algorithm, because this app
 * schedules school knowledge for a child on a daily rhythm:
 *
 * 1. The second interval is 3 days, not SM-2's 6. The canonical value assumes
 *    adult learners reviewing dense foreign-language cards; a 6-day second
 *    hop reliably overshoots a ten-year-old's school material, and the cost of
 *    overshooting (full re-learning) is far higher than one extra review.
 * 2. Failure resets the repetition counter but keeps EF. A lapse tells you the
 *    item is *harder than you thought* (already priced into EF) — it should not
 *    also erase progress already demonstrated. Resetting EF too makes items
 *    spiral back to daily forever and turns the queue into punishment.
 * 3. Graduation is defined by interval length (>= 21 days), not by a fixed
 *    number of reviews. "Mastered" should mean "retention is stable", and
 *    interval length is the observable proxy for that. Counting to a magic
 *    number of reviews rewards the *counter*, not the memory.
 *
 * All wait times are whole local days. Review attaches to a day ("after
 * dinner"), not to a clock offset, and day granularity makes it impossible to
 * review the same card twice within one calendar day by construction — which
 * removes the obvious way to farm stars.
 *
 * Everything here is a pure function over plain values, so the whole schedule
 * can be unit-tested without a database or a device.
 */
object SpacedRepetition {

    /**
     * How well the card came back during a review.
     *
     * The numeric [score] is the SM-2 `q` (0-5). Only three points are offered
     * in the UI: asking a child to discriminate six levels of "how well do you
     * know this" is the kind of friction that kills habits, and the marginal
     * scheduling accuracy is not worth the drop-off.
     */
    enum class Quality(val score: Int, val label: String, val emoji: String) {
        /** Nothing came back. Full re-learning, item returns tomorrow. */
        FORGOT(score = 1, label = "完全忘了", emoji = "🫥"),

        /** Recognition without recall — soothing, but it does not consolidate. */
        FUZZY(score = 3, label = "有点模糊", emoji = "🌫️"),

        /** Recalled unaided. This is the retrieval that strengthens memory. */
        RECALL(score = 5, label = "想起来了", emoji = "💡")
    }

    /**
     * Chunk-size difficulty chosen when recording.
     *
     * This is a *learning* parameter, not just a star multiplier: a hard item
     * starts with a lower easiness factor, so it comes back sooner and more
     * often. The child is effectively telling the scheduler "this one will need
     * more help", which is the desirable-difficulty principle made explicit —
     * the hardest card is the one where effort pays off most.
     */
    enum class Difficulty(val level: Int, val label: String, val initialEase: Float) {
        EASY(level = 1, label = "简单", initialEase = 2.6f),
        NORMAL(level = 2, label = "中等", initialEase = 2.35f),
        HARD(level = 3, label = "有难度", initialEase = 2.0f);

        companion object {
            fun of(level: Int?): Difficulty = entries.firstOrNull { it.level == level } ?: NORMAL
        }
    }

    /** Lower bound on easiness. Below this intervals collapse and nothing graduates. */
    const val MIN_EASE = 1.3f

    /** Upper bound on easiness, so consistently easy cards do not vanish from the queue. */
    const val MAX_EASE = 2.8f

    /** Second successful interval, in days. See class doc, deviation 1. */
    const val SECOND_INTERVAL_DAYS = 3

    /** Interval at which memory counts as stable enough to leave the daily queue. */
    const val MASTERY_INTERVAL_DAYS = 21

    /** Stars for recording a card, by difficulty. */
    fun starsForRecording(difficulty: Difficulty): Int = when (difficulty) {
        Difficulty.EASY -> 2
        Difficulty.NORMAL -> 3
        Difficulty.HARD -> 4
    }

    /**
     * Stars awarded for finishing a review, graded by how well it came back.
     *
     * Remembering pays roughly double what forgetting pays, while forgetting
     * still pays something: the goal is to make *honest* self-rating the most
     * rewarding strategy. Pay zero for failure and the child learns to press
     * "I remembered" to protect the stars — which converts a measurement
     * instrument into a lie generator and corrupts the schedule downstream.
     */
    fun starsForReview(difficulty: Difficulty, quality: Quality): Int = when (quality) {
        Quality.RECALL -> when (difficulty) {
            Difficulty.EASY -> 3
            Difficulty.NORMAL -> 4
            Difficulty.HARD -> 6
        }
        Quality.FUZZY -> when (difficulty) {
            Difficulty.EASY -> 2
            Difficulty.NORMAL -> 2
            Difficulty.HARD -> 3
        }
        Quality.FORGOT -> 1
    }

    /**
     * The schedule state of one card. Keeping consecutive successes and lapses
     * separate from total attempts lets the UI show honest labels ("5th review,
     * 2 resets") instead of a single number that hides failure.
     */
    data class Progress(
        val repetitions: Int = 0,
        val lapses: Int = 0,
        val intervalDays: Int = 0,
        val ease: Float = Difficulty.NORMAL.initialEase
    )

    /** Outcome of grading one review; carries everything the UI must report. */
    data class Outcome(
        val repetitions: Int,
        val lapses: Int,
        val intervalDays: Int,
        val ease: Float,
        val nextDueDay: String,
        val isMastered: Boolean,
        val stars: Int
    )

    /** Due day for a freshly recorded card: tomorrow. */
    fun initialDueDay(createdAtMillis: Long): String =
        TimeUtils.dayKeyPlusDays(createdAtMillis, 1L)

    /**
     * Applies one review grade and returns the new schedule.
     *
     * @param progress card state *before* this review
     * @param quality how well it came back
     * @param difficulty the card's chunk difficulty
     * @param reviewedAtMillis when the review happened; the next due day counts
     *        from here rather than from the scheduled date, so being late never
     *        punishes you with an interval you have not actually waited out.
     */
    fun applyReview(
        progress: Progress,
        quality: Quality,
        difficulty: Difficulty,
        reviewedAtMillis: Long
    ): Outcome {
        val succeeded = quality.score >= Quality.FUZZY.score

        val newEase = if (succeeded) {
            updateEase(progress.ease, quality.score)
        } else {
            // A lapse already costs a reset of the repetition streak; trimming
            // ease as well would double-charge the same mistake.
            progress.ease
        }

        val newRepetitions = if (succeeded) progress.repetitions + 1 else 0
        val newLapses = if (succeeded) progress.lapses else progress.lapses + 1
        val newInterval = nextInterval(progress.repetitions, progress.intervalDays, newEase, succeeded)

        return Outcome(
            repetitions = newRepetitions,
            lapses = newLapses,
            intervalDays = newInterval,
            ease = newEase,
            nextDueDay = TimeUtils.dayKeyPlusDays(reviewedAtMillis, newInterval.toLong()),
            isMastered = succeeded && newInterval >= MASTERY_INTERVAL_DAYS,
            stars = starsForReview(difficulty, quality)
        )
    }

    /**
     * SM-2 ease update: EF' = EF + (0.1 - (5-q) * (0.08 + (5-q) * 0.02)).
     *
     * A perfect recall nudges ease up by 0.1, so easy cards slowly drift out of
     * the queue. A fuzzy recall costs 0.14 — remembered, but clearly not
     * comfortably. Failure is not graded here because a lapse is handled by
     * resetting the repetition counter instead.
     */
    private fun updateEase(currentEase: Float, q: Int): Float {
        val delta = 0.1f - (5 - q) * (0.08f + (5 - q) * 0.02f)
        return (currentEase + delta).coerceIn(MIN_EASE, MAX_EASE)
    }

    /**
     * Next wait for a successful review.
     *
     * The `prev + 1` floor guarantees strict growth: without it, a card whose
     * ease has bottomed out could round to the same interval forever and be
     * re-reviewed on the identical schedule indefinitely.
     */
    private fun nextInterval(
        repetitionsBefore: Int,
        previousInterval: Int,
        ease: Float,
        succeeded: Boolean
    ): Int {
        if (!succeeded) return 1
        return when {
            repetitionsBefore == 0 -> 1
            repetitionsBefore == 1 -> SECOND_INTERVAL_DAYS
            else -> (previousInterval * ease).roundToInt().coerceAtLeast(previousInterval + 1)
        }
    }

    /**
     * Crude but honest 0-1 estimate of how consolidated a card is, derived from
     * how long it can currently survive without a review.
     *
     * Shown as a progress bar because the goal-gradient effect is real: people
     * work harder the closer they perceive themselves to a goal. A card at 80%
     * is visibly worth finishing, one at 10% looks like starting over.
     */
    fun memoryStrength(intervalDays: Int): Float =
        (intervalDays.toFloat() / MASTERY_INTERVAL_DAYS).coerceIn(0f, 1f)

    /** Days until [dueDay]; negative means already overdue. */
    fun daysUntilDue(dueDay: String, todayMillis: Long = TimeUtils.now()): Long =
        TimeUtils.daysBetweenKeys(TimeUtils.todayKey(todayMillis), dueDay)

    fun isMastered(intervalDays: Int): Boolean = intervalDays >= MASTERY_INTERVAL_DAYS
}

package com.ran.app

import com.ran.app.core.RewardEngine
import com.ran.app.core.SpacedRepetition
import com.ran.app.core.SpacedRepetition.Difficulty
import com.ran.app.core.SpacedRepetition.Quality
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import kotlin.random.Random

/**
 * The scheduler is the only component where a small arithmetic slip silently
 * produces the wrong queue, so it is pinned down end to end: a full simulated
 * run to mastery, the three branching rules, and the boundary behaviour.
 */
class SpacedRepetitionTest {

    private val zone: ZoneId = ZoneId.systemDefault()
    private val base: LocalDate = LocalDate.of(2026, 10, 13)

    private fun millisOf(day: LocalDate): Long =
        day.atStartOfDay(zone).toInstant().toEpochMilli()

    private fun keyOf(day: LocalDate): String = day.toString()

    private fun freshProgress(difficulty: Difficulty = Difficulty.NORMAL) =
        SpacedRepetition.Progress(ease = difficulty.initialEase)

    @Test
    fun `first review lands one day after recording`() {
        assertEquals(keyOf(base.plusDays(1)), SpacedRepetition.initialDueDay(millisOf(base)))
    }

    /**
     * Only the two literals the algorithm hard-codes are asserted as numbers;
     * beyond that the schedule is *multiplicative over a float ease*, so exact
     * expectations would encode rounding accidents rather than intent. The
     * properties that matter are: growth, monotonicity, and never stalling.
     */
    @Test
    fun `a perfect streak starts small then grows strictly`() {
        var progress = freshProgress()
        val seeded = listOf(1, SpacedRepetition.SECOND_INTERVAL_DAYS)
        seeded.forEachIndexed { index, days ->
            val out = SpacedRepetition.applyReview(progress, Quality.RECALL, Difficulty.NORMAL, millisOf(base))
            assertEquals("seeded interval at repetition $index", days, out.intervalDays)
            assertEquals(index + 1, out.repetitions)
            assertEquals(0, out.lapses)
            progress = SpacedRepetition.Progress(out.repetitions, out.lapses, out.intervalDays, out.ease)
        }

        var previous = progress.intervalDays
        repeat(6) {
            val out = SpacedRepetition.applyReview(progress, Quality.RECALL, Difficulty.NORMAL, millisOf(base))
            assertTrue("must keep growing: ${out.intervalDays} <= $previous", out.intervalDays > previous)
            assertTrue("ease must stay bounded", out.ease <= SpacedRepetition.MAX_EASE)
            previous = out.intervalDays
            progress = SpacedRepetition.Progress(out.repetitions, out.lapses, out.intervalDays, out.ease)
        }
        assertTrue("a sustained streak must eventually graduate", previous >= SpacedRepetition.MASTERY_INTERVAL_DAYS)
    }

    @Test
    fun `forgetting resets the streak but keeps the ease it already earned`() {
        var progress = freshProgress()
        for (i in 0..2) {
            val out = SpacedRepetition.applyReview(progress, Quality.RECALL, Difficulty.NORMAL, millisOf(base))
            progress = SpacedRepetition.Progress(out.repetitions, out.lapses, out.intervalDays, out.ease)
        }
        val easeBeforeFailure = progress.ease

        val failed = SpacedRepetition.applyReview(progress, Quality.FORGOT, Difficulty.NORMAL, millisOf(base.plusDays(30)))
        assertEquals(0, failed.repetitions)
        assertEquals(1, failed.lapses)
        assertEquals(1, failed.intervalDays)
        assertEquals(easeBeforeFailure, failed.ease)
        assertEquals(keyOf(base.plusDays(31)), failed.nextDueDay)
        assertFalse(failed.isMastered)
    }

    @Test
    fun `a lapse counts from the day you actually reviewed, not the day it was due`() {
        val progress = SpacedRepetition.Progress(repetitions = 0, ease = Difficulty.NORMAL.initialEase)
        val reviewedLate = millisOf(base.plusDays(9))
        val out = SpacedRepetition.applyReview(progress, Quality.RECALL, Difficulty.NORMAL, reviewedLate)
        assertEquals(keyOf(base.plusDays(10)), out.nextDueDay)
    }

    @Test
    fun `ease drifts up on recall and drifts down on fuzzy recall`() {
        val recalled = SpacedRepetition.applyReview(
            SpacedRepetition.Progress(), Quality.RECALL, Difficulty.NORMAL, millisOf(base)
        )
        val easedNormal = Difficulty.NORMAL.initialEase
        assertTrue("recall should raise ease", recalled.ease > easedNormal)

        val fuzzy = SpacedRepetition.applyReview(
            SpacedRepetition.Progress(), Quality.FUZZY, Difficulty.NORMAL, millisOf(base)
        )
        assertTrue("fuzzy should lower ease", fuzzy.ease < easedNormal)
        assertEquals(0, fuzzy.repetitions - 1)
        assertTrue(fuzzy.ease >= SpacedRepetition.MIN_EASE)
    }

    @Test
    fun `hard cards start tighter and therefore graduate later than easy ones`() {
        assertTrue(Difficulty.HARD.initialEase < Difficulty.NORMAL.initialEase)
        assertTrue(Difficulty.NORMAL.initialEase < Difficulty.EASY.initialEase)

        fun daysToMaster(difficulty: Difficulty): Int {
            var day = 0L
            var progress = freshProgress(difficulty)
            repeat(60) {
                val out = SpacedRepetition.applyReview(
                    progress, Quality.RECALL, difficulty, millisOf(base.plusDays(day))
                )
                day += out.intervalDays
                if (out.isMastered) return day.toInt()
                progress = SpacedRepetition.Progress(out.repetitions, out.lapses, out.intervalDays, out.ease)
            }
            return Int.MAX_VALUE
        }

        val easyDays = daysToMaster(Difficulty.EASY)
        val hardDays = daysToMaster(Difficulty.HARD)
        assertTrue("hard material must be revisited sooner: easy=$easyDays hard=$hardDays", hardDays > easyDays)
        assertTrue(hardDays < Int.MAX_VALUE)
    }

    @Test
    fun `mastery is declared by interval length alone`() {
        assertFalse(SpacedRepetition.isMastered(SpacedRepetition.MASTERY_INTERVAL_DAYS - 1))
        assertTrue(SpacedRepetition.isMastered(SpacedRepetition.MASTERY_INTERVAL_DAYS))
        assertTrue(SpacedRepetition.isMastered(SpacedRepetition.MASTERY_INTERVAL_DAYS + 60))
    }

    @Test
    fun `intervals grow strictly even when ease bottoms out`() {
        var progress = SpacedRepetition.Progress(
            repetitions = 2,
            intervalDays = 1,
            ease = SpacedRepetition.MIN_EASE
        )
        var previous = progress.intervalDays
        repeat(6) {
            val out = SpacedRepetition.applyReview(progress, Quality.FUZZY, Difficulty.HARD, millisOf(base))
            assertTrue("interval must grow: ${out.intervalDays} <= $previous", out.intervalDays > previous)
            previous = out.intervalDays
            progress = SpacedRepetition.Progress(out.repetitions, out.lapses, out.intervalDays, out.ease)
        }
    }

    @Test
    fun `remembering pays strictly more than forgetting for every difficulty`() {
        Difficulty.entries.forEach { d ->
            val recalled = SpacedRepetition.starsForReview(d, Quality.RECALL)
            val fuzzy = SpacedRepetition.starsForReview(d, Quality.FUZZY)
            val forgot = SpacedRepetition.starsForReview(d, Quality.FORGOT)
            assertTrue(recalled > fuzzy)
            assertTrue(fuzzy > forgot)
            assertTrue("failure must still pay something, or it invites lying", forgot > 0)
        }
        // Harder material must be the most profitable to record honestly.
        assertTrue(
            SpacedRepetition.starsForReview(Difficulty.HARD, Quality.RECALL) >
                SpacedRepetition.starsForReview(Difficulty.EASY, Quality.RECALL)
        )
    }

    @Test
    fun `review bonus fires about the advertised share of the time`() {
        val random = Random(20261013)
        val trials = 20_000
        var hits = 0
        repeat(trials) { if (RewardEngine.rollReviewBonus(random)) hits++ }
        val rate = hits.toDouble() / trials
        val target = RewardEngine.REVIEW_BONUS_CHANCE.toDouble()
        assertTrue("observed bonus rate $rate should sit near $target", kotlin.math.abs(rate - target) < 0.02)
    }

    @Test
    fun `memory strength is bounded by the mastery interval`() {
        assertEquals(0f, SpacedRepetition.memoryStrength(0))
        assertEquals(1f, SpacedRepetition.memoryStrength(SpacedRepetition.MASTERY_INTERVAL_DAYS))
        assertEquals(1f, SpacedRepetition.memoryStrength(SpacedRepetition.MASTERY_INTERVAL_DAYS * 4))
        assertTrue(SpacedRepetition.memoryStrength(10) in 0f..1f)
    }
}

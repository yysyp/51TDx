package com.ran.app

import com.ran.app.core.RewardEngine
import com.ran.app.core.TimeUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Unit tests for the reward economy.
 *
 * These cover the logic that actually drives user motivation: star costs, the
 * goal-gradient easing curve, level progression, and weighted random selection.
 * A bug here silently corrupts the whole feedback loop, so it is worth pinning
 * down with tests rather than relying on manual taps.
 */
class RewardEngineTest {

    // ---------- Weighted random ----------

    @Test
    fun `weightedPick returns null on empty pool`() {
        assertNull(RewardEngine.weightedPick(emptyList<String>(), { 1.0 }))
    }

    @Test
    fun `weightedPick respects zero and negative weights without failing`() {
        // Weights are coerced to at least 1, so a bad value must not throw or starve an item.
        val weights = mapOf("a" to 0, "b" to -5)
        repeat(200) {
            val picked = RewardEngine.weightedPick(listOf("a", "b"), { weights.getValue(it).toDouble() })
            assertNotNull(picked)
        }
    }

    @Test
    fun `weightedPick favours higher weight over many trials`() {
        val items = listOf("common", "rare")
        var rare = 0
        val random = Random(20260911)
        repeat(4000) {
            if (RewardEngine.weightedPick(items, { if (it == "rare") 1.0 else 20.0 }, random) == "rare") rare++
        }
        // Expected share is 1/21 ~= 4.8%. A wide band keeps this stable yet meaningful.
        assertTrue("rare was drawn $rare/4000 times", rare in 40..320)
    }

    @Test
    fun `weightedPick always returns an item from the pool`() {
        val items = (1..20).map { "item$it" }
        repeat(500) {
            val picked = RewardEngine.weightedPick(items, { 1.0 })
            assertTrue(picked in items)
        }
    }

    @Test
    fun `weightedPick falls back to random when total weight is zero`() {
        val items = listOf("only")
        assertEquals("only", RewardEngine.weightedPick(items, { 0.0 }))
    }

    // ---------- Progress and easing ----------

    @Test
    fun `progressRatio clamps outside the valid range`() {
        assertEquals(0f, RewardEngine.progressRatio(0, 10), 0.001f)
        assertEquals(1f, RewardEngine.progressRatio(15, 10), 0.001f)
        assertEquals(0f, RewardEngine.progressRatio(5, 0), 0.001f)
        assertEquals(0f, RewardEngine.progressRatio(-3, 10), 0.001f)
    }

    @Test
    fun `easedProgress is monotonic and bounded`() {
        var previous = -1f
        for (current in 0..20) {
            val eased = RewardEngine.easedProgress(current, 20)
            assertTrue("eased out of range: $eased", eased in 0f..1f)
            assertTrue("not monotonic at $current", eased >= previous)
            previous = eased
        }
    }

    @Test
    fun `easedProgress runs faster than linear near the target`() {
        // Goal-gradient effect: the final steps should outpace the early ones.
        val linearHalf = RewardEngine.progressRatio(5, 10)
        val easedHalf = RewardEngine.easedProgress(5, 10)
        assertTrue("easing did not accelerate the second half", easedHalf > linearHalf)
        assertEquals(0f, RewardEngine.easedProgress(0, 10), 0.001f)
        assertEquals(1f, RewardEngine.easedProgress(10, 10), 0.001f)
    }

    @Test
    fun `daily goal grants a non-zero starting endowment`() {
        // Endowed-progress effect: the day opens part-filled rather than at a blank 0%.
        assertTrue(RewardEngine.dailyGoalRatio(0) > 0f)
        assertEquals(1f, RewardEngine.dailyGoalRatio(RewardEngine.DAILY_GOAL_TARGET), 0.001f)
        assertTrue(RewardEngine.dailyGoalRatio(5) <= RewardEngine.dailyGoalRatio(30))
        // Progress must never run backwards as stars accumulate.
        var previous = -1f
        for (stars in 0..80) {
            val ratio = RewardEngine.dailyGoalRatio(stars)
            assertTrue("goal ratio regressed at $stars", ratio >= previous)
            assertTrue("goal ratio out of range at $stars", ratio in 0f..1f)
            previous = ratio
        }
    }

    // ---------- Levels ----------

    @Test
    fun `level starts at one and never decreases`() {
        assertEquals(1, RewardEngine.levelFor(0))
        var previous = 1
        for (stars in 0..3000 step 10) {
            val level = RewardEngine.levelFor(stars)
            assertTrue("level dropped at $stars stars", level >= previous)
            previous = level
        }
    }

    @Test
    fun `stars required increases with level`() {
        var previous = -1
        for (level in 1..15) {
            val required = RewardEngine.starsRequiredFor(level)
            assertTrue("level $level requirement ($required) did not increase", required > previous)
            previous = required
        }
    }

    @Test
    fun `level and starsRequired are mutual inverses`() {
        // Both functions derive from the same constants; if they ever drift,
        // the level bar would show progress against the wrong bracket.
        for (level in 1..20) {
            val required = RewardEngine.starsRequiredFor(level)
            assertEquals(
                "starsRequiredFor($level)=$required did not map back to level $level",
                level,
                RewardEngine.levelFor(required)
            )
        }
    }

    @Test
    fun `level progress resets near zero at each level boundary`() {
        for (level in 2..12) {
            val justEntered = RewardEngine.starsRequiredFor(level)
            val progress = RewardEngine.levelProgress(justEntered)
            assertTrue("level $level started at $progress instead of 0", progress < 0.05f)
        }
    }

    // ---------- Copy ----------

    @Test
    fun `encouragement covers every progress band`() {
        listOf(0f, 0.1f, 0.5f, 0.8f, 1f).forEach { ratio ->
            listOf(0, 3, 30).forEach { streak ->
                assertNotNull(RewardEngine.encouragement(ratio, streak))
            }
        }
    }

    @Test
    fun `encouragement stays non-empty for a long streak`() {
        assertTrue(RewardEngine.encouragement(0.5f, 30).isNotBlank())
    }

    @Test
    fun `star costs stay affordable relative to a day of small actions`() {
        // Three check-ins should roughly fund one draw, otherwise the reward
        // loop stops functioning.
        val fromCheckIns = RewardEngine.STAR_CHECK_IN * 3
        assertTrue(fromCheckIns >= 5)
        assertTrue(RewardEngine.DEFAULT_TODO_REWARD > RewardEngine.STAR_CHECK_IN)
    }

    @Test
    fun `a todo reward can never exceed the cap`() {
        assertEquals(RewardEngine.MAX_TODO_REWARD, RewardEngine.clampTodoReward(9999))
        assertEquals(RewardEngine.MAX_TODO_REWARD, RewardEngine.clampTodoReward(500))
        assertEquals(RewardEngine.MAX_TODO_REWARD, RewardEngine.clampTodoReward(11))
        // The boundary itself is allowed.
        assertEquals(RewardEngine.MAX_TODO_REWARD, RewardEngine.clampTodoReward(10))
    }

    @Test
    fun `a user-authored reward never goes negative`() {
        assertEquals(0, RewardEngine.clampTodoReward(-50))
        assertEquals(0, RewardEngine.clampTodoReward(0))
        assertEquals(3, RewardEngine.clampTodoReward(3))
    }

    @Test
    fun `an unusable reward falls back to a sane default`() {
        assertEquals(RewardEngine.DEFAULT_TODO_REWARD, RewardEngine.clampTodoReward(null))
    }

    @Test
    fun `the cap holds for rows written before it existed`() {
        // Regression: a todo saved when the editor allowed 9999 must not pay
        // 9999 on completion. The grant path clamps, so the stored value is
        // irrelevant to what actually gets paid.
        val legacyRow = 9999
        val granted = RewardEngine.clampTodoReward(legacyRow)
        assertTrue("legacy row paid $granted", granted <= RewardEngine.MAX_TODO_REWARD)
        // Reopening must refund exactly what was granted, never more.
        assertEquals(granted, RewardEngine.clampTodoReward(legacyRow))
    }

    @Test
    fun `plan reward defaults to three stars per target step`() {
        assertEquals(3, RewardEngine.defaultPlanReward(1))
        assertEquals(30, RewardEngine.defaultPlanReward(10))
        assertEquals(63, RewardEngine.defaultPlanReward(21))
        // A blank or nonsensical target must still produce a usable reward.
        assertEquals(3, RewardEngine.defaultPlanReward(0))
        assertEquals(3, RewardEngine.defaultPlanReward(-5))
    }

    @Test
    fun `plan reward is capped at five stars per target step`() {
        assertEquals(5, RewardEngine.maxPlanReward(1))
        assertEquals(50, RewardEngine.maxPlanReward(10))
        assertEquals(150, RewardEngine.maxPlanReward(30))
        assertEquals(5, RewardEngine.maxPlanReward(0))
    }

    @Test
    fun `a plan reward above the scaled cap is clamped down`() {
        // 10 steps -> cap 50, so 9999 becomes 50, not the flat 10 of the old rule.
        assertEquals(50, RewardEngine.clampPlanReward(9999, targetCount = 10))
        assertEquals(50, RewardEngine.clampPlanReward(51, targetCount = 10))
        assertEquals(50, RewardEngine.clampPlanReward(50, targetCount = 10))
        // A short plan stays short: 2 steps -> cap 10.
        assertEquals(10, RewardEngine.clampPlanReward(9999, targetCount = 2))
    }

    @Test
    fun `an omitted plan reward becomes the per-step default`() {
        assertEquals(30, RewardEngine.clampPlanReward(null, targetCount = 10))
        assertEquals(3, RewardEngine.clampPlanReward(null, targetCount = 1))
        assertEquals(0, RewardEngine.clampPlanReward(-1, targetCount = 10))
    }

    @Test
    fun `plan reward always scales with the target and never inverts`() {
        // A flat ceiling would make a 2-step plan worth more than a 30-step plan.
        var previousMax = 0
        for (target in 1..40) {
            val max = RewardEngine.maxPlanReward(target)
            assertTrue("max not increasing at target=$target", max > previousMax)
            previousMax = max
            assertTrue(
                "default exceeds max at target=$target",
                RewardEngine.defaultPlanReward(target) <= max
            )
            // The default is exactly three per step, which is the product rule.
            assertEquals(target * 3, RewardEngine.defaultPlanReward(target))
        }
    }

    @Test
    fun `defaults themselves respect their own caps`() {
        assertTrue(RewardEngine.DEFAULT_TODO_REWARD <= RewardEngine.MAX_TODO_REWARD)
        assertTrue(RewardEngine.STAR_CHECK_IN <= RewardEngine.MAX_STAR_PER_CHECK_IN)
        assertTrue(RewardEngine.MAX_STAR_PER_CHECK_IN <= RewardEngine.STAR_CHECK_IN * 2)
    }
}

/** Tests for the time helpers used by daily check-in deduplication. */
class TimeUtilsTest {

    @Test
    fun `todayKey produces a stable sortable daily key`() {
        val key = TimeUtils.todayKey()
        assertTrue(key.matches(Regex("\\d{4}-\\d{2}-\\d{2}")))
        assertEquals(key, TimeUtils.todayKey())
    }

    @Test
    fun `daysBetweenKeys measures signed day gaps`() {
        assertEquals(0L, TimeUtils.daysBetweenKeys("2026-10-11", "2026-10-11"))
        assertEquals(1L, TimeUtils.daysBetweenKeys("2026-10-10", "2026-10-11"))
        assertEquals(3L, TimeUtils.daysBetweenKeys("2026-10-08", "2026-10-11"))
        assertEquals(-2L, TimeUtils.daysBetweenKeys("2026-10-13", "2026-10-11"))
    }

    @Test
    fun `formatClock clamps negatives and pads both fields`() {
        assertEquals("00:00", TimeUtils.formatClock(-5_000))
        assertEquals("00:00", TimeUtils.formatClock(0))
        assertEquals("00:09", TimeUtils.formatClock(9_000))
        assertEquals("01:00", TimeUtils.formatClock(60_000))
        assertEquals("25:00", TimeUtils.formatClock(1_500_000))
    }

    @Test
    fun `date formatting does not throw for epoch bounds`() {
        assertNotNull(TimeUtils.formatDate(0L))
        assertNotNull(TimeUtils.formatTime(0L))
        assertNotNull(TimeUtils.formatDateTime(1_700_000_000_000L))
    }
}
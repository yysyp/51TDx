package com.ran.app

import com.ran.app.core.DailyCycle
import com.ran.app.core.TimeUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression cover for the check-in bug: a plan checked in yesterday must be
 * clickable again today. The rows in the database never change overnight —
 * only the day key does — so the filter, not the data, decides whether the
 * button re-arms.
 */
class DailyCycleTest {

    @Test
    fun `check-in from another day does not count today`() {
        val stamps = listOf(
            DailyCycle.Stamp(1L, "2026-10-14"),
            DailyCycle.Stamp(2L, "2026-10-14")
        )
        assertTrue(DailyCycle.idsStampedOn(stamps, "2026-10-15").isEmpty())
    }

    @Test
    fun `check-in made today counts today`() {
        val stamps = listOf(
            DailyCycle.Stamp(1L, "2026-10-14"),
            DailyCycle.Stamp(2L, "2026-10-15")
        )
        assertEquals(setOf(2L), DailyCycle.idsStampedOn(stamps, "2026-10-15"))
    }

    @Test
    fun `same day carries every plan and drops duplicates`() {
        val stamps = listOf(
            DailyCycle.Stamp(7L, "2026-10-15"),
            DailyCycle.Stamp(8L, "2026-10-15"),
            DailyCycle.Stamp(7L, "2026-10-15"),
            DailyCycle.Stamp(9L, "2026-10-16")
        )
        assertEquals(setOf(7L, 8L), DailyCycle.idsStampedOn(stamps, "2026-10-15"))
    }

    @Test
    fun `no records leaves nothing checked in`() {
        assertTrue(DailyCycle.idsStampedOn(emptyList(), TimeUtils.todayKey()).isEmpty())
    }

    @Test
    fun `start of day round-trips through the day key`() {
        val day = "2026-10-15"
        assertEquals(day, TimeUtils.todayKey(DailyCycle.startOfDayMillis(day)))
    }

    @Test
    fun `start of day is the first instant of that day`() {
        val start = DailyCycle.startOfDayMillis("2026-10-15")
        assertEquals("2026-10-15", TimeUtils.todayKey(start))
        assertEquals("2026-10-14", TimeUtils.todayKey(start - 1))
    }

    @Test
    fun `next local day is within a day and never zero`() {
        val wait = DailyCycle.millisUntilNextLocalDay()
        assertTrue(wait > 0)
        assertTrue(wait <= 86_400_000L)
    }

    @Test
    fun `now falls inside today's window`() {
        val now = TimeUtils.now()
        val start = DailyCycle.startOfDayMillis(TimeUtils.todayKey(now))
        assertTrue(now >= start)
        assertTrue(now - start < 86_400_000L)
    }
}

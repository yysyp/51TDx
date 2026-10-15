package com.ran.app.core

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Everything the UI needs in order to roll over at midnight.
 *
 * The bug this replaces was subtle: "today" was read once and then cached,
 * while the flows behind it only emit when the *database* changes. Nothing
 * changes in the database at midnight, so yesterday's answer survived into
 * today and every finished check-in stayed greyed out until some unrelated
 * write happened to refresh it.
 *
 * "Today" therefore has to be treated as a stream, not a value — the day
 * boundary arrives whether or not data moved.
 */
object DailyCycle {

    private val keyFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    /** A record stamped with the day it happened; enough to ask "already today?". */
    data class Stamp(val id: Long, val dayKey: String)

    /**
     * The ids stamped on exactly [dayKey]. Anything from another day is
     * ignored, which is what lets a completed check-in re-arm overnight
     * instead of staying disabled forever.
     */
    fun idsStampedOn(stamps: List<Stamp>, dayKey: String): Set<Long> =
        stamps.asSequence().filter { it.dayKey == dayKey }.map { it.id }.toSet()

    /** Epoch millis of 00:00 on [dayKey], in [zone]. */
    fun startOfDayMillis(dayKey: String, zone: ZoneId = ZoneId.systemDefault()): Long =
        java.time.LocalDate.parse(dayKey, keyFormatter)
            .atStartOfDay(zone)
            .toInstant()
            .toEpochMilli()

    /** Millis from [now] until the next local midnight, never below one second. */
    fun millisUntilNextLocalDay(
        now: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault()
    ): Long {
        val nextMidnight = Instant.ofEpochMilli(now)
            .atZone(zone)
            .toLocalDate()
            .plusDays(1)
            .atStartOfDay(zone)
            .toInstant()
            .toEpochMilli()
        return (nextMidnight - now).coerceAtLeast(1_000L)
    }
}

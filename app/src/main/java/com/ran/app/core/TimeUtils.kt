package com.ran.app.core

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

/**
 * Time helpers. All persisted timestamps are epoch milliseconds (UTC based);
 * formatting converts to the device zone at the edge.
 */
object TimeUtils {

    private val dateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val displayFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("M月d日")
    private val dateTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("M月d日 HH:mm")
    private val timeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    fun now(): Long = System.currentTimeMillis()

    /** Stable `yyyy-MM-dd` key used for daily check-in deduplication. */
    fun todayKey(millis: Long = now()): String =
        Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate().format(dateFormatter)

    /** `yyyy-MM-dd` key for the day [days] after [millis], in the device zone.
     *  Used by the review scheduler, which thinks in whole local days. */
    fun dayKeyPlusDays(millis: Long, days: Long): String =
        Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
            .plusDays(days).format(dateFormatter)

    fun localDate(millis: Long): LocalDate =
        Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()

    fun formatDate(millis: Long): String = localDate(millis).format(displayFormatter)

    fun formatTime(millis: Long): String =
        Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDateTime().format(timeFormatter)

    fun formatDateTime(millis: Long): String =
        Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDateTime().format(dateTimeFormatter)

    /** Whole days between two `yyyy-MM-dd` keys; negative when [from] is later. */
    fun daysBetweenKeys(from: String, to: String): Long =
        java.time.LocalDate.parse(to, dateFormatter).toEpochDay() -
            java.time.LocalDate.parse(from, dateFormatter).toEpochDay()

    /** Formats a duration as `mm:ss`, clamped at zero. */
    fun formatClock(millis: Long): String {
        val safe = millis.coerceAtLeast(0)
        val totalSeconds = TimeUnit.MILLISECONDS.toSeconds(safe)
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return "%02d:%02d".format(minutes, seconds)
    }
}
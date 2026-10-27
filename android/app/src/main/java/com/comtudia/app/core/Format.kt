package com.comtudia.app.core

import java.time.Duration
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Human-readable formatting shared by the dashboard, the settings preview and
 * notifications. The output matches the desktop app exactly, including the
 * en dash used in ranges.
 */
object Format {
    private const val EN_DASH = "\u2013"

    private val CLOCK: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH)
    private val DAY_CLOCK: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE HH:mm", Locale.ENGLISH)
    private val WEEKDAY_SHORT: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE", Locale.ENGLISH)
    private val DAY_NAME: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE", Locale.ENGLISH)

    /** Formats a duration as HH:MM:SS, truncating partial seconds. */
    fun countdown(d: Duration): String {
        if (d <= Duration.ZERO) return "00:00:00"
        val total = d.seconds
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return "${two(h)}:${two(m)}:${two(s)}"
    }

    /** Formats a duration for cards and labels, e.g. "17h 42m". */
    fun duration(d: Duration): String {
        if (d <= Duration.ZERO) return "0m"
        var minutes = roundToMinutes(d)
        if (minutes < 1) minutes = 1
        return hoursAndMinutes(minutes)
    }

    /** Formats a precise minute-ish duration, e.g. "2h" or "1h 30m". */
    fun durationCompact(d: Duration): String {
        if (d <= Duration.ZERO) return "0m"
        return hoursAndMinutes(roundToMinutes(d))
    }

    /** Formats an integer number of minutes as a duration. */
    fun minutes(m: Int): String = duration(Duration.ofMinutes(m.toLong()))

    /** Formats a time as 24-hour HH:MM. */
    fun clock(t: ZonedDateTime): String = t.format(CLOCK)

    /**
     * Formats minutes after midnight (which may overflow into following days)
     * as HH:MM.
     */
    fun clockMinutes(minutes: Int): String {
        val m = Math.floorMod(minutes, Plan.MINUTES_PER_DAY)
        return "${two((m / 60).toLong())}:${two((m % 60).toLong())}"
    }

    /** Formats two times as a range, noting a following day when needed. */
    fun range(start: ZonedDateTime, end: ZonedDateTime): String {
        val suffix = if (!sameDate(start, end)) " (+1)" else ""
        return clock(start) + EN_DASH + clock(end) + suffix
    }

    /** Formats minute offsets from the start of the eating window. */
    fun rangeMinutes(start: Int, end: Int): String {
        // Truncating division, like the desktop implementation.
        val suffix = if (end / Plan.MINUTES_PER_DAY > start / Plan.MINUTES_PER_DAY) " (+1)" else ""
        return clockMinutes(start) + EN_DASH + clockMinutes(end) + suffix
    }

    /** Formats a time relative to now, e.g. "Today 12:00". */
    fun dayClock(t: ZonedDateTime, now: ZonedDateTime): String = when {
        sameDate(t, now) -> "Today ${clock(t)}"
        sameDate(t, now.plusDays(1)) -> "Tomorrow ${clock(t)}"
        sameDate(t, now.minusDays(1)) -> "Yesterday ${clock(t)}"
        else -> t.format(DAY_CLOCK)
    }

    /** Formats a time for a sentence, e.g. "tomorrow at 12:00". */
    fun whenPhrase(t: ZonedDateTime, now: ZonedDateTime): String = when {
        sameDate(t, now) -> "today at ${clock(t)}"
        sameDate(t, now.plusDays(1)) -> "tomorrow at ${clock(t)}"
        sameDate(t, now.minusDays(1)) -> "yesterday at ${clock(t)}"
        else -> "${t.format(WEEKDAY_SHORT)} at ${clock(t)}"
    }

    /**
     * Names a date relative to now: Today, Tomorrow, Yesterday, or the weekday
     * name.
     */
    fun dayName(t: ZonedDateTime, now: ZonedDateTime): String = when {
        sameDate(t, now) -> "Today"
        sameDate(t, now.plusDays(1)) -> "Tomorrow"
        sameDate(t, now.minusDays(1)) -> "Yesterday"
        else -> t.format(DAY_NAME)
    }

    /** Reports whether two times fall on the same calendar date. */
    fun sameDate(a: ZonedDateTime, b: ZonedDateTime): Boolean = a.toLocalDate() == b.toLocalDate()

    private fun roundToMinutes(d: Duration): Long {
        val totalMillis = d.seconds * 1000L + d.nano / 1_000_000L
        // Round half away from zero; d is positive here.
        return (totalMillis + 30_000L) / 60_000L
    }

    private fun hoursAndMinutes(totalMinutes: Long): String {
        val h = totalMinutes / 60
        val m = totalMinutes % 60
        return when {
            h == 0L -> "${m}m"
            m == 0L -> "${h}h"
            else -> "${h}h ${m.toString().padStart(2, '0')}m"
        }
    }

    private fun two(n: Long): String = if (n < 10) "0$n" else n.toString()
}

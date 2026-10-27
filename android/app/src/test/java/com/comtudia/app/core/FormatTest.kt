package com.comtudia.app.core

import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FormatTest {
    private val utc: ZoneId = ZoneId.of("UTC")

    @Test
    fun countdown() {
        val cases = listOf(
            Duration.ofSeconds(-1) to "00:00:00",
            Duration.ZERO to "00:00:00",
            Duration.ofSeconds(1) to "00:00:01",
            Duration.ofMinutes(59).plusSeconds(59) to "00:59:59",
            Duration.ofHours(17).plusMinutes(42).plusSeconds(10) to "17:42:10",
            Duration.ofHours(23).plusMinutes(59).plusSeconds(59) to "23:59:59",
            Duration.ofHours(17).plusMinutes(42).plusSeconds(10).plusMillis(900) to "17:42:10",
        )
        for ((duration, want) in cases) {
            assertEquals("Countdown($duration)", want, Format.countdown(duration))
        }
    }

    @Test
    fun duration() {
        val cases = listOf(
            Duration.ofMinutes(-1) to "0m",
            Duration.ZERO to "0m",
            Duration.ofSeconds(30) to "1m",
            Duration.ofMinutes(1) to "1m",
            Duration.ofMinutes(59) to "59m",
            Duration.ofHours(1) to "1h",
            Duration.ofMinutes(90) to "1h 30m",
            Duration.ofHours(18) to "18h",
            Duration.ofHours(17).plusMinutes(42).plusSeconds(30) to "17h 43m",
        )
        for ((duration, want) in cases) {
            assertEquals("Duration($duration)", want, Format.duration(duration))
        }
    }

    @Test
    fun durationCompact() {
        val cases = listOf(
            Duration.ZERO to "0m",
            Duration.ofMinutes(45) to "45m",
            Duration.ofHours(1) to "1h",
            Duration.ofMinutes(90) to "1h 30m",
            Duration.ofHours(2) to "2h",
        )
        for ((duration, want) in cases) {
            assertEquals("DurationCompact($duration)", want, Format.durationCompact(duration))
        }
    }

    @Test
    fun minutes() {
        assertEquals("0m", Format.minutes(0))
        assertEquals("1h 30m", Format.minutes(90))
        assertEquals("18h", Format.minutes(18 * 60))
        assertEquals("0m", Format.minutes(-60))
    }

    @Test
    fun clockMinutesWraps() {
        val cases = listOf(
            0 to "00:00",
            90 to "01:30",
            750 to "12:30",
            1439 to "23:59",
            1440 to "00:00",
            1500 to "01:00",
            -60 to "23:00",
        )
        for ((minutes, want) in cases) {
            assertEquals("ClockMinutes($minutes)", want, Format.clockMinutes(minutes))
        }
    }

    @Test
    fun ranges() {
        val start = ZonedDateTime.of(2026, 10, 26, 12, 0, 0, 0, utc)
        val end = ZonedDateTime.of(2026, 10, 26, 14, 30, 0, 0, utc)
        assertEquals("12:00\u201314:30", Format.range(start, end))

        val next = ZonedDateTime.of(2026, 10, 27, 1, 0, 0, 0, utc)
        assertEquals("12:00\u201301:00 (+1)", Format.range(start, next))

        assertEquals("12:00\u201314:00", Format.rangeMinutes(12 * 60, 14 * 60))
        assertEquals("23:00\u201301:00 (+1)", Format.rangeMinutes(23 * 60, 25 * 60))
    }

    @Test
    fun dayClockAndWhen() {
        val now = ZonedDateTime.of(2026, 10, 26, 10, 0, 0, 0, utc)
        val today = ZonedDateTime.of(2026, 10, 26, 12, 0, 0, 0, utc)
        val tomorrow = ZonedDateTime.of(2026, 10, 27, 12, 0, 0, 0, utc)
        val yesterday = ZonedDateTime.of(2026, 10, 25, 18, 0, 0, 0, utc)
        val later = ZonedDateTime.of(2026, 10, 29, 8, 30, 0, 0, utc)

        assertEquals("Today 12:00", Format.dayClock(today, now))
        assertEquals("Tomorrow 12:00", Format.dayClock(tomorrow, now))
        assertEquals("Yesterday 18:00", Format.dayClock(yesterday, now))
        assertEquals("Thu 08:30", Format.dayClock(later, now))

        assertEquals("tomorrow at 12:00", Format.whenPhrase(tomorrow, now))
        assertEquals("today at 12:00", Format.whenPhrase(today, now))
        assertEquals("yesterday at 18:00", Format.whenPhrase(yesterday, now))
        assertEquals("Thu at 08:30", Format.whenPhrase(later, now))
    }

    @Test
    fun dayNameAndSameDate() {
        val now = ZonedDateTime.of(2026, 10, 26, 10, 0, 0, 0, utc)

        assertTrue(Format.sameDate(now, ZonedDateTime.of(2026, 10, 26, 23, 0, 0, 0, utc)))
        assertFalse(Format.sameDate(now, ZonedDateTime.of(2026, 10, 27, 0, 0, 0, 0, utc)))

        assertEquals("Today", Format.dayName(ZonedDateTime.of(2026, 10, 26, 12, 0, 0, 0, utc), now))
        assertEquals("Tomorrow", Format.dayName(ZonedDateTime.of(2026, 10, 27, 12, 0, 0, 0, utc), now))
        assertEquals("Yesterday", Format.dayName(ZonedDateTime.of(2026, 10, 25, 12, 0, 0, 0, utc), now))
        assertEquals("Thursday", Format.dayName(ZonedDateTime.of(2026, 10, 29, 12, 0, 0, 0, utc), now))
    }

    @Test
    fun clockUses24Hours() {
        assertEquals("00:05", Format.clock(ZonedDateTime.of(2026, 10, 26, 0, 5, 0, 0, utc)))
        assertEquals("18:00", Format.clock(ZonedDateTime.of(2026, 10, 26, 18, 0, 0, 0, utc)))
    }
}

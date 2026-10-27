package com.comtudia.app.ui

import com.comtudia.app.core.Plan
import com.comtudia.app.ui.components.TimelineSegment
import com.comtudia.app.ui.components.timelineDayMinutes
import com.comtudia.app.ui.components.timelineSegments
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM mirror of `internal/ui/timeline_test.go`. The desktop test only sees the
 * eating blocks; here [timelineSegments] also returns the fasting stretches that
 * fill the rest of the bar, so the eating subset is what must match the Go
 * expectations, while the whole list stays contiguous and covers the day.
 */
class TimelineTest {

    private val zone: ZoneId = ZoneOffset.UTC

    @Test
    fun segmentsForDefaultPlan() {
        val plan = Plan.default()
        val day = LocalDate.of(2026, 10, 26)

        val segments = timelineSegments(plan, day, zone)
        val eating = segments.filter { it.kind == TimelineSegment.Kind.EATING }

        assertEquals("eating segment count", 3, eating.size)
        assertArrayEquals(
            intArrayOf(12 * 60, 14 * 60, 16 * 60),
            eating.map { it.startMinutes }.toIntArray(),
        )
        assertArrayEquals(
            intArrayOf(14 * 60, 16 * 60, 18 * 60),
            eating.map { it.endMinutes }.toIntArray(),
        )

        assertEquals(0, segments.first().startMinutes)
        assertEquals(1440, segments.last().endMinutes)
        assertContiguousAndCovering(segments)
    }

    @Test
    fun segmentsCrossingMidnight() {
        val plan = Plan.default().copy(windowStartMinutes = 23 * 60)
        val day = LocalDate.of(2026, 10, 27)

        val segments = timelineSegments(plan, day, zone)
        val eating = segments.filter { it.kind == TimelineSegment.Kind.EATING }

        assertEquals("eating segment count", 4, eating.size)
        assertEquals("first segment starts at day start", 0, eating.first().startMinutes)
        assertEquals("last segment ends at day end", 1440, eating.last().endMinutes)
        assertContiguousAndCovering(segments)
    }

    @Test
    fun invalidPlanHasNoSegments() {
        val plan = Plan.default().copy(meals = 0)
        assertTrue(timelineSegments(plan, LocalDate.of(2026, 10, 26), zone).isEmpty())
    }

    @Test
    fun gapBetweenMealsIsFasting() {
        val plan = Plan.default().copy(eatingMinutes = 4 * 60, meals = 2, gapMinutes = 60)
        val day = LocalDate.of(2026, 10, 26)

        val segments = timelineSegments(plan, day, zone)
        val eating = segments.filter { it.kind == TimelineSegment.Kind.EATING }

        assertEquals(2, eating.size)
        assertEquals(listOf(12 * 60, 15 * 60), eating.map { it.startMinutes })
        assertEquals(listOf(14 * 60, 17 * 60), eating.map { it.endMinutes })

        val gap = segments.first {
            it.kind == TimelineSegment.Kind.FASTING && it.startMinutes == 14 * 60
        }
        assertEquals(14 * 60, gap.startMinutes)
        assertEquals(15 * 60, gap.endMinutes)
        assertContiguousAndCovering(segments)
    }

    @Test
    fun dayLengthFollowsDst() {
        val newYork = ZoneId.of("America/New_York")
        assertEquals(1440, timelineDayMinutes(LocalDate.of(2026, 3, 7), newYork))
        assertEquals(1380, timelineDayMinutes(LocalDate.of(2026, 3, 8), newYork))
        assertEquals(1500, timelineDayMinutes(LocalDate.of(2026, 11, 1), newYork))
    }

    private fun assertContiguousAndCovering(segments: List<TimelineSegment>) {
        assertTrue("segments should not be empty", segments.isNotEmpty())
        var cursor = 0
        for (segment in segments) {
            assertEquals("segments must cover the day without gaps", cursor, segment.startMinutes)
            assertTrue("segment must advance", segment.endMinutes > segment.startMinutes)
            cursor = segment.endMinutes
        }
        assertEquals(1440, cursor)
    }
}

package com.comtudia.app.core

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ScheduleTest {
    private val utc: ZoneId = ZoneId.of("UTC")

    @Test
    fun computeContiguousPlan() {
        val plan = Plan.default()
        val day = ZonedDateTime.of(2026, 10, 26, 0, 0, 0, 0, utc)

        run {
            val state = Schedule.compute(plan, day.plusHours(10))
            assertEquals(Phase.FASTING, state.phase)
            assertInstant(day.minusDays(1).plusHours(18), state.phaseStart)
            assertInstant(day.plusHours(12), state.phaseEnd)
            assertEquals(Duration.ofHours(2), state.remaining)
            assertProgress(16.0 / 18.0, state.progress)
            assertInstant(day.plusHours(12), state.nextMeal!!.start)
            assertInstant(day.plusHours(12), state.nextWindowStart!!)
        }

        run {
            val state = Schedule.compute(plan, day.plusHours(12))
            assertEquals(Phase.EATING, state.phase)
            assertEquals(1, state.currentMeal!!.index)
            assertInstant(day.plusHours(12), state.phaseStart)
            assertInstant(day.plusHours(18), state.phaseEnd)
            assertEquals(1, state.firstMealIndex)
            assertEquals(3, state.lastMealIndex)
            assertEquals(Duration.ofHours(6), state.remaining)
            assertEquals(0.0f, state.progress, 1e-6f)
            assertInstant(day.plusHours(14), state.nextMeal!!.start)
            assertInstant(day.plusDays(1).plusHours(12), state.nextEatingPhaseStart!!)
            assertInstant(day.plusDays(1).plusHours(12), state.nextWindowStart!!)
        }

        run {
            val state = Schedule.compute(plan, day.plusHours(14))
            assertEquals(Phase.EATING, state.phase)
            assertEquals(2, state.currentMeal!!.index)
            assertProgress(1.0 / 3.0, state.progress)
        }

        run {
            val state = Schedule.compute(plan, day.plusHours(18))
            assertEquals(Phase.FASTING, state.phase)
            assertInstant(day.plusHours(18), state.phaseStart)
            assertInstant(day.plusDays(1).plusHours(12), state.nextMeal!!.start)
            assertEquals(0.0f, state.progress, 1e-6f)
        }

        run {
            val state = Schedule.compute(plan, day.plusHours(18).minusSeconds(1))
            assertEquals(Phase.EATING, state.phase)
            assertEquals(Duration.ofSeconds(1), state.remaining)
        }
    }

    @Test
    fun computeWithGaps() {
        val plan = Plan.default().copy(gapMinutes = 60)
        val day = ZonedDateTime.of(2026, 10, 26, 0, 0, 0, 0, utc)

        run {
            val state = Schedule.compute(plan, day.plusHours(12))
            assertEquals(Phase.EATING, state.phase)
            assertEquals(1, state.currentMeal!!.index)
            assertEquals(1, state.firstMealIndex)
            assertEquals(1, state.lastMealIndex)
            assertInstant(day.plusHours(15), state.nextEatingPhaseStart!!)
        }

        run {
            val state = Schedule.compute(plan, day.plusHours(14).plusMinutes(30))
            assertEquals(Phase.FASTING, state.phase)
            assertInstant(day.plusHours(14), state.phaseStart)
            assertInstant(day.plusHours(15), state.phaseEnd)
            assertEquals(Duration.ofMinutes(30), state.remaining)
            assertProgress(0.5, state.progress)
            assertInstant(day.plusHours(15), state.nextMeal!!.start)
            assertEquals(2, state.firstMealIndex)
            assertEquals(2, state.lastMealIndex)
        }

        run {
            val state = Schedule.compute(plan, day.plusHours(15))
            assertEquals(Phase.EATING, state.phase)
            assertEquals(2, state.currentMeal!!.index)
            assertInstant(day.plusHours(18), state.nextEatingPhaseStart!!)
        }

        run {
            val state = Schedule.compute(plan, day.plusHours(20))
            assertEquals(Phase.FASTING, state.phase)
            assertInstant(day.plusHours(20), state.phaseStart)
            assertEquals(Duration.ofHours(16), state.longFast)
            assertEquals(Duration.ofHours(18), state.fastingTotal)
        }
    }

    @Test
    fun computeWindowCrossingMidnight() {
        val plan = Plan.default().copy(windowStartMinutes = 23 * 60)
        val day = ZonedDateTime.of(2026, 10, 26, 0, 0, 0, 0, utc)

        run {
            val state = Schedule.compute(plan, day.plusDays(1).plusMinutes(30))
            assertEquals(Phase.EATING, state.phase)
            assertNotNull(state.currentMeal)
            assertEquals(1, state.currentMeal!!.index)
            assertEquals(LocalDate.of(2026, 10, 26), state.currentMeal!!.day)
            assertInstant(day.plusHours(23), state.phaseStart)
            assertInstant(day.plusDays(1).plusHours(5), state.phaseEnd)
            assertInstant(day.plusDays(1).plusHours(23), state.nextWindowStart!!)
        }

        run {
            val state = Schedule.compute(plan, day.plusDays(1).plusHours(6))
            assertEquals(Phase.FASTING, state.phase)
            assertInstant(day.plusDays(1).plusHours(23), state.nextMeal!!.start)
            assertInstant(day.plusDays(1).plusHours(23), state.nextWindowStart!!)
        }
    }

    @Test
    fun computeScansAcrossYearBoundary() {
        val state = Schedule.compute(Plan.default(), ZonedDateTime.of(2027, 1, 1, 10, 0, 0, 0, utc))
        assertEquals(Phase.FASTING, state.phase)
        assertInstant(ZonedDateTime.of(2026, 12, 31, 18, 0, 0, 0, utc), state.phaseStart)
        assertInstant(ZonedDateTime.of(2027, 1, 1, 12, 0, 0, 0, utc), state.phaseEnd)
        assertInstant(ZonedDateTime.of(2027, 1, 1, 12, 0, 0, 0, utc), state.nextWindowStart!!)
    }

    @Test
    fun computeRejectsInvalidPlan() {
        try {
            Schedule.compute(Plan(meals = 0), ZonedDateTime.of(2026, 10, 26, 10, 0, 0, 0, utc))
            fail("expected ValidationException")
        } catch (e: ValidationException) {
            assertEquals("meals", e.field)
        }
    }

    @Test
    fun slotsForDateNormalizesWindowIntoFollowingDays() {
        val plan = Plan(eatingMinutes = 60, meals = 1, windowStartMinutes = Plan.MINUTES_PER_DAY + 90)
        val slots = Schedule.slotsForDate(plan, LocalDate.of(2026, 10, 26), utc)
        assertEquals(1, slots.size)
        assertEquals(LocalDate.of(2026, 10, 26), slots[0].day)
        assertEquals(LocalDate.of(2026, 10, 27), slots[0].start.toLocalDate())
        assertEquals("01:30", Format.clock(slots[0].start))
        assertEquals(1, slots[0].index)
    }

    @Test
    fun slotsForDateEmptyWithoutMeals() {
        assertEquals(
            emptyList<Slot>(),
            Schedule.slotsForDate(Plan(meals = 0), LocalDate.of(2026, 10, 26), utc),
        )
    }

    @Test
    fun slotsAcrossSpringForwardInNewYork() {
        assertSpringForwardSlots(ZoneId.of("America/New_York"), LocalDate.of(2026, 3, 8))
    }

    @Test
    fun slotsAcrossSpringForwardInBerlin() {
        assertSpringForwardSlots(ZoneId.of("Europe/Berlin"), LocalDate.of(2026, 3, 29))
    }

    @Test
    fun slotsAcrossFallBackInNewYork() {
        assertFallBackSlots(ZoneId.of("America/New_York"), LocalDate.of(2026, 11, 1))
    }

    @Test
    fun slotsAcrossFallBackInBerlin() {
        assertFallBackSlots(ZoneId.of("Europe/Berlin"), LocalDate.of(2026, 10, 25))
    }

    @Test
    fun slotsWithGapAcrossSpringForward() {
        for ((zoneName, day) in listOf(
            "America/New_York" to LocalDate.of(2026, 3, 8),
            "Europe/Berlin" to LocalDate.of(2026, 3, 29),
        )) {
            val zone = ZoneId.of(zoneName)
            val plan = Plan(eatingMinutes = 120, meals = 2, gapMinutes = 30, windowStartMinutes = 90)
            val slots = Schedule.slotsForDate(plan, day, zone)
            assertEquals("$zoneName: slot count", 2, slots.size)
            for (slot in slots) {
                assertEquals("$zoneName: slot ${slot.index} duration", Duration.ofHours(1), slot.duration())
            }
            assertEquals(
                "$zoneName: gap",
                Duration.ofMinutes(30),
                Duration.between(slots[0].end, slots[1].start),
            )
            assertTrue("$zoneName: meals overlap", slots[1].start.toInstant() >= slots[0].end.toInstant())
        }
    }

    @Test
    fun computeAcrossSpringForward() {
        val zone = ZoneId.of("America/New_York")
        val plan = Plan(eatingMinutes = 120, meals = 1, windowStartMinutes = 90)
        val state = Schedule.compute(plan, ZonedDateTime.of(2026, 3, 8, 3, 30, 0, 0, zone))

        assertEquals(Phase.EATING, state.phase)
        assertEquals(1, state.currentMeal!!.index)
        assertInstant(ZonedDateTime.of(2026, 3, 8, 1, 30, 0, 0, zone), state.phaseStart)
        assertInstant(ZonedDateTime.of(2026, 3, 8, 4, 30, 0, 0, zone), state.phaseEnd)
        assertEquals(Duration.ofHours(2), Duration.between(state.phaseStart, state.phaseEnd))
        assertEquals(Duration.ofHours(1), state.remaining)
        assertProgress(0.5, state.progress)
    }

    @Test
    fun computeAcrossFallBack() {
        val zone = ZoneId.of("Europe/Berlin")
        val plan = Plan(eatingMinutes = 120, meals = 1, windowStartMinutes = 90)
        val state = Schedule.compute(plan, ZonedDateTime.of(2026, 10, 25, 1, 45, 0, 0, zone))

        assertEquals(Phase.EATING, state.phase)
        assertEquals(1, state.currentMeal!!.index)
        assertInstant(ZonedDateTime.ofInstant(Instant.parse("2026-10-24T23:30:00Z"), zone), state.phaseStart)
        assertInstant(ZonedDateTime.ofInstant(Instant.parse("2026-10-25T01:30:00Z"), zone), state.phaseEnd)
        // Wall clock crosses the repeated hour but the meal is still two hours long.
        assertEquals(Duration.ofHours(2), Duration.between(state.phaseStart, state.phaseEnd))
        assertEquals(Duration.ofMinutes(105), state.remaining)
        assertProgress(0.125, state.progress)
        assertEquals("02:30", Format.clock(state.phaseEnd))
    }

    private fun assertSpringForwardSlots(zone: ZoneId, day: LocalDate) {
        val plan = Plan(eatingMinutes = 120, meals = 1, windowStartMinutes = 90)
        val slots = Schedule.slotsForDate(plan, day, zone)
        assertEquals(1, slots.size)
        assertEquals("01:30", Format.clock(slots[0].start))
        assertEquals("04:30", Format.clock(slots[0].end))
        assertEquals(Duration.ofHours(2), slots[0].duration())
        assertTrue(slots[0].end.toInstant() > slots[0].start.toInstant())
    }

    private fun assertFallBackSlots(zone: ZoneId, day: LocalDate) {
        val plan = Plan(eatingMinutes = 120, meals = 1, windowStartMinutes = 90)
        val slots = Schedule.slotsForDate(plan, day, zone)
        assertEquals(1, slots.size)
        assertEquals("01:30", Format.clock(slots[0].start))
        assertEquals("02:30", Format.clock(slots[0].end))
        assertEquals(Duration.ofHours(2), slots[0].duration())
    }

    private fun assertInstant(expected: ZonedDateTime, actual: ZonedDateTime) {
        assertEquals(expected.toInstant(), actual.toInstant())
    }

    private fun assertProgress(expected: Double, actual: Float) {
        assertTrue(
            "progress = $actual, want $expected",
            abs(actual.toDouble() - expected) < 1e-6,
        )
    }

    @Test
    fun currentMealIsNullWhileFasting() {
        val state = Schedule.compute(Plan.default(), ZonedDateTime.of(2026, 10, 26, 10, 0, 0, 0, utc))
        assertNull(state.currentMeal)
        assertEquals(Phase.FASTING, state.phase)
    }
}

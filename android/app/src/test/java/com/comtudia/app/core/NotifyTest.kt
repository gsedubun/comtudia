package com.comtudia.app.core

import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NotifyTest {
    private val utc: ZoneId = ZoneId.of("UTC")
    private val day: ZonedDateTime = ZonedDateTime.of(2026, 10, 26, 0, 0, 0, 0, utc)

    @Test
    fun contiguousMealsNotifyOncePerPhase() {
        val plan = Plan.default()
        val notifier = Notifier()

        assertNull("first update should prime", notifier.update(stateAt(plan, day.plusHours(10))))
        assertNull("same phase should be silent", notifier.update(stateAt(plan, day.plusHours(11))))

        assertEquals(
            Message("Eating window open", "Meals 1\u20133 until 18:00"),
            notifier.update(stateAt(plan, day.plusHours(12))),
        )
        assertNull(
            "contiguous meal boundary should be silent",
            notifier.update(stateAt(plan, day.plusHours(14))),
        )
        assertNull(
            "same eating phase should be silent",
            notifier.update(stateAt(plan, day.plusHours(17))),
        )

        assertEquals(
            Message("Fasting started", "Next eating tomorrow at 12:00 (in 18h)"),
            notifier.update(stateAt(plan, day.plusHours(18))),
        )
        assertNull(
            "same fasting phase should be silent",
            notifier.update(stateAt(plan, day.plusHours(23))),
        )
    }

    @Test
    fun gapsProduceSeparateMealPhases() {
        val plan = Plan.default().copy(gapMinutes = 60)
        val notifier = Notifier()

        assertNull(
            "first update should prime",
            notifier.update(stateAt(plan, day.plusHours(14).plusMinutes(30))),
        )

        assertEquals(
            Message("Eating time", "Meal 2 of 3 until 17:00"),
            notifier.update(stateAt(plan, day.plusHours(15))),
        )
        assertEquals(
            Message("Fasting started", "Next eating today at 18:00 (in 1h)"),
            notifier.update(stateAt(plan, day.plusHours(17))),
        )
        assertEquals(
            Message("Eating time", "Meal 3 of 3 until 20:00"),
            notifier.update(stateAt(plan, day.plusHours(18))),
        )
    }

    @Test
    fun singleMealUsesEatingTimeMessage() {
        val plan = Plan(eatingMinutes = 60, meals = 1, windowStartMinutes = 12 * 60)
        val notifier = Notifier()

        assertNull(notifier.update(stateAt(plan, day.plusHours(11))))
        assertEquals(
            Message("Eating time", "Meal 1 of 1 until 13:00"),
            notifier.update(stateAt(plan, day.plusHours(12))),
        )
    }

    @Test
    fun resetPrimesAgain() {
        val plan = Plan.default()
        val notifier = Notifier()

        assertNull(notifier.update(stateAt(plan, day.plusHours(10))))
        assertEquals(
            Message("Eating window open", "Meals 1\u20133 until 18:00"),
            notifier.update(stateAt(plan, day.plusHours(12))),
        )

        notifier.reset()
        assertNull(
            "reset should suppress message",
            notifier.update(stateAt(plan, day.plusHours(12))),
        )
        assertEquals(
            Message("Fasting started", "Next eating tomorrow at 12:00 (in 18h)"),
            notifier.update(stateAt(plan, day.plusHours(18))),
        )
    }

    private fun stateAt(plan: Plan, now: ZonedDateTime): ScheduleState = Schedule.compute(plan, now)
}

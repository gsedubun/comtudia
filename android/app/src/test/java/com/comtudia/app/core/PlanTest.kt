package com.comtudia.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test

class PlanTest {
    @Test
    fun defaultPlanIsValid() {
        val plan = Plan.default()
        assertNull(plan.validate())
        assertEquals(18 * 60, plan.fastingMinutes())
        assertEquals(6 * 60, plan.spanMinutes())
        assertEquals(18 * 60, plan.longFastMinutes())
        assertEquals(Plan.VERSION, plan.version)
        assertEquals(3, Plan.PRESETS.size)
        assertEquals(listOf("16:8", "18:6", "20:4"), Plan.PRESETS.map { it.name })
    }

    @Test
    fun validateMatrix() {
        data class Case(val name: String, val plan: Plan, val ok: Boolean)

        val cases = listOf(
            Case("default", Plan.default(), true),
            Case("no meals", Plan(eatingMinutes = 360, meals = 0, windowStartMinutes = 720), false),
            Case("too many meals", Plan(eatingMinutes = 360, meals = 9, windowStartMinutes = 720), false),
            Case("no eating", Plan(eatingMinutes = 0, meals = 1, windowStartMinutes = 720), false),
            Case("all day eating", Plan(eatingMinutes = 1440, meals = 3, windowStartMinutes = 720), false),
            Case("start before day", Plan(eatingMinutes = 360, meals = 3, windowStartMinutes = -1), false),
            Case("start after day", Plan(eatingMinutes = 360, meals = 3, windowStartMinutes = 1440), false),
            Case(
                "negative gap",
                Plan(eatingMinutes = 360, meals = 3, gapMinutes = -1, windowStartMinutes = 720),
                false,
            ),
            Case(
                "single meal gap",
                Plan(eatingMinutes = 360, meals = 1, gapMinutes = 60, windowStartMinutes = 720),
                false,
            ),
            Case("meal too short", Plan(eatingMinutes = 60, meals = 5, windowStartMinutes = 720), false),
            Case(
                "gaps overflow day",
                Plan(eatingMinutes = 360, meals = 3, gapMinutes = 600, windowStartMinutes = 720),
                false,
            ),
            Case(
                "gaps fill day",
                Plan(eatingMinutes = 360, meals = 3, gapMinutes = 540, windowStartMinutes = 0),
                false,
            ),
            Case(
                "gaps leave too little outside time",
                Plan(eatingMinutes = 1380, meals = 2, gapMinutes = 1, windowStartMinutes = 0),
                false,
            ),
            Case("max meals", Plan(eatingMinutes = 360, meals = Plan.MAX_MEALS, windowStartMinutes = 0), true),
            Case("max eating", Plan(eatingMinutes = 1380, meals = 3, windowStartMinutes = 0), true),
            Case("min eating", Plan(eatingMinutes = Plan.MIN_EATING, meals = 1, windowStartMinutes = 0), true),
        )

        for (case in cases) {
            val error = case.plan.validate()
            if (case.ok) {
                assertNull("${case.name}: want valid, got $error", error)
            } else {
                assertNotNull("${case.name}: want invalid", error)
            }
        }
    }

    @Test
    fun validationMessagesMatchDesktop() {
        assertValidation(
            Plan(eatingMinutes = 360, meals = 0),
            "meals",
            "meal count must be between 1 and 8",
        )
        assertValidation(
            Plan(eatingMinutes = 0, meals = 1),
            "eating",
            "eating time must be between 1h and 23h",
        )
        assertValidation(
            Plan(eatingMinutes = 360, meals = 3, windowStartMinutes = 1440),
            "window_start",
            "start time must be within the day",
        )
        assertValidation(
            Plan(eatingMinutes = 360, meals = 3, gapMinutes = -1),
            "gap",
            "gap cannot be negative",
        )
        assertValidation(
            Plan(eatingMinutes = 360, meals = 1, gapMinutes = 60),
            "gap",
            "a single meal cannot have a gap",
        )
        assertValidation(
            Plan(eatingMinutes = 60, meals = 5),
            "meals",
            "each meal must be at least 15 minutes",
        )
        assertValidation(
            Plan(eatingMinutes = 360, meals = 3, gapMinutes = 600),
            "gap",
            "the meal spread must be 23 hours or less",
        )
    }

    @Test
    fun requireValidThrows() {
        Plan.default().requireValid()
        try {
            Plan(meals = 0).requireValid()
            fail("expected ValidationException")
        } catch (e: ValidationException) {
            assertEquals("meals", e.field)
            assertEquals("meal count must be between 1 and 8", e.message)
        }
    }

    @Test
    fun mealRangesContiguous() {
        val ranges = Plan.default().mealRanges()
        assertEquals(3, ranges.size)
        assertEquals(
            listOf(
                MealRange(1, 0, 120, 120),
                MealRange(2, 120, 240, 120),
                MealRange(3, 240, 360, 120),
            ),
            ranges,
        )
    }

    @Test
    fun mealRangesWithGap() {
        val plan = Plan.default().copy(gapMinutes = 60)
        val ranges = plan.mealRanges()
        assertEquals(
            listOf(
                MealRange(1, 0, 120, 120),
                MealRange(2, 180, 300, 120),
                MealRange(3, 360, 480, 120),
            ),
            ranges,
        )
        assertEquals(480, plan.spanMinutes())
        assertEquals(16 * 60, plan.longFastMinutes())
    }

    @Test
    fun mealRangesGiveLeftoverMinutesToEarliestMeals() {
        val plan = Plan(eatingMinutes = 100, meals = 3, windowStartMinutes = 0)
        val ranges = plan.mealRanges()
        assertEquals(3, ranges.size)
        assertEquals(34, ranges[0].durationMinutes)
        assertEquals(33, ranges[1].durationMinutes)
        assertEquals(33, ranges[2].durationMinutes)
        assertEquals(100, ranges[2].endMinutes)
        assertEquals(0, ranges[0].startMinutes)
        assertEquals(34, ranges[1].startMinutes)
    }

    @Test
    fun mealRangesEmptyWhenImpossible() {
        assertEquals(emptyList<MealRange>(), Plan(meals = 0).mealRanges())
        assertEquals(emptyList<MealRange>(), Plan(eatingMinutes = 2, meals = 3).mealRanges())
    }

    @Test
    fun summary() {
        val plan = Plan.default()
        assertEquals("18h fast \u00b7 6h eat", plan.summary())
        assertEquals("18h fast \u00b7 6h eat", plan.copy(gapMinutes = 60, windowStartMinutes = 8 * 60).summary())
        assertEquals("23h fast \u00b7 1h eat", Plan(eatingMinutes = 60, meals = 1).summary())
        assertEquals("0h fast \u00b7 24h eat", Plan(eatingMinutes = 1440, meals = 3).summary())
    }

    private fun assertValidation(plan: Plan, field: String, message: String) {
        val error = plan.validate()
        assertNotNull("expected ${field} error", error)
        error!!
        assertEquals(field, error.field)
        assertEquals(message, error.message)
    }
}

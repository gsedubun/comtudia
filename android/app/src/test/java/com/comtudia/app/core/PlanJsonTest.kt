package com.comtudia.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test

class PlanJsonTest {
    @Test
    fun encodesDesktopCompatibleDocument() {
        val want = """
            {
              "version": 1,
              "eating_minutes": 360,
              "meals": 3,
              "gap_minutes": 0,
              "window_start_minutes": 720,
              "notifications": true,
              "run_in_tray": false
            }
        """.trimIndent() + "\n"

        assertEquals(want, PlanJson.encode(Plan.default()))
    }

    @Test
    fun roundTripsPlan() {
        val plan = Plan(
            eatingMinutes = 240,
            meals = 2,
            gapMinutes = 30,
            windowStartMinutes = 22 * 60,
            notifications = false,
            runInTray = true,
        )
        assertEquals(plan, PlanJson.decode(PlanJson.encode(plan)))
    }

    @Test
    fun missingFieldsKeepDesktopDefaults() {
        val decoded = PlanJson.decode("""{"eating_minutes": 480, "meals": 2}""")
        assertEquals(
            Plan(eatingMinutes = 480, meals = 2),
            decoded,
        )
        assertNull(decoded.validate())
    }

    @Test
    fun zeroVersionIsUpgraded() {
        assertEquals(Plan.VERSION, PlanJson.decode("""{"version": 0}""").version)
    }

    @Test
    fun newerVersionIsRejected() {
        try {
            PlanJson.decode("""{"version": 2}""")
            fail("expected PlanFormatException")
        } catch (e: PlanFormatException) {
            assertEquals("plan version 2 is newer than supported version 1", e.message)
        }
    }

    @Test
    fun invalidPlanIsRejected() {
        try {
            PlanJson.decode("""{"meals": 0}""")
            fail("expected PlanFormatException")
        } catch (e: PlanFormatException) {
            assertEquals("invalid plan: meal count must be between 1 and 8", e.message)
        }
    }

    @Test
    fun malformedJsonIsRejected() {
        try {
            PlanJson.decode("not json")
            fail("expected PlanFormatException")
        } catch (e: PlanFormatException) {
            assertEquals(true, e.message?.startsWith("parse plan:"))
        }
    }
}

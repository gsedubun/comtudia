package com.comtudia.app.core

import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Compares the Kotlin schedule against deterministic vectors produced by the
 * desktop Go implementation (`internal/plan/golden_test.go`).
 *
 * Regenerate the vectors with:
 * `go test ./internal/plan/ -run TestGoldenVectors -update`
 */
class ScheduleGoldenTest {
    @Test
    fun matchesDesktopGoldenVectors() {
        val cases = loadGoldenFile().cases
        assertTrue("golden file should contain cases", cases.isNotEmpty())

        for (case in cases) {
            val zone = ZoneId.of(case.zone)
            val now = OffsetDateTime.parse(case.now).atZoneSameInstant(zone)
            val plan = case.plan.toPlan()

            assertNull("${case.name}: plan should be valid", plan.validate())

            val expected = case.slots
            val collected = ArrayList<Slot>(plan.meals * 3)
            val day = now.toLocalDate()
            for (offset in -1..1) {
                collected.addAll(Schedule.slotsForDate(plan, day.plusDays(offset.toLong()), zone))
            }
            val actual = collected.sortedBy { it.start.toInstant() }

            assertEquals("${case.name}: slot count", expected.size, actual.size)
            for (i in expected.indices) {
                assertEquals("${case.name}: slot $i index", expected[i].index, actual[i].index)
                assertEquals("${case.name}: slot $i start", instant(expected[i].start), actual[i].start.toInstant())
                assertEquals("${case.name}: slot $i end", instant(expected[i].end), actual[i].end.toInstant())
            }

            val state = Schedule.compute(plan, now)

            assertEquals("${case.name}: phase", case.state.phase, phaseName(state.phase))
            assertEquals("${case.name}: phaseStart", instant(case.state.phaseStart), state.phaseStart.toInstant())
            assertEquals("${case.name}: phaseEnd", instant(case.state.phaseEnd), state.phaseEnd.toInstant())
            assertEquals("${case.name}: currentMeal", case.state.currentMealIndex, state.currentMeal?.index)
            assertEquals("${case.name}: nextMeal", case.state.nextMealIndex, state.nextMeal?.index)
            assertEquals("${case.name}: firstMealIndex", case.state.firstMealIndex, state.firstMealIndex)
            assertEquals("${case.name}: lastMealIndex", case.state.lastMealIndex, state.lastMealIndex)
            assertEquals(
                "${case.name}: nextEatingPhaseStart",
                case.state.nextEatingPhaseStart?.let(::instant),
                state.nextEatingPhaseStart?.toInstant(),
            )
            assertEquals(
                "${case.name}: nextWindowStart",
                case.state.nextWindowStart?.let(::instant),
                state.nextWindowStart?.toInstant(),
            )
            assertEquals("${case.name}: remaining", case.state.remainingSeconds, state.remaining.seconds)
            assertEquals("${case.name}: progress", case.state.progress, state.progress.toDouble(), 1e-6)

            assertEquals(
                "${case.name}: countdown",
                case.formatted.countdown,
                Format.countdown(state.remaining),
            )
            assertEquals(
                "${case.name}: duration",
                case.formatted.duration,
                Format.duration(state.remaining),
            )
            assertEquals(
                "${case.name}: clockPhaseEnd",
                case.formatted.clockPhaseEnd,
                Format.clock(state.phaseEnd),
            )
        }
    }

    private fun loadGoldenFile(): GoldenFile {
        val stream = javaClass.classLoader.getResourceAsStream("schedule-golden.json")
        assertNotNull("schedule-golden.json must be on the test classpath", stream)
        val text = stream!!.bufferedReader().use { it.readText() }
        return json.decodeFromString(GoldenFile.serializer(), text)
    }

    private fun instant(text: String): Instant = OffsetDateTime.parse(text).toInstant()

    private fun phaseName(phase: Phase): String = when (phase) {
        Phase.FASTING -> "fasting"
        Phase.EATING -> "eating"
    }

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
    }

    @Serializable
    private data class GoldenFile(val cases: List<GoldenCase>)

    @Serializable
    private data class GoldenCase(
        val name: String,
        val zone: String,
        val plan: GoldenPlan,
        val now: String,
        val slots: List<GoldenSlot>,
        val state: GoldenState,
        val formatted: GoldenFormatted,
    )

    @Serializable
    private data class GoldenPlan(
        val version: Int = Plan.VERSION,
        @SerialName("eating_minutes") val eatingMinutes: Int,
        @SerialName("meals") val meals: Int,
        @SerialName("gap_minutes") val gapMinutes: Int,
        @SerialName("window_start_minutes") val windowStartMinutes: Int,
        @SerialName("notifications") val notifications: Boolean,
        @SerialName("run_in_tray") val runInTray: Boolean,
    ) {
        fun toPlan(): Plan = Plan(
            version = version,
            eatingMinutes = eatingMinutes,
            meals = meals,
            gapMinutes = gapMinutes,
            windowStartMinutes = windowStartMinutes,
            notifications = notifications,
            runInTray = runInTray,
        )
    }

    @Serializable
    private data class GoldenSlot(
        val index: Int,
        val start: String,
        val end: String,
    )

    @Serializable
    private data class GoldenState(
        val phase: String,
        val phaseStart: String,
        val phaseEnd: String,
        val currentMealIndex: Int? = null,
        val nextMealIndex: Int? = null,
        val firstMealIndex: Int,
        val lastMealIndex: Int,
        val nextEatingPhaseStart: String? = null,
        val nextWindowStart: String? = null,
        val remainingSeconds: Long,
        val progress: Double,
    )

    @Serializable
    private data class GoldenFormatted(
        val countdown: String,
        val duration: String,
        val clockPhaseEnd: String,
    )
}

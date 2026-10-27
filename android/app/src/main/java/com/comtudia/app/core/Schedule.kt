package com.comtudia.app.core

import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/** One meal on a particular cycle day. */
data class Slot(
    val index: Int,
    val day: LocalDate,
    val start: ZonedDateTime,
    val end: ZonedDateTime,
) {
    /** Length of the meal. Durations are preserved across DST transitions. */
    fun duration(): Duration = Duration.between(start, end)
}

/** The computed schedule at a single instant. */
data class ScheduleState(
    val now: ZonedDateTime,
    val phase: Phase,
    val phaseStart: ZonedDateTime,
    val phaseEnd: ZonedDateTime,
    val currentMeal: Slot?,
    val nextMeal: Slot?,
    val nextEatingPhaseStart: ZonedDateTime?,
    val nextEatingPhaseMeal: Int,
    val nextWindowStart: ZonedDateTime?,
    val firstMealIndex: Int,
    val lastMealIndex: Int,
    val mealCount: Int,
    val remaining: Duration,
    val progress: Float,
    val fastingTotal: Duration,
    val longFast: Duration,
) {
    /**
     * Identifies a contiguous phase. It changes exactly when notifications
     * should fire.
     */
    fun phaseKey(): String = "${phase.ordinal}/${phaseStart.toInstant().toEpochMilli()}"
}

object Schedule {
    /**
     * Meals belonging to the cycle that starts on [day].
     *
     * The window start is local wall-clock time while meal offsets are absolute
     * durations from that start, so DST transitions preserve meal durations and
     * later meals can shift relative to the wall clock.
     */
    fun slotsForDate(plan: Plan, day: LocalDate, zone: ZoneId): List<Slot> {
        val ranges = plan.mealRanges()
        if (ranges.isEmpty()) return emptyList()

        val dayStart = day.atStartOfDay(zone)
        val windowStart = wallTime(dayStart, plan.windowStartMinutes)
        val slots = ArrayList<Slot>(ranges.size)
        for (range in ranges) {
            slots.add(
                Slot(
                    index = range.index,
                    day = dayStart.toLocalDate(),
                    start = windowStart.plusMinutes(range.startMinutes.toLong()),
                    end = windowStart.plusMinutes(range.endMinutes.toLong()),
                ),
            )
        }
        return slots
    }

    /**
     * Calculates the current phase and the next events around [now].
     *
     * @throws ValidationException when the plan is invalid.
     */
    fun compute(plan: Plan, now: ZonedDateTime): ScheduleState {
        plan.requireValid()

        val zone = now.zone
        val day = now.toLocalDate()
        val collected = ArrayList<Slot>(plan.meals * 3)
        for (offset in -1..1) {
            collected.addAll(slotsForDate(plan, day.plusDays(offset.toLong()), zone))
        }
        // Stable, like the desktop implementation: slot order is preserved when
        // two slots start at the same instant.
        val slots = collected.sortedBy { it.start.toInstant() }

        val runStart = IntArray(slots.size)
        for (i in slots.indices) {
            runStart[i] = if (i > 0 && sameInstant(slots[i].start, slots[i - 1].end)) {
                runStart[i - 1]
            } else {
                i
            }
        }
        val runEnd = IntArray(slots.size)
        for (i in slots.indices.reversed()) {
            runEnd[i] = if (i < slots.size - 1 && sameInstant(slots[i].end, slots[i + 1].start)) {
                runEnd[i + 1]
            } else {
                i
            }
        }

        var current = -1
        for (i in slots.indices) {
            if (!now.isBefore(slots[i].start) && now.isBefore(slots[i].end)) {
                current = i
                break
            }
        }

        val phase: Phase
        val phaseStart: ZonedDateTime
        val phaseEnd: ZonedDateTime
        var currentMeal: Slot? = null
        var nextMeal: Slot? = null
        var nextEatingPhaseStart: ZonedDateTime? = null
        var nextEatingPhaseMeal = 0
        var firstMealIndex: Int
        var lastMealIndex: Int

        if (current >= 0) {
            val first = runStart[current]
            val last = runEnd[current]
            phase = Phase.EATING
            currentMeal = slots[current]
            phaseStart = slots[first].start
            phaseEnd = slots[last].end
            firstMealIndex = slots[first].index
            lastMealIndex = slots[last].index
            if (current + 1 < slots.size) {
                nextMeal = slots[current + 1]
            }
            if (last + 1 < slots.size) {
                nextEatingPhaseStart = slots[last + 1].start
                nextEatingPhaseMeal = slots[last + 1].index
            }
        } else {
            var next = -1
            for (i in slots.indices) {
                if (slots[i].start.isAfter(now)) {
                    next = i
                    break
                }
            }
            if (next < 0) {
                // Unreachable with a valid plan (three days of slots are always
                // generated), but mirror the desktop error rather than guess.
                throw IllegalStateException("no upcoming meal found")
            }

            phase = Phase.FASTING
            nextMeal = slots[next]
            phaseEnd = slots[next].start
            firstMealIndex = slots[next].index
            lastMealIndex = slots[runEnd[next]].index
            nextEatingPhaseStart = slots[next].start
            nextEatingPhaseMeal = slots[next].index

            var previousEnd: ZonedDateTime? = null
            for (slot in slots) {
                if (!slot.end.isAfter(now)) {
                    previousEnd = slot.end
                }
            }
            phaseStart = previousEnd ?: phaseEnd
        }

        var nextWindowStart: ZonedDateTime? = null
        for (slot in slots) {
            if (slot.index != 1) continue
            if (slot.start.isAfter(now)) {
                nextWindowStart = slot.start
                break
            }
        }

        var remaining = Duration.between(now, phaseEnd)
        if (remaining < Duration.ZERO) {
            remaining = Duration.ZERO
        }

        var progress = 0.0
        val total = Duration.between(phaseStart, phaseEnd)
        if (total > Duration.ZERO) {
            progress = Duration.between(phaseStart, now).toNanos().toDouble() / total.toNanos().toDouble()
            progress = progress.coerceIn(0.0, 1.0)
        }

        return ScheduleState(
            now = now,
            phase = phase,
            phaseStart = phaseStart,
            phaseEnd = phaseEnd,
            currentMeal = currentMeal,
            nextMeal = nextMeal,
            nextEatingPhaseStart = nextEatingPhaseStart,
            nextEatingPhaseMeal = nextEatingPhaseMeal,
            nextWindowStart = nextWindowStart,
            firstMealIndex = firstMealIndex,
            lastMealIndex = lastMealIndex,
            mealCount = plan.meals,
            remaining = remaining,
            progress = progress.toFloat(),
            fastingTotal = Duration.ofMinutes(plan.fastingMinutes().toLong()),
            longFast = Duration.ofMinutes(plan.longFastMinutes().toLong()),
        )
    }

    /**
     * Converts a wall-clock minute offset from local midnight into a time,
     * normalizing into following days when necessary.
     */
    private fun wallTime(dayStart: ZonedDateTime, minutes: Int): ZonedDateTime {
        val days = Math.floorDiv(minutes, Plan.MINUTES_PER_DAY)
        val rem = Math.floorMod(minutes, Plan.MINUTES_PER_DAY)
        val date = dayStart.toLocalDate().plusDays(days.toLong())
        return ZonedDateTime.of(date, LocalTime.of(rem / 60, rem % 60), dayStart.zone)
    }

    private fun sameInstant(a: ZonedDateTime, b: ZonedDateTime): Boolean =
        a.toInstant() == b.toInstant()
}

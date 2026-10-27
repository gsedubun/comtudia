package com.comtudia.app.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** The two parts of the daily eating cycle. */
enum class Phase { FASTING, EATING }

/**
 * A recurring daily eating schedule.
 *
 * Field names match the desktop config file (`plan.json`) so plans can be moved
 * between the desktop app and Android unchanged.
 */
@Serializable
data class Plan(
    @SerialName("version") val version: Int = Plan.VERSION,
    @SerialName("eating_minutes") val eatingMinutes: Int = Plan.DEFAULT_EATING_MINUTES,
    @SerialName("meals") val meals: Int = Plan.DEFAULT_MEALS,
    @SerialName("gap_minutes") val gapMinutes: Int = Plan.DEFAULT_GAP_MINUTES,
    @SerialName("window_start_minutes") val windowStartMinutes: Int = Plan.DEFAULT_WINDOW_START,
    @SerialName("notifications") val notifications: Boolean = Plan.DEFAULT_NOTIFICATIONS,
    @SerialName("run_in_tray") val runInTray: Boolean = Plan.DEFAULT_RUN_IN_TRAY,
) {
    companion object {
        /** Current persisted plan schema version. */
        const val VERSION = 1

        const val MINUTES_PER_DAY = 24 * 60
        const val MAX_MEALS = 8
        const val MIN_MEAL_MINUTE = 15
        const val MIN_EATING = 60
        const val MIN_FASTING = 60

        const val DEFAULT_EATING_MINUTES = 6 * 60
        const val DEFAULT_MEALS = 3
        const val DEFAULT_GAP_MINUTES = 0
        const val DEFAULT_WINDOW_START = 12 * 60
        const val DEFAULT_NOTIFICATIONS = true
        const val DEFAULT_RUN_IN_TRAY = false
        const val DEFAULT_WINDOW_START_LABEL = "12:00"

        /** The plan used on first launch and by reset. */
        fun default(): Plan = Plan()

        /** Named starting points for the settings screen. */
        val PRESETS: List<Preset> = listOf(
            Preset("16:8", 8 * 60, 2, 0),
            Preset("18:6", 6 * 60, 3, 0),
            Preset("20:4", 4 * 60, 2, 0),
        )
    }
}

/** A named starting point for the settings screen. */
data class Preset(
    val name: String,
    val eatingMinutes: Int,
    val meals: Int,
    val gapMinutes: Int,
)

/** One eating slot relative to the start of the eating window. */
data class MealRange(
    val index: Int,
    val startMinutes: Int,
    val endMinutes: Int,
    val durationMinutes: Int,
)

/** Describes an invalid plan field. */
class ValidationException(val field: String, message: String) : IllegalArgumentException(message)

/**
 * Checks that the plan can be laid out inside a 24-hour day.
 *
 * Returns the first problem found, or `null` when the plan is valid.
 */
fun Plan.validate(): ValidationException? {
    if (meals < 1 || meals > Plan.MAX_MEALS) {
        return ValidationException(
            "meals",
            "meal count must be between 1 and ${Plan.MAX_MEALS}",
        )
    }
    if (eatingMinutes < Plan.MIN_EATING || eatingMinutes > Plan.MINUTES_PER_DAY - Plan.MIN_FASTING) {
        return ValidationException("eating", "eating time must be between 1h and 23h")
    }
    if (windowStartMinutes < 0 || windowStartMinutes >= Plan.MINUTES_PER_DAY) {
        return ValidationException("window_start", "start time must be within the day")
    }
    if (gapMinutes < 0) {
        return ValidationException("gap", "gap cannot be negative")
    }
    if (meals == 1 && gapMinutes != 0) {
        return ValidationException("gap", "a single meal cannot have a gap")
    }
    if (eatingMinutes / meals < Plan.MIN_MEAL_MINUTE) {
        return ValidationException(
            "meals",
            "each meal must be at least ${Plan.MIN_MEAL_MINUTE} minutes",
        )
    }
    if (spanMinutes() > Plan.MINUTES_PER_DAY - Plan.MIN_FASTING) {
        return ValidationException("gap", "the meal spread must be 23 hours or less")
    }
    return null
}

/** Throws [ValidationException] when the plan is invalid. */
fun Plan.requireValid() {
    validate()?.let { throw it }
}

/** Total non-eating time in a day. */
fun Plan.fastingMinutes(): Int = Plan.MINUTES_PER_DAY - eatingMinutes

/** Time from the first meal start to the last meal end. */
fun Plan.spanMinutes(): Int {
    if (meals <= 0) return 0
    return eatingMinutes + (meals - 1) * gapMinutes
}

/** Time outside the whole meal spread. */
fun Plan.longFastMinutes(): Int {
    val n = Plan.MINUTES_PER_DAY - spanMinutes()
    return if (n < 0) 0 else n
}

/**
 * Splits the eating time into equal slots, giving any leftover minute to the
 * earliest slots. Gaps between meals count as fasting.
 */
fun Plan.mealRanges(): List<MealRange> {
    if (meals <= 0 || eatingMinutes < meals) return emptyList()

    val base = eatingMinutes / meals
    val remainder = eatingMinutes % meals
    val out = ArrayList<MealRange>(meals)
    var offset = 0
    for (i in 0 until meals) {
        val duration = if (i < remainder) base + 1 else base
        out.add(
            MealRange(
                index = i + 1,
                startMinutes = offset,
                endMinutes = offset + duration,
                durationMinutes = duration,
            ),
        )
        offset += duration + gapMinutes
    }
    return out
}

/** A short human-readable plan description, e.g. `18h fast · 6h eat`. */
fun Plan.summary(): String =
    "${minutesLabel(fastingMinutes())} fast \u00b7 ${minutesLabel(eatingMinutes)} eat"

private fun minutesLabel(minutes: Int): String {
    if (minutes <= 0) return "0h"
    val h = minutes / 60
    val m = minutes % 60
    return when {
        m == 0 -> "${h}h"
        h == 0 -> "${m}m"
        else -> "${h}h ${m.toString().padStart(2, '0')}m"
    }
}

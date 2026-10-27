package com.comtudia.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import com.comtudia.app.core.Format
import com.comtudia.app.core.Phase
import com.comtudia.app.core.Plan
import com.comtudia.app.core.Schedule
import com.comtudia.app.core.ScheduleState
import com.comtudia.app.core.Slot
import com.comtudia.app.ui.theme.onPhaseColor
import com.comtudia.app.ui.theme.phaseColor

private enum class MealStatus { NONE, CURRENT, NEXT }

/**
 * Today's meals (the cycle day that owns the current or next meal) with the
 * current meal highlighted as "NOW" and the upcoming one as "NEXT".
 */
@Composable
fun MealList(
    plan: Plan,
    state: ScheduleState,
    modifier: Modifier = Modifier,
) {
    val zone = state.now.zone
    val day = state.currentMeal?.day ?: state.nextMeal?.day ?: state.now.toLocalDate()
    val slots = Schedule.slotsForDate(plan, day, zone)
    val accent = phaseColor(Phase.EATING)
    val onAccent = onPhaseColor(Phase.EATING)

    val currentIndex = if (state.phase == Phase.EATING) state.currentMeal?.index else null
    val nextStart = state.nextEatingPhaseStart
    val nextIndex = if (
        nextStart != null &&
        state.nextEatingPhaseMeal > 0 &&
        Format.sameDate(nextStart, day.atStartOfDay(zone)) &&
        !(state.phase == Phase.EATING && state.nextEatingPhaseMeal == currentIndex)
    ) {
        state.nextEatingPhaseMeal
    } else {
        null
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = "Schedule · ${Format.dayName(day.atStartOfDay(zone), state.now)}",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        if (slots.isEmpty()) {
            Text(
                text = "No meals configured.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            slots.forEach { slot ->
                val status = when {
                    slot.index == currentIndex -> MealStatus.CURRENT
                    slot.index == nextIndex -> MealStatus.NEXT
                    else -> MealStatus.NONE
                }
                MealRow(slot = slot, status = status, accent = accent, onAccent = onAccent)
            }
        }
    }
}

@Composable
private fun MealRow(
    slot: Slot,
    status: MealStatus,
    accent: Color,
    onAccent: Color,
) {
    val base = MaterialTheme.colorScheme.surface
    val container = when (status) {
        MealStatus.CURRENT -> lerp(base, accent, 0.24f)
        MealStatus.NEXT -> lerp(base, accent, 0.10f)
        MealStatus.NONE -> base
    }

    Surface(
        color = container,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = slot.index.toString(),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.width(22.dp),
            )
            Text(
                text = Format.range(slot.start, slot.end),
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = Format.durationCompact(slot.duration()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (status != MealStatus.NONE) {
                Spacer(Modifier.width(10.dp))
                StatusPill(
                    text = if (status == MealStatus.CURRENT) "NOW" else "NEXT",
                    accent = accent,
                    onAccent = onAccent,
                    filled = status == MealStatus.CURRENT,
                )
            }
        }
    }
}

@Composable
private fun StatusPill(
    text: String,
    accent: Color,
    onAccent: Color,
    filled: Boolean,
) {
    Surface(
        color = if (filled) accent else accent.copy(alpha = 0.18f),
        contentColor = if (filled) onAccent else accent,
        shape = RoundedCornerShape(percent = 50),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

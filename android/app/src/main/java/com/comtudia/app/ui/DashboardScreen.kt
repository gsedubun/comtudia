package com.comtudia.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.comtudia.app.core.Format
import com.comtudia.app.core.Phase
import com.comtudia.app.core.Plan
import com.comtudia.app.core.ScheduleState
import com.comtudia.app.core.mealRanges
import com.comtudia.app.core.summary
import com.comtudia.app.ui.components.MealList
import com.comtudia.app.ui.components.ProgressRing
import com.comtudia.app.ui.components.StatCard
import com.comtudia.app.ui.components.Timeline
import com.comtudia.app.ui.theme.ContentGutter
import com.comtudia.app.ui.theme.ContentMaxWidth
import com.comtudia.app.ui.theme.onPhaseColor
import com.comtudia.app.ui.theme.phaseColor
import java.time.Duration

/**
 * Main dashboard: phase, countdown, progress ring, next-meal line, 24-hour
 * timeline, today's meals, and the daily totals. Pure composable — callers own
 * the plan, the ticked schedule state, and navigation.
 */
@Composable
fun DashboardScreen(
    plan: Plan,
    state: ScheduleState,
    onOpenSettings: () -> Unit,
    onToggleNotifications: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = phaseColor(state.phase)
    val onAccent = onPhaseColor(state.phase)
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val mealDuration = plan.mealRanges().firstOrNull()
        ?.let { Format.durationCompact(Duration.ofMinutes(it.durationMinutes.toLong())) }
        ?: "—"

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = ContentMaxWidth)
                .padding(horizontal = ContentGutter, vertical = 20.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "comtudia",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Text(
                        text = plan.summary(),
                        style = MaterialTheme.typography.bodySmall,
                        color = muted,
                    )
                }
                TextButton(onClick = onOpenSettings) {
                    Text(text = "⚙", fontSize = 24.sp)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Notifications",
                    style = MaterialTheme.typography.bodySmall,
                    color = muted,
                )
                Spacer(Modifier.weight(1f))
                Switch(
                    checked = plan.notifications,
                    onCheckedChange = onToggleNotifications,
                )
            }

            Spacer(Modifier.height(12.dp))
            PhasePill(
                phase = state.phase,
                accent = accent,
                onAccent = onAccent,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )

            Spacer(Modifier.height(18.dp))
            ProgressRing(
                progress = state.progress,
                color = accent,
                modifier = Modifier
                    .size(260.dp)
                    .align(Alignment.CenterHorizontally),
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(horizontal = 32.dp),
                ) {
                    Text(
                        text = Format.countdown(state.remaining),
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = ringSubtitle(state),
                        style = MaterialTheme.typography.bodySmall,
                        color = muted,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
            StatCard(
                title = nextMealTitle(state),
                value = Format.duration(state.remaining),
                subtitle = nextMealSubtitle(state),
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(22.dp))
            Timeline(plan = plan, now = state.now)

            Spacer(Modifier.height(24.dp))
            MealList(plan = plan, state = state)

            Spacer(Modifier.height(20.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                StatCard(
                    title = "Fasting total",
                    value = Format.duration(state.fastingTotal),
                    modifier = Modifier.weight(1f),
                )
                StatCard(
                    title = "Long fast",
                    value = Format.duration(state.longFast),
                    modifier = Modifier.weight(1f),
                )
                StatCard(
                    title = "Meals",
                    value = "${state.mealCount} × $mealDuration",
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun PhasePill(
    phase: Phase,
    accent: Color,
    onAccent: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = accent,
        contentColor = onAccent,
        shape = RoundedCornerShape(percent = 50),
        modifier = modifier,
    ) {
        Text(
            text = if (phase == Phase.EATING) "EATING NOW" else "FASTING",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(horizontal = 22.dp, vertical = 9.dp),
        )
    }
}

private fun ringSubtitle(state: ScheduleState): String {
    val next = state.nextEatingPhaseStart
    return if (state.phase == Phase.EATING) {
        if (state.firstMealIndex == state.lastMealIndex) {
            "meal ${state.lastMealIndex} of ${state.mealCount} · ends ${Format.clock(state.phaseEnd)}"
        } else {
            "meals ${state.firstMealIndex}–${state.lastMealIndex} · window ends ${Format.clock(state.phaseEnd)}"
        }
    } else if (next != null) {
        "next eating ${Format.whenPhrase(next, state.now)}"
    } else {
        "fasting"
    }
}

private fun nextMealTitle(state: ScheduleState): String {
    if (state.phase != Phase.EATING) {
        return "Next eating"
    }
    return if (state.firstMealIndex == state.lastMealIndex) "Meal ends" else "Window ends"
}

private fun nextMealSubtitle(state: ScheduleState): String {
    val next = state.nextEatingPhaseStart ?: return "No upcoming meal"
    return if (state.phase == Phase.EATING) {
        "next eating ${Format.dayClock(next, state.now)}"
    } else {
        "opens ${Format.dayClock(next, state.now)}"
    }
}

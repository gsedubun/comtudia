package com.comtudia.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.comtudia.app.core.Format
import com.comtudia.app.core.Plan
import com.comtudia.app.core.fastingMinutes
import com.comtudia.app.core.longFastMinutes
import com.comtudia.app.core.mealRanges
import com.comtudia.app.core.validate
import com.comtudia.app.ui.theme.ContentGutter
import com.comtudia.app.ui.theme.ContentMaxWidth
import java.time.Duration

/**
 * Plan editor. Owns only local draft state seeded from [initial]; the caller
 * persists the plan and reports success or failure back through [message].
 */
@Composable
fun SettingsScreen(
    initial: Plan,
    onSave: (Plan) -> Unit,
    onCancel: () -> Unit,
    onImport: () -> Unit,
    onExport: () -> Unit,
    message: String? = null,
    modifier: Modifier = Modifier,
) {
    val minEating = Plan.MIN_EATING
    val maxEating = Plan.MINUTES_PER_DAY - Plan.MIN_FASTING

    var eatingMinutes by remember(initial) {
        mutableIntStateOf(initial.eatingMinutes.coerceIn(minEating, maxEating))
    }
    var meals by remember(initial) {
        mutableIntStateOf(initial.meals.coerceIn(1, Plan.MAX_MEALS))
    }
    var gapMinutes by remember(initial) {
        mutableIntStateOf(initial.gapMinutes.coerceIn(0, Plan.MINUTES_PER_DAY))
    }
    var windowStartMinutes by remember(initial) {
        mutableIntStateOf(initial.windowStartMinutes.coerceIn(0, Plan.MINUTES_PER_DAY - 1))
    }
    var notifications by remember(initial) {
        mutableStateOf(initial.notifications)
    }

    val draft = initial.copy(
        eatingMinutes = eatingMinutes,
        meals = meals,
        gapMinutes = gapMinutes,
        windowStartMinutes = windowStartMinutes,
        notifications = notifications,
    )
    val validation = draft.validate()

    fun fieldError(field: String): String? =
        if (validation?.field == field) validation.message else null

    val resetToDefault: () -> Unit = {
        val defaults = Plan.default()
        eatingMinutes = defaults.eatingMinutes
        meals = defaults.meals
        gapMinutes = defaults.gapMinutes
        windowStartMinutes = defaults.windowStartMinutes
        notifications = defaults.notifications
    }

    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val previewStyle: TextStyle = MaterialTheme.typography.bodySmall.copy(
        fontFamily = FontFamily.Monospace,
    )

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
                .padding(horizontal = ContentGutter, vertical = 16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onCancel) {
                    Text(text = "← Back", style = MaterialTheme.typography.labelLarge)
                }
                Spacer(Modifier.width(4.dp))
                Text(
                    text = "Plan",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }

            if (message != null) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                ) {
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }

            Text(
                text = "Your daily cycle is 24 hours. Eating time is the total across all " +
                    "meals; gaps count as fasting time.",
                style = MaterialTheme.typography.bodySmall,
                color = muted,
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
            )

            SectionLabel("Preset")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Plan.PRESETS.forEach { preset ->
                    FilterChip(
                        selected = eatingMinutes == preset.eatingMinutes &&
                            meals == preset.meals &&
                            gapMinutes == preset.gapMinutes,
                        onClick = {
                            eatingMinutes = preset.eatingMinutes
                            meals = preset.meals
                            gapMinutes = preset.gapMinutes
                        },
                        label = { Text(preset.name) },
                    )
                }
            }

            SectionLabel("Eating time")
            StepperRow(
                label = "Hours",
                value = twoDigits(eatingMinutes / 60),
                onDecrease = { eatingMinutes = (eatingMinutes - 60).coerceAtLeast(minEating) },
                onIncrease = { eatingMinutes = (eatingMinutes + 60).coerceAtMost(maxEating) },
                canDecrease = eatingMinutes > minEating,
                canIncrease = eatingMinutes < maxEating,
            )
            StepperRow(
                label = "Minutes",
                value = twoDigits(eatingMinutes % 60),
                onDecrease = { eatingMinutes = (eatingMinutes - 5).coerceAtLeast(minEating) },
                onIncrease = { eatingMinutes = (eatingMinutes + 5).coerceAtMost(maxEating) },
                canDecrease = eatingMinutes > minEating,
                canIncrease = eatingMinutes < maxEating,
            )
            Text(
                text = "= ${Format.minutes(eatingMinutes)} total",
                style = MaterialTheme.typography.bodySmall,
                color = muted,
                modifier = Modifier.padding(top = 2.dp),
            )
            FieldError(fieldError("eating"))

            SectionLabel("Meals")
            StepperRow(
                label = "Meals per day",
                value = meals.toString(),
                onDecrease = { meals = (meals - 1).coerceAtLeast(1) },
                onIncrease = { meals = (meals + 1).coerceAtMost(Plan.MAX_MEALS) },
                supporting = "Split evenly across the eating window",
                canDecrease = meals > 1,
                canIncrease = meals < Plan.MAX_MEALS,
            )
            FieldError(fieldError("meals"))

            SectionLabel("Gap between meals")
            StepperRow(
                label = "Gap",
                value = Format.minutes(gapMinutes),
                onDecrease = { gapMinutes = (gapMinutes - 15).coerceAtLeast(0) },
                onIncrease = { gapMinutes = (gapMinutes + 15).coerceAtMost(Plan.MINUTES_PER_DAY) },
                supporting = "0 = meals back-to-back",
                canDecrease = gapMinutes > 0,
                canIncrease = gapMinutes < Plan.MINUTES_PER_DAY,
            )
            FieldError(fieldError("gap"))

            SectionLabel("First meal")
            StepperRow(
                label = "Hour",
                value = twoDigits(windowStartMinutes / 60),
                onDecrease = { windowStartMinutes = shiftStartHour(windowStartMinutes, -1) },
                onIncrease = { windowStartMinutes = shiftStartHour(windowStartMinutes, 1) },
            )
            StepperRow(
                label = "Minute",
                value = twoDigits(windowStartMinutes % 60),
                onDecrease = { windowStartMinutes = shiftStartMinute(windowStartMinutes, -5) },
                onIncrease = { windowStartMinutes = shiftStartMinute(windowStartMinutes, 5) },
            )
            Text(
                text = "Starts ${Format.clockMinutes(windowStartMinutes)}",
                style = MaterialTheme.typography.bodySmall,
                color = muted,
                modifier = Modifier.padding(top = 2.dp),
            )
            FieldError(fieldError("window_start"))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Notifications",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "Remind me when fasting and eating phases change.",
                        style = MaterialTheme.typography.bodySmall,
                        color = muted,
                    )
                }
                Switch(
                    checked = notifications,
                    onCheckedChange = { notifications = it },
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = { onSave(draft) },
                    enabled = validation == null,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Save plan")
                }
                OutlinedButton(onClick = resetToDefault) {
                    Text("Reset")
                }
                TextButton(onClick = onCancel) {
                    Text("Cancel")
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = onImport,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Import plan")
                }
                OutlinedButton(
                    onClick = onExport,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Export plan")
                }
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 20.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Preview",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(10.dp))

                    if (validation != null) {
                        Text(
                            text = validation.message ?: "This plan is not valid yet.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    } else {
                        draft.mealRanges().forEach { range ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "${range.index}.",
                                    style = previewStyle,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.width(24.dp),
                                )
                                Text(
                                    text = Format.rangeMinutes(
                                        draft.windowStartMinutes + range.startMinutes,
                                        draft.windowStartMinutes + range.endMinutes,
                                    ),
                                    style = previewStyle,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    text = Format.durationCompact(
                                        Duration.ofMinutes(range.durationMinutes.toLong()),
                                    ),
                                    style = previewStyle,
                                    color = muted,
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Eating ${Format.minutes(draft.eatingMinutes)} · " +
                                "Fasting ${Format.minutes(draft.fastingMinutes())}",
                            style = previewStyle,
                            color = muted,
                        )
                        Text(
                            text = longFastLine(draft),
                            style = previewStyle,
                            color = muted,
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 18.dp, bottom = 2.dp),
    )
}

@Composable
private fun StepperRow(
    label: String,
    value: String,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    canDecrease: Boolean = true,
    canIncrease: Boolean = true,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (supporting != null) {
                Text(
                    text = supporting,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        StepperButton(symbol = "−", enabled = canDecrease, onClick = onDecrease)
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace),
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(min = 54.dp),
            maxLines = 1,
        )
        StepperButton(symbol = "+", enabled = canIncrease, onClick = onIncrease)
    }
}

@Composable
private fun StepperButton(
    symbol: String,
    enabled: Boolean,
    onClick: () -> Unit,
    tint: Color? = null,
) {
    FilledTonalIconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(38.dp),
    ) {
        Text(
            text = symbol,
            fontSize = 18.sp,
            color = tint ?: MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun FieldError(message: String?) {
    if (message != null) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

private fun twoDigits(value: Int): String = value.toString().padStart(2, '0')

private fun shiftStartHour(minutes: Int, delta: Int): Int {
    val hour = ((minutes / 60) + delta + 24) % 24
    return hour * 60 + minutes % 60
}

private fun shiftStartMinute(minutes: Int, delta: Int): Int {
    return (minutes + delta + Plan.MINUTES_PER_DAY) % Plan.MINUTES_PER_DAY
}

private fun longFastLine(plan: Plan): String {
    val base = "Long fast ${Format.minutes(plan.longFastMinutes())}"
    return if (plan.gapMinutes > 0) {
        base + " · meal gaps ${Format.minutes((plan.meals - 1) * plan.gapMinutes)}"
    } else {
        base
    }
}

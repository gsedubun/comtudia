package com.comtudia.app.wear

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.ExperimentalWearMaterialApi
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import com.comtudia.app.core.Format
import com.comtudia.app.core.Phase
import com.comtudia.app.core.Schedule
import kotlinx.coroutines.delay
import java.time.ZonedDateTime

private val EatingAccent = Color(0xFF3DD597)
private val FastingAccent = Color(0xFF8AA2FF)
private val Muted = Color(0xFF8FA39D)

/**
 * Watch dashboard: phase, drift-free countdown, the phase boundary and the next
 * meal. The plan is whatever the phone last pushed; "Sync phone" asks again.
 */
@OptIn(ExperimentalWearMaterialApi::class)
@Composable
fun WearApp() {
    val context = LocalContext.current
    val store = remember { WearPlanStore.get(context) }
    val plan by store.plan.collectAsStateWithLifecycle()
    var now by remember { mutableStateOf(ZonedDateTime.now()) }

    LaunchedEffect(Unit) {
        WearSync.requestPlan(context)
        while (true) {
            now = ZonedDateTime.now()
            delay(1_000)
        }
    }

    val state = remember(plan, now) {
        runCatching { Schedule.compute(plan, now) }.getOrNull()
    }
    val accent = if (state?.phase == Phase.EATING) EatingAccent else FastingAccent

    Scaffold(timeText = { TimeText() }) {
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            item {
                Text(
                    text = when (state?.phase) {
                        Phase.EATING -> "Eating"
                        Phase.FASTING -> "Fasting"
                        null -> "No plan"
                    },
                    color = accent,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
            item {
                Text(
                    text = state?.let { Format.countdown(it.remaining) } ?: "--:--:--",
                    fontSize = 28.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                )
            }
            if (state != null) {
                item {
                    Text(
                        text = "until ${Format.clock(state.phaseEnd)}",
                        color = Muted,
                        fontSize = 12.sp,
                    )
                }
                item {
                    Text(
                        text = state.nextMeal?.let { "Next meal ${Format.clock(it.start)}" }
                            ?: "Next ${Format.clock(state.nextEatingPhaseStart ?: state.phaseEnd)}",
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            item {
                Text(
                    text = "Sync phone",
                    color = Muted,
                    fontSize = 11.sp,
                    modifier = Modifier
                        .clickable { WearSync.requestPlan(context) }
                        .padding(8.dp),
                )
            }
        }
    }
}

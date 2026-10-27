package com.comtudia.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.comtudia.app.alarm.AlarmScheduler
import com.comtudia.app.core.Plan
import com.comtudia.app.core.Schedule
import com.comtudia.app.core.ScheduleState
import com.comtudia.app.core.summary
import com.comtudia.app.data.PhaseStateStore
import com.comtudia.app.data.PlanRepository
import com.comtudia.app.notify.Notifications
import com.comtudia.app.notify.PhaseNotifications
import com.comtudia.app.sync.WearSync
import com.comtudia.app.ui.DashboardScreen
import com.comtudia.app.ui.SettingsScreen
import com.comtudia.app.ui.theme.ComtudiaTheme
import kotlinx.coroutines.delay
import java.time.ZonedDateTime

/**
 * Single-activity shell. Owns the plan repository, the alarm scheduler and the
 * notification poster, and keeps the dashboard ticking while it is visible.
 *
 * The one-second ticker only drives the UI: the notification countdown is a
 * SystemUI chronometer and phase alerts come from exact alarms, so neither
 * depends on the activity staying alive.
 */
class MainActivity : ComponentActivity() {

    private lateinit var repository: PlanRepository
    private lateinit var scheduler: AlarmScheduler
    private lateinit var notifications: PhaseNotifications
    private lateinit var stateStore: PhaseStateStore

    private var message by mutableStateOf<String?>(null)
    private var exactAlarmBlocked by mutableStateOf(false)

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) refreshSchedule(prime = false)
        }

    private val importDocument =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) importPlan(uri)
        }

    private val exportDocument =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            if (uri != null) exportPlan(uri)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        repository = PlanRepository(this)
        scheduler = AlarmScheduler(this)
        notifications = PhaseNotifications(this)
        stateStore = PhaseStateStore(this)

        repository.loadError.value?.let { message = it }

        Notifications.ensureChannels(this)
        refreshSchedule(prime = false)
        requestNotificationPermission()

        setContent {
            ComtudiaTheme {
                ComtudiaApp()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val blocked = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !scheduler.canScheduleExact()
        if (exactAlarmBlocked && !blocked) {
            // Coming back from the system screen after granting exact alarms.
            refreshSchedule(prime = false)
        }
        exactAlarmBlocked = blocked
    }

    @Composable
    private fun ComtudiaApp() {
        val plan by repository.plan.collectAsStateWithLifecycle()
        var now by remember { mutableStateOf(ZonedDateTime.now()) }
        var showSettings by remember { mutableStateOf(false) }

        LaunchedEffect(Unit) {
            while (true) {
                now = ZonedDateTime.now()
                delay(1_000)
            }
        }

        val state = remember(plan, now) {
            runCatching { Schedule.compute(plan, now) }.getOrNull()
        }

        // Keep the silent card in step while the app is open. Alerts stay owned
        // by the alarm receiver so a phase change is never announced twice.
        LaunchedEffect(state?.phaseKey(), plan.notifications) {
            val current = state ?: return@LaunchedEffect
            if (plan.notifications) notifications.showOngoing(current) else notifications.cancelOngoing()
        }

        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            Column(Modifier.fillMaxSize().systemBarsPadding()) {
                if (exactAlarmBlocked) {
                    Banner(
                        text = "Exact alarms are off, so phase alerts may be late.",
                        actionLabel = "Fix",
                        onAction = ::openExactAlarmSettings,
                    )
                }
                message?.let { text ->
                    Banner(text = text, actionLabel = "Dismiss", onAction = { message = null })
                }

                if (showSettings) {
                    SettingsScreen(
                        initial = plan,
                        onSave = { edited -> savePlan(edited) { showSettings = false } },
                        onCancel = { showSettings = false; message = null },
                        onImport = { importDocument.launch(IMPORT_MIME_TYPES) },
                        onExport = { exportDocument.launch(EXPORT_FILE_NAME) },
                        message = message,
                    )
                } else if (state != null) {
                    DashboardScreen(
                        plan = plan,
                        state = state,
                        onOpenSettings = { message = null; showSettings = true },
                        onToggleNotifications = { enabled -> toggleNotifications(plan, enabled) },
                    )
                } else {
                    Banner(text = "The saved plan cannot be displayed. Open settings and reset it.")
                }
            }
        }
    }

    /** Validates, persists and re-arms everything after a plan edit. */
    private fun savePlan(plan: Plan, onSaved: () -> Unit) {
        repository.save(plan)
            .onSuccess {
                // Prime after a plan change so a shifted boundary does not fire
                // an alert the moment the user saves.
                refreshSchedule(prime = true)
                message = "Plan saved · ${plan.summary()}"
                onSaved()
            }
            .onFailure { error ->
                message = "Could not save the plan: ${error.message}"
            }
    }

    private fun toggleNotifications(plan: Plan, enabled: Boolean) {
        repository.save(plan.copy(notifications = enabled))
            .onSuccess {
                if (enabled) {
                    refreshSchedule(prime = false)
                } else {
                    notifications.cancelOngoing()
                }
            }
            .onFailure { message = "Could not update notifications: ${it.message}" }
    }

    private fun refreshSchedule(prime: Boolean) {
        val plan = repository.current()
        val state = runCatching { Schedule.compute(plan, ZonedDateTime.now()) }.getOrNull() ?: return

        if (prime || !stateStore.isPrimed()) stateStore.record(state)
        scheduler.schedule(state)
        WearSync.pushPlan(this, plan)
        if (plan.notifications) notifications.showOngoing(state) else notifications.cancelOngoing()
    }

    private fun importPlan(uri: Uri) {
        repository.importFrom(uri)
            .onSuccess { imported ->
                refreshSchedule(prime = true)
                message = "Imported ${imported.summary()}"
            }
            .onFailure { message = "Import failed: ${it.message}" }
    }

    private fun exportPlan(uri: Uri) {
        repository.exportTo(uri)
            .onSuccess { message = "Plan exported" }
            .onFailure { message = "Export failed: ${it.message}" }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun openExactAlarmSettings() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val request = Intent(
            Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
            Uri.parse("package:$packageName"),
        )
        runCatching { startActivity(request) }.onFailure {
            runCatching {
                startActivity(
                    Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:$packageName"),
                    ),
                )
            }
        }
    }

    @Composable
    private fun Banner(
        text: String,
        actionLabel: String? = null,
        onAction: (() -> Unit)? = null,
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = text,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (actionLabel != null && onAction != null) {
                    TextButton(onClick = onAction) { Text(actionLabel) }
                }
            }
        }
    }

    private companion object {
        val IMPORT_MIME_TYPES = arrayOf(
            "application/json",
            "text/plain",
            "application/octet-stream",
        )
        const val EXPORT_FILE_NAME = "plan.json"
    }
}

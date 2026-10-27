package com.comtudia.app.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.comtudia.app.core.ScheduleState

/**
 * Keeps exactly one alarm armed at the end of the current phase.
 *
 * Exact alarms are used when the platform allows them; otherwise the alarm is
 * still set as an idle-tolerant inexact alarm, so a missing permission degrades
 * timing instead of breaking the app.
 */
class AlarmScheduler(context: Context) {
    private val appContext = context.applicationContext

    private val alarmManager: AlarmManager?
        get() = appContext.getSystemService(AlarmManager::class.java)

    /** Reports whether exact alarms may currently be scheduled. */
    fun canScheduleExact(): Boolean {
        val manager = alarmManager ?: return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            manager.canScheduleExactAlarms()
        } else {
            true
        }
    }

    /** Replaces any pending alarm with one at [ScheduleState.phaseEnd]. */
    fun schedule(state: ScheduleState) {
        val manager = alarmManager ?: return
        val pending = pendingIntent()
        manager.cancel(pending)

        val triggerAt = state.phaseEnd.toInstant().toEpochMilli()
        if (triggerAt <= System.currentTimeMillis()) return

        if (canScheduleExact()) {
            try {
                manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
                return
            } catch (e: SecurityException) {
                // Permission was revoked between the check and the call.
            }
        }
        try {
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
        } catch (e: SecurityException) {
            // Nothing else can be done; the next app start reschedules.
        }
    }

    /** Cancels the pending phase alarm. */
    fun cancel() {
        alarmManager?.cancel(pendingIntent())
    }

    private fun pendingIntent(): PendingIntent = PendingIntent.getBroadcast(
        appContext,
        REQUEST_CODE,
        Intent(appContext, PhaseAlarmReceiver::class.java).setAction(ACTION_PHASE_ALARM),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    companion object {
        const val REQUEST_CODE = 1001
        private const val ACTION_PHASE_ALARM = "com.comtudia.app.action.PHASE_ALARM"
    }
}

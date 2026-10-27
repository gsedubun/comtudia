package com.comtudia.app.alarm

import android.content.Context
import android.util.Log
import com.comtudia.app.core.Schedule
import com.comtudia.app.core.ValidationException
import com.comtudia.app.core.phaseMessage
import com.comtudia.app.data.PhaseStateStore
import com.comtudia.app.data.PlanRepository
import com.comtudia.app.notify.PhaseNotifications
import java.time.ZonedDateTime

/**
 * Recomputes the schedule after an alarm or a system time change.
 *
 * Both receivers share this path so the "one alert per contiguous phase" rule
 * is applied in exactly one place. The phase key is recorded before posting so a
 * crash cannot replay the same alert, and an unprimed store only primes itself
 * instead of firing an alert on first launch or after a reboot.
 */
internal object PhaseRefresh {
    private const val TAG = "PhaseRefresh"

    /**
     * Serialized so an alarm and a time-change broadcast cannot both announce
     * the same phase.
     */
    @Synchronized
    fun run(context: Context) {
        val plan = PlanRepository(context).current()
        val state = try {
            Schedule.compute(plan, ZonedDateTime.now())
        } catch (e: ValidationException) {
            Log.w(TAG, "cannot compute schedule: ${e.message}")
            return
        } catch (e: IllegalStateException) {
            Log.w(TAG, "cannot compute schedule: ${e.message}")
            return
        }

        val store = PhaseStateStore(context)
        val key = state.phaseKey()
        val changed = store.isPrimed() && store.lastPhaseKey() != key

        // Record first: worst case an alert is skipped, never duplicated.
        store.setPhaseKey(key, true)

        val notifications = PhaseNotifications(context)
        when {
            !plan.notifications -> notifications.cancelOngoing()
            changed -> notifications.showPhaseChange(state, phaseMessage(state))
            else -> notifications.showOngoing(state)
        }

        AlarmScheduler(context).schedule(state)
    }
}

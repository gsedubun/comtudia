package com.comtudia.app.data

import android.content.Context
import com.comtudia.app.core.ScheduleState

/**
 * Remembers the last announced phase across process death and reboots.
 *
 * The receiver process cannot rely on an in-memory notifier, so the phase key
 * is persisted before the notification is posted. That keeps "one alert per
 * contiguous phase" true even when the app is killed between two alarms.
 */
class PhaseStateStore(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** The phase key that was announced last, or `null` before priming. */
    fun lastPhaseKey(): String? = prefs.getString(KEY_LAST_PHASE, null)

    /** Whether a phase has ever been recorded. */
    fun isPrimed(): Boolean = prefs.getBoolean(KEY_PRIMED, false)

    /** Records [state] as the announced phase. */
    fun record(state: ScheduleState) = setPhaseKey(state.phaseKey(), true)

    /** Stores [key] and the primed flag in one write. */
    fun setPhaseKey(key: String, primed: Boolean = true) {
        prefs.edit()
            .putString(KEY_LAST_PHASE, key)
            .putBoolean(KEY_PRIMED, primed)
            .apply()
    }

    /** Forgets everything, so the next phase is treated as the first one. */
    fun clear() {
        prefs.edit().clear().apply()
    }

    companion object {
        const val PREFS_NAME = "comtudia_state"
        private const val KEY_LAST_PHASE = "lastPhaseKey"
        private const val KEY_PRIMED = "primed"
    }
}

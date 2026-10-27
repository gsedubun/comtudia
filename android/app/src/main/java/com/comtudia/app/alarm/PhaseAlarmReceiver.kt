package com.comtudia.app.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Fires at the end of a phase. The work touches the disk and SharedPreferences,
 * so it runs on a short background thread while [goAsync] keeps the receiver
 * alive for the system.
 */
class PhaseAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val appContext = context.applicationContext
        val result = goAsync()
        Thread({
            try {
                PhaseRefresh.run(appContext)
            } catch (e: Exception) {
                Log.w(TAG, "phase alarm failed", e)
            } finally {
                result.finish()
            }
        }, TAG).start()
    }

    companion object {
        private const val TAG = "PhaseAlarm"
    }
}

package com.comtudia.app.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Rebuilds the schedule after a boot, a clock or timezone change, or an app
 * update. Alarms do not survive those events, so the next phase alarm has to be
 * armed again. The shared refresh path only alerts when the phase key really
 * changed, which keeps a reboot from repeating the last notification.
 */
class RescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val appContext = context.applicationContext
        val result = goAsync()
        Thread({
            try {
                PhaseRefresh.run(appContext)
            } catch (e: Exception) {
                Log.w(TAG, "reschedule failed", e)
            } finally {
                result.finish()
            }
        }, TAG).start()
    }

    companion object {
        private const val TAG = "PhaseReschedule"
    }
}

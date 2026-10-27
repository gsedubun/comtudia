package com.comtudia.app.notify

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.comtudia.app.MainActivity
import com.comtudia.app.R
import com.comtudia.app.core.Format
import com.comtudia.app.core.Message
import com.comtudia.app.core.Phase
import com.comtudia.app.core.ScheduleState

/**
 * Posts the ongoing phase card and phase-change alerts.
 *
 * The ongoing card uses a countdown chronometer, so SystemUI keeps the remaining
 * time ticking without a foreground service or a per-second wake-up.
 */
class PhaseNotifications(private val context: Context) {
    private val appContext = context.applicationContext

    private val manager: NotificationManager?
        get() = appContext.getSystemService(NotificationManager::class.java)

    /** Shows or updates the silent current-phase card (id 1001). */
    fun showOngoing(state: ScheduleState) {
        // Channels are cheap to create and are useful even before the user
        // grants the notification permission.
        Notifications.ensureChannels(appContext)
        if (!Notifications.canPostNotifications(appContext)) return

        val title = when (state.phase) {
            Phase.FASTING -> appContext.getString(R.string.notification_phase_fasting)
            Phase.EATING -> appContext.getString(R.string.notification_phase_eating)
        }

        val builder = Notification.Builder(appContext, Notifications.CHANNEL_ONGOING)
            .setSmallIcon(R.drawable.ic_stat_comtudia)
            .setContentTitle(title)
            .setContentText(
                appContext.getString(
                    R.string.notification_until,
                    Format.clock(state.phaseEnd),
                ),
            )
            .setSubText(appContext.getString(R.string.app_name))
            .setContentIntent(contentIntent())
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setWhen(state.phaseEnd.toInstant().toEpochMilli())

        post(Notifications.ID_ONGOING, builder.build())
    }

    /** Shows the phase-change alert (id 1002) and refreshes the ongoing card. */
    fun showPhaseChange(state: ScheduleState, message: Message) {
        Notifications.ensureChannels(appContext)
        if (!Notifications.canPostNotifications(appContext)) return

        val builder = Notification.Builder(appContext, Notifications.CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_stat_comtudia)
            .setContentTitle(message.title)
            .setContentText(message.body)
            .setStyle(Notification.BigTextStyle().bigText(message.body))
            .setSubText(appContext.getString(R.string.app_name))
            .setContentIntent(contentIntent())
            .setAutoCancel(true)
            .setCategory(Notification.CATEGORY_REMINDER)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setWhen(System.currentTimeMillis())
            .setShowWhen(true)

        post(Notifications.ID_ALERT, builder.build())
        showOngoing(state)
    }

    /** Removes the current-phase card, for example when alerts are disabled. */
    fun cancelOngoing() {
        manager?.cancel(Notifications.ID_ONGOING)
    }

    private fun post(id: Int, notification: Notification) {
        val manager = manager ?: return
        try {
            manager.notify(id, notification)
        } catch (e: SecurityException) {
            // The permission may have been revoked since the check above.
        }
    }

    private fun contentIntent(): PendingIntent {
        val intent = Intent(appContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            appContext,
            CONTENT_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        private const val CONTENT_REQUEST_CODE = 0
    }
}

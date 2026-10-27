package com.comtudia.app.notify

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.comtudia.app.R

/** Notification channels and the permission check shared by every poster. */
object Notifications {
    /** Low-importance, silent channel for the persistent current-phase card. */
    const val CHANNEL_ONGOING = "comtudia.ongoing"

    /** Default-importance channel for phase-change alerts. */
    const val CHANNEL_ALERTS = "comtudia.alerts"

    /** Notification id of the ongoing phase card. */
    const val ID_ONGOING = 1001

    /** Notification id of the phase-change alert. */
    const val ID_ALERT = 1002

    /** Creates both channels when they are missing. Safe to call repeatedly. */
    fun ensureChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return

        val ongoing = NotificationChannel(
            CHANNEL_ONGOING,
            context.getString(R.string.notification_channel_ongoing),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = context.getString(R.string.notification_channel_ongoing_description)
            setSound(null, null)
            enableVibration(false)
            enableLights(false)
            setShowBadge(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }

        val alerts = NotificationChannel(
            CHANNEL_ALERTS,
            context.getString(R.string.notification_channel_alerts),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.notification_channel_alerts_description)
            setShowBadge(true)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }

        manager.createNotificationChannels(listOf(ongoing, alerts))
    }

    /**
     * Reports whether the app may post notifications. Before Android 13 the
     * permission does not exist and posting is always allowed.
     */
    fun canPostNotifications(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    }
}

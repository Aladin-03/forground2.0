package com.aladin.foreground2.service.detection

import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.aladin.foreground2.MainActivity
import com.aladin.foreground2.R
import com.aladin.foreground2.core.logging.Log
import com.aladin.foreground2.core.permissions.Permission
import com.aladin.foreground2.core.permissions.names
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The detection service's ongoing notification, and the alert posted when a permission it needs (Usage access,
 * Display over other apps) has been turned off. Both open the app.
 */
@Singleton
class DetectionNotifications
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        private val manager = NotificationManagerCompat.from(context)

        /** The foreground-service notification. */
        fun ongoing(): Notification {
            createChannels()
            return NotificationCompat
                .Builder(context, ONGOING_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(context.getString(R.string.detection_notification_title))
                .setContentText(context.getString(R.string.detection_notification_text))
                .setContentIntent(openApp())
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
                .build()
        }

        /**
         * Tells the user that [missing] is off and watched apps aren't being paused. Replaces any earlier alert.
         * MissingPermission is suppressed because the notifications grant is checked first via areNotificationsEnabled.
         */
        @SuppressLint("MissingPermission")
        fun showPermissionsMissing(missing: List<Permission>) {
            createChannels()
            if (!manager.areNotificationsEnabled()) {
                Log.w(Log.Area.Perm) { "can't alert, notifications are off; missing=$missing" }
                return
            }
            val text = context.getString(R.string.problem_notification_text, missing.names(context))
            val notification =
                NotificationCompat
                    .Builder(context, PROBLEM_CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setContentTitle(context.getString(R.string.problem_notification_title))
                    .setContentText(text)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                    .setContentIntent(openApp())
                    .setCategory(NotificationCompat.CATEGORY_ERROR)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setOnlyAlertOnce(true)
                    .build()
            manager.notify(PROBLEM_NOTIFICATION_ID, notification)
            Log.i(Log.Area.Perm) { "missing-permission alert shown missing=$missing" }
        }

        /** Removes the missing-permission alert, if showing. */
        fun clearPermissionsMissing() {
            manager.cancel(PROBLEM_NOTIFICATION_ID)
        }

        private fun createChannels() {
            manager.createNotificationChannelsCompat(
                listOf(
                    NotificationChannelCompat
                        .Builder(ONGOING_CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_LOW)
                        .setName(context.getString(R.string.detection_channel_name))
                        .setShowBadge(false)
                        .build(),
                    NotificationChannelCompat
                        .Builder(PROBLEM_CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_HIGH)
                        .setName(context.getString(R.string.problem_channel_name))
                        .build(),
                ),
            )
        }

        private fun openApp(): PendingIntent =
            PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE,
            )

        companion object {
            /** Also used by [DetectionService.onStartCommand] for startForeground. */
            const val ONGOING_NOTIFICATION_ID = 1
            private const val PROBLEM_NOTIFICATION_ID = 2
            private const val ONGOING_CHANNEL_ID = "detection"
            private const val PROBLEM_CHANNEL_ID = "problems"
        }
    }

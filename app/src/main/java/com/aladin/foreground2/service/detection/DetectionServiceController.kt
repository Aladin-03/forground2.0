package com.aladin.foreground2.service.detection

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.aladin.foreground2.core.logging.Log
import com.aladin.foreground2.core.permissions.PermissionChecker
import com.aladin.foreground2.core.permissions.missingRequired
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Starts [DetectionService] and reports whether it is running. */
interface DetectionServiceController {
    /** Whether the service is running in this process. */
    val running: StateFlow<Boolean>

    /**
     * Starts the service if every required permission is granted, otherwise posts the missing-permission alert.
     * Returns whether a start was requested. Safe to call repeatedly; a running service ignores the extra start.
     */
    fun startIfReady(): Boolean
}

@Singleton
class AndroidDetectionServiceController
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val permissionChecker: PermissionChecker,
        private val notifications: DetectionNotifications,
        status: DetectionStatus,
    ) : DetectionServiceController {
        override val running: StateFlow<Boolean> = status.running

        override fun startIfReady(): Boolean {
            val missing = permissionChecker.missingRequired()
            if (missing.isNotEmpty()) {
                // Reached from BootReceiver when a permission was revoked before the reboot: tell the user.
                Log.w(Log.Area.Detect) { "service not started, missing=$missing" }
                notifications.showPermissionsMissing(missing)
                return false
            }
            return try {
                ContextCompat.startForegroundService(context, Intent(context, DetectionService::class.java))
                Log.i(Log.Area.Detect) { "service start requested" }
                true
            } catch (e: IllegalStateException) {
                // ForegroundServiceStartNotAllowedException when started from the background without an exemption.
                Log.e(Log.Area.Detect, e) { "service start refused" }
                false
            }
        }
    }

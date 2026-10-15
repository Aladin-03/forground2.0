package com.aladin.foreground2.service.detection

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.aladin.foreground2.core.logging.Log
import com.aladin.foreground2.core.permissions.PermissionChecker
import com.aladin.foreground2.core.permissions.missingRequired
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Starts [DetectionService]. Safe to call repeatedly; a running service just ignores the extra start. */
fun interface DetectionServiceController {
    /** Starts the service if every required permission is granted. Returns whether a start was requested. */
    fun startIfReady(): Boolean
}

@Singleton
class AndroidDetectionServiceController
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val permissionChecker: PermissionChecker,
    ) : DetectionServiceController {
        override fun startIfReady(): Boolean {
            val missing = permissionChecker.missingRequired()
            if (missing.isNotEmpty()) {
                Log.w(Log.Area.Detect) { "service not started, missing=$missing" }
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

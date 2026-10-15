package com.aladin.foreground2.service.detection

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.aladin.foreground2.core.logging.Log
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/**
 * Restarts detection after a reboot, and after the app is updated (which kills the running service). Both
 * broadcasts are exempt from the background foreground-service start restriction.
 */
class BootReceiver : BroadcastReceiver() {
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface BootReceiverEntryPoint {
        fun detectionServiceController(): DetectionServiceController
    }

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action !in HandledActions) return
        Log.i(Log.Area.Detect) { "received ${intent.action}" }
        EntryPointAccessors
            .fromApplication(context, BootReceiverEntryPoint::class.java)
            .detectionServiceController()
            .startIfReady()
    }

    private companion object {
        val HandledActions = setOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED)
    }
}

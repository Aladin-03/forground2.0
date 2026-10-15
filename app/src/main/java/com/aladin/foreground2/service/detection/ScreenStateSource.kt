package com.aladin.foreground2.service.detection

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import androidx.core.content.ContextCompat
import com.aladin.foreground2.core.config.Timing
import com.aladin.foreground2.core.logging.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Whether the screen is on and unlocked. Goes false on SCREEN_OFF. After SCREEN_ON it goes true once the keyguard
 * is gone: XOS withholds USER_PRESENT from some apps, so instead of relying on it the keyguard is checked every
 * poll interval until it is dismissed (USER_PRESENT still counts if it does arrive). Screen broadcasts can only be
 * received by a registered receiver.
 */
@Singleton
class ScreenStateSource
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val timing: Timing,
    ) {
        private val power = context.getSystemService(PowerManager::class.java)
        private val keyguard = context.getSystemService(KeyguardManager::class.java)

        val unlocked: Flow<Boolean> =
            callbackFlow {
                var waitForUnlock: Job? = null
                val receiver =
                    object : BroadcastReceiver() {
                        override fun onReceive(
                            context: Context,
                            intent: Intent,
                        ) {
                            Log.d(Log.Area.Detect) { "screen ${intent.action?.substringAfterLast('.')}" }
                            waitForUnlock?.cancel()
                            when (intent.action) {
                                Intent.ACTION_SCREEN_OFF -> trySend(false)
                                Intent.ACTION_SCREEN_ON ->
                                    waitForUnlock =
                                        launch {
                                            while (keyguard.isKeyguardLocked) delay(timing.pollIntervalMillis)
                                            send(true)
                                        }
                                Intent.ACTION_USER_PRESENT -> trySend(true)
                            }
                        }
                    }
                val filter =
                    IntentFilter().apply {
                        addAction(Intent.ACTION_SCREEN_OFF)
                        addAction(Intent.ACTION_SCREEN_ON)
                        addAction(Intent.ACTION_USER_PRESENT)
                    }
                // System broadcasts still reach a non-exported receiver.
                ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
                trySend(power.isInteractive && !keyguard.isKeyguardLocked)
                awaitClose { context.unregisterReceiver(receiver) }
            }.distinctUntilChanged()
    }

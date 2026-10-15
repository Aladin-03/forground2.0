package com.aladin.foreground2.service.detection

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.aladin.foreground2.MainActivity
import com.aladin.foreground2.R
import com.aladin.foreground2.core.config.Timing
import com.aladin.foreground2.core.data.repo.WatchedAppsRepository
import com.aladin.foreground2.core.domain.Clock
import com.aladin.foreground2.core.logging.Log
import com.aladin.foreground2.service.overlay.OverlayEffects
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Foreground service (type specialUse) that keeps detection alive: polls the front app while the screen is on and
 * unlocked, and feeds front-app changes, poll ticks, overlay events and the watched-apps list into [GateCoordinator].
 * The coordinator's overlay effects are carried out by [OverlayEffects].
 */
@AndroidEntryPoint
class DetectionService : Service() {
    @Inject lateinit var detector: FrontAppDetector

    @Inject lateinit var coordinator: GateCoordinator

    @Inject lateinit var screenState: ScreenStateSource

    @Inject lateinit var watchedApps: WatchedAppsRepository

    @Inject lateinit var overlays: OverlayEffects

    @Inject lateinit var clock: Clock

    @Inject lateinit var timing: Timing

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null

    override fun onCreate() {
        super.onCreate()
        Log.i(Log.Area.Detect) { "service onCreate" }
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
        )
        if (job == null) {
            Log.i(Log.Area.Detect) { "service started (restart=${intent == null})" }
            job = scope.launch { run() }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        overlays.hideAll()
        Log.i(Log.Area.Detect) { "service onDestroy" }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun run() {
        // Load the watched list before the first poll so an app already in front is recognised.
        val watched = watchedApps.observeWatched().stateIn(scope)
        coordinator.handle(GateInput.WatchedChanged(watched.value))
        val polling =
            screenState.unlocked.flatMapLatest { unlocked ->
                Log.i(Log.Area.Detect) { if (unlocked) "unlocked, polling" else "screen off, polling paused" }
                if (unlocked) merge(detector.changes().map(GateInput::FrontChanged), ticks()) else emptyFlow()
            }
        // One collector, so the coordinator sees inputs strictly one at a time and effects run in order.
        // Overlay events keep flowing while the screen is off, so a breath can still finish then.
        merge(watched.drop(1).map(GateInput::WatchedChanged), polling, overlays.inputs).collect { input ->
            coordinator.handle(input).forEach { overlays.apply(it) }
        }
    }

    private fun ticks(): Flow<GateInput.Tick> =
        flow {
            while (true) {
                delay(timing.pollIntervalMillis)
                emit(GateInput.Tick(clock.nowMillis()))
            }
        }

    private fun buildNotification(): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.detection_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply { setShowBadge(false) },
        )
        val openApp =
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE,
            )
        return NotificationCompat
            .Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.detection_notification_title))
            .setContentText(getString(R.string.detection_notification_text))
            .setContentIntent(openApp)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private companion object {
        const val CHANNEL_ID = "detection"
        const val NOTIFICATION_ID = 1
    }
}

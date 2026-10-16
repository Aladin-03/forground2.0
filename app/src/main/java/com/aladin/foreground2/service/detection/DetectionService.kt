package com.aladin.foreground2.service.detection

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.ServiceCompat
import com.aladin.foreground2.core.config.Timing
import com.aladin.foreground2.core.data.repo.WatchedAppsRepository
import com.aladin.foreground2.core.domain.Clock
import com.aladin.foreground2.core.logging.Log
import com.aladin.foreground2.core.permissions.Permission
import com.aladin.foreground2.core.permissions.PermissionChecker
import com.aladin.foreground2.core.permissions.missingRequired
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
import kotlinx.coroutines.flow.distinctUntilChanged
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
 *
 * START_STICKY, so the system recreates it after killing the process; [BootReceiver] starts it after a reboot. Every
 * start rereads the locks from Room. While running it rechecks the required permissions and posts an alert if one is
 * revoked (detection or overlays silently stop working otherwise), clearing it once they are back.
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

    @Inject lateinit var permissionChecker: PermissionChecker

    @Inject lateinit var notifications: DetectionNotifications

    @Inject lateinit var status: DetectionStatus

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
            DetectionNotifications.ONGOING_NOTIFICATION_ID,
            notifications.ongoing(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
        )
        status.setRunning(true)
        if (job == null) {
            // intent is null when the system recreates the service after killing the process (START_STICKY).
            Log.i(Log.Area.Detect) { "service started (restart=${intent == null})" }
            job = scope.launch { run() }
            scope.launch { watchPermissions() }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        overlays.hideAll()
        status.setRunning(false)
        Log.i(Log.Area.Detect) { "service onDestroy" }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun run() {
        // Locks are read from Room on every start. Load them and the watched list before the first poll, so an app
        // already in front is recognised (as a fresh entry, since the detector starts over too).
        detector.reset()
        coordinator.loadLocks()
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

    /**
     * Keeps running while a permission is missing: detection and overlays pick up again by themselves once it is
     * granted back, and the alert is cleared.
     */
    private suspend fun watchPermissions() {
        missingPermissions().collect { missing ->
            if (missing.isEmpty()) {
                Log.i(Log.Area.Perm) { "required permissions granted" }
                notifications.clearPermissionsMissing()
            } else {
                Log.w(Log.Area.Perm) { "required permissions missing=$missing" }
                notifications.showPermissionsMissing(missing)
            }
        }
    }

    private fun missingPermissions(): Flow<List<Permission>> =
        flow {
            while (true) {
                emit(permissionChecker.missingRequired())
                delay(timing.permissionCheckIntervalMillis)
            }
        }.distinctUntilChanged()
}

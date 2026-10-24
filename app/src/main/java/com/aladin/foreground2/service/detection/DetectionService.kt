package com.aladin.foreground2.service.detection

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.ServiceCompat
import com.aladin.foreground2.core.config.Timing
import com.aladin.foreground2.core.data.repo.LockRepository
import com.aladin.foreground2.core.data.repo.WatchedAppsRepository
import com.aladin.foreground2.core.data.usage.HistoryTrigger
import com.aladin.foreground2.core.data.usage.UsageHistory
import com.aladin.foreground2.core.domain.Clock
import com.aladin.foreground2.core.domain.ZoneProvider
import com.aladin.foreground2.core.logging.Log
import com.aladin.foreground2.core.permissions.Permission
import com.aladin.foreground2.core.permissions.PermissionChecker
import com.aladin.foreground2.core.permissions.missingRequired
import com.aladin.foreground2.service.overlay.MediaPauser
import com.aladin.foreground2.service.overlay.OverlayEffects
import com.aladin.foreground2.widget.WidgetRefreshTriggers
import com.aladin.foreground2.widget.WidgetRefresher
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
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Foreground service (type specialUse) that keeps detection alive: polls the front app while the screen is on and
 * unlocked, and feeds front-app changes, poll ticks, overlay events and the watched-apps list into [GateCoordinator].
 * The coordinator's overlay effects are carried out by [OverlayEffects].
 *
 * START_STICKY, so the system recreates it after killing the process; [BootReceiver] starts it after a reboot. Every
 * start rereads the locks from Room. While running it rechecks the required permissions and posts an alert if one is
 * revoked (detection or overlays silently stop working otherwise), clearing it once they are back. It also drives
 * the home-screen widget refreshes ([WidgetRefreshTriggers]): on unlock and periodically while unlocked, and saves
 * each finished day into the usage history on its first poll after local midnight.
 */
@AndroidEntryPoint
class DetectionService : Service() {
    @Inject lateinit var detector: FrontAppDetector

    @Inject lateinit var coordinator: GateCoordinator

    @Inject lateinit var screenState: ScreenStateSource

    @Inject lateinit var watchedApps: WatchedAppsRepository

    @Inject lateinit var locks: LockRepository

    @Inject lateinit var overlays: OverlayEffects

    @Inject lateinit var mediaPauser: MediaPauser

    @Inject lateinit var clock: Clock

    @Inject lateinit var timing: Timing

    @Inject lateinit var permissionChecker: PermissionChecker

    @Inject lateinit var notifications: DetectionNotifications

    @Inject lateinit var status: DetectionStatus

    @Inject lateinit var widgetTriggers: WidgetRefreshTriggers

    @Inject lateinit var widgetRefresher: WidgetRefresher

    @Inject lateinit var usageHistory: UsageHistory

    @Inject lateinit var zones: ZoneProvider

    private val dayWatcher by lazy { DayChangeWatcher(zones) }

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
            scope.launch { usageHistory.catchUp(HistoryTrigger.ServiceStart) }
            scope.launch { widgetTriggers.triggers(screenState.unlocked).collect { widgetRefresher.refresh(it) } }
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
        withContext(Dispatchers.Main) { mediaPauser.restoreAfterCrash() }
        coordinator.loadLocks()
        val watched = watchedApps.observeWatched().stateIn(scope)
        coordinator.handle(GateInput.WatchedChanged(watched.value))
        val polling =
            screenState.unlocked.flatMapLatest { unlocked ->
                Log.i(Log.Area.Detect) { if (unlocked) "unlocked, polling" else "screen off, polling paused" }
                if (unlocked) merge(detector.changes().map(GateInput::FrontChanged), ticks()) else emptyFlow()
            }
        // One collector, so the coordinator sees inputs strictly one at a time and effects run in order.
        // Overlay events keep flowing while the screen is off, so a breath can still finish then. The locks table is
        // observed too, so a lock started from Home is enforced straight away.
        merge(
            watched.drop(1).map(GateInput::WatchedChanged),
            locks.observeLocks().map(GateInput::LocksChanged),
            polling,
            overlays.inputs,
        ).collect { input ->
            coordinator.handle(input).forEach { overlays.apply(it) }
            // The first poll after local midnight saves the day that just ended.
            if (input is GateInput.Tick && dayWatcher.dayChanged(input.at)) {
                scope.launch { usageHistory.catchUp(HistoryTrigger.Midnight, rewriteYesterday = true) }
            }
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

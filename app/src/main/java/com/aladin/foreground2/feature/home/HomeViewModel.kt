package com.aladin.foreground2.feature.home

import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aladin.foreground2.core.data.apps.LauncherAppSource
import com.aladin.foreground2.core.data.repo.LockRepository
import com.aladin.foreground2.core.data.repo.WatchedAppsRepository
import com.aladin.foreground2.core.domain.AppInfo
import com.aladin.foreground2.core.domain.Clock
import com.aladin.foreground2.core.logging.Log
import com.aladin.foreground2.core.permissions.Permission
import com.aladin.foreground2.core.permissions.PermissionChecker
import com.aladin.foreground2.service.detection.DetectionServiceController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class HomeViewModel
    @Inject
    constructor(
        private val permissionChecker: PermissionChecker,
        private val detectionServiceController: DetectionServiceController,
        private val launcherAppSource: LauncherAppSource,
        lockRepository: LockRepository,
        watchedAppsRepository: WatchedAppsRepository,
        /** Drives each row's live countdown. */
        val clock: Clock,
    ) : ViewModel() {
        private val missing = MutableStateFlow(currentMissing())

        /** The last locked list written to the log, so each change is logged once. */
        private var loggedLocks: List<LockedApp>? = null

        /**
         * Both lists, from the locks and watched tables (Room flows, so a new lock shows up without a refresh).
         * Lock labels come from the watched list, falling back to the package manager for an app unwatched while
         * locked. Ended locks are dropped here; the service deletes their rows on its next tick.
         */
        @OptIn(ExperimentalCoroutinesApi::class)
        private val lists: Flow<Pair<List<LockedApp>, List<WatchedApp>>> =
            combine(lockRepository.observeLocks(), watchedAppsRepository.observeWatched()) { locks, watched ->
                locks to watched
            }.mapLatest { (locks, watched) ->
                val now = clock.nowMillis()
                val labels = watched.associate { it.packageName to it.label }
                val running = locks.filterValues { it > now }
                val lockedApps =
                    running
                        .map { (pkg, until) ->
                            LockedApp(AppInfo(pkg, labels[pkg] ?: launcherAppSource.label(pkg) ?: pkg), until)
                        }.sortedBy { it.unlockAtMillis }
                val watchedApps =
                    watched
                        .sortedBy { it.label.lowercase() }
                        .map { WatchedApp(it, locked = it.packageName in running) }
                logIfChanged(lockedApps, now)
                lockedApps to watchedApps
            }

        val uiState: StateFlow<HomeUiState> =
            combine(lists, missing, detectionServiceController.running) { (locks, watched), missing, running ->
                HomeUiState(locks = locks, watched = watched, missing = missing, serviceRunning = running)
            }.stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                HomeUiState(missing = missing.value, serviceRunning = detectionServiceController.running.value),
            )

        /** Rechecks every permission. Called on each ON_RESUME, since grants and revocations happen in Settings. */
        fun refresh() {
            val current = currentMissing()
            if (current != missing.value) Log.i(Log.Area.Perm) { "home: missing=$current" }
            missing.value = current
        }

        fun onStartService() {
            Log.i(Log.Area.UI) { "home: start service tapped" }
            detectionServiceController.startIfReady()
            refresh()
        }

        suspend fun loadIcon(packageName: String): ImageBitmap? = launcherAppSource.icon(packageName)

        private fun logIfChanged(
            locks: List<LockedApp>,
            now: Long,
        ) {
            if (locks == loggedLocks) return
            loggedLocks = locks
            Log.i(Log.Area.UI) {
                "home: locked=" +
                    locks.map {
                        "${it.app.label} (${it.app.packageName}) unlockAt=${it.unlockAtMillis} " +
                            "in ${(it.unlockAtMillis - now) / MINUTE_MILLIS}m"
                    }
            }
        }

        private fun currentMissing(): List<Permission> = Permission.entries.filterNot(permissionChecker::isGranted)

        private companion object {
            const val STOP_TIMEOUT_MILLIS = 5_000L
            const val MINUTE_MILLIS = 60_000L
        }
    }

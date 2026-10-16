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

        /**
         * Labels come from the watched list, falling back to the package manager for an app unwatched while locked.
         * Ended locks are dropped here; the service deletes their rows on its next tick.
         */
        @OptIn(ExperimentalCoroutinesApi::class)
        private val locks: Flow<List<LockedApp>> =
            combine(lockRepository.observeLocks(), watchedAppsRepository.observeWatched()) { locks, watched ->
                locks to watched.associate { it.packageName to it.label }
            }.mapLatest { (locks, labels) ->
                val now = clock.nowMillis()
                locks
                    .filterValues { it > now }
                    .map { (pkg, until) ->
                        LockedApp(AppInfo(pkg, labels[pkg] ?: launcherAppSource.label(pkg) ?: pkg), until)
                    }.sortedBy { it.unlockAtMillis }
            }

        val uiState: StateFlow<HomeUiState> =
            combine(locks, missing, detectionServiceController.running) { locks, missing, running ->
                HomeUiState(locks = locks, missing = missing, serviceRunning = running)
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

        private fun currentMissing(): List<Permission> = Permission.entries.filterNot(permissionChecker::isGranted)

        private companion object {
            const val STOP_TIMEOUT_MILLIS = 5_000L
        }
    }

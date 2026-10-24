package com.aladin.foreground2.feature.lock

import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aladin.foreground2.core.data.apps.LauncherAppSource
import com.aladin.foreground2.core.data.repo.LockRepository
import com.aladin.foreground2.core.data.repo.WatchedAppsRepository
import com.aladin.foreground2.core.domain.AppInfo
import com.aladin.foreground2.core.domain.Clock
import com.aladin.foreground2.core.domain.LockDuration
import com.aladin.foreground2.core.logging.Log
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Locks one app from Home. Whether it is already locked is read once on open, so saving a lock doesn't flip the
 * screen to the read-only view on its way back. Start goes through the same [LockRepository.lock] as the overlay, so
 * an existing later lock is kept; the running service picks the new row up from the locks table.
 */
@HiltViewModel
class LockAppViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        private val lockRepository: LockRepository,
        private val watchedAppsRepository: WatchedAppsRepository,
        private val launcherAppSource: LauncherAppSource,
        /** Drives the picker's "Unlocks at" preview and the locked view's countdown. */
        val clock: Clock,
    ) : ViewModel() {
        private val packageName: String = checkNotNull(savedStateHandle[LockAppRoute.ARG_PACKAGE_NAME])
        private val state = MutableStateFlow<LockAppUiState>(LockAppUiState.Loading)
        val uiState: StateFlow<LockAppUiState> = state.asStateFlow()

        /** The save in progress, so a second tap on Start is ignored. */
        private var starting: Job? = null

        init {
            viewModelScope.launch { state.value = load() }
        }

        fun onStart(duration: LockDuration) {
            val picking = state.value as? LockAppUiState.Picking ?: return
            if (duration.isZero || starting != null) return
            starting =
                viewModelScope.launch {
                    val requested = clock.nowMillis() + duration.millis
                    val stored = lockRepository.lock(packageName, requested)
                    Log.i(Log.Area.UI) {
                        "lock started from Home ${picking.app.label} ($packageName) for $duration until=$stored" +
                            if (stored != requested) " (kept later existing lock)" else ""
                    }
                    state.value = LockAppUiState.Started
                }
        }

        suspend fun loadIcon(packageName: String): ImageBitmap? = launcherAppSource.icon(packageName)

        private suspend fun load(): LockAppUiState {
            val label =
                watchedAppsRepository
                    .observeWatched()
                    .first()
                    .find { it.packageName == packageName }
                    ?.label
                    ?: launcherAppSource.label(packageName)
                    ?: packageName
            val app = AppInfo(packageName, label)
            val until = lockRepository.activeLocks()[packageName]
            Log.i(Log.Area.UI) { "lock app: opened $label ($packageName), locked until=$until" }
            return if (until != null) LockAppUiState.Locked(app, until) else LockAppUiState.Picking(app)
        }
    }

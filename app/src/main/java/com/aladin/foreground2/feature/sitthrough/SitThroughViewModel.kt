package com.aladin.foreground2.feature.sitthrough

import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aladin.foreground2.core.config.Timing
import com.aladin.foreground2.core.data.apps.LauncherAppSource
import com.aladin.foreground2.core.data.repo.WatchChanges
import com.aladin.foreground2.core.data.repo.WatchedAppsRepository
import com.aladin.foreground2.core.domain.AppInfo
import com.aladin.foreground2.core.domain.Clock
import com.aladin.foreground2.core.domain.Countdown
import com.aladin.foreground2.core.domain.watch.PendingChange
import com.aladin.foreground2.core.domain.watch.SitThrough
import com.aladin.foreground2.core.domain.watch.WatchDecision
import com.aladin.foreground2.core.domain.watch.WatchMode
import com.aladin.foreground2.core.domain.watch.WatchRequest
import com.aladin.foreground2.core.domain.watch.leave
import com.aladin.foreground2.core.domain.watch.tick
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
 * Runs one sit-through countdown (docs/SPEC.md "Watch modes"). The change is only written when the countdown
 * completes on screen; until then the app stays watched in its current mode. [onLeave] (Back, Cancel, the screen
 * stopping for Home, another app or screen off) cancels it. A view model restored after process death finds the
 * countdown already started and cancels it too, so the next attempt starts from the full duration.
 */
@HiltViewModel
class SitThroughViewModel
    @Inject
    constructor(
        private val savedStateHandle: SavedStateHandle,
        private val changes: WatchChanges,
        private val watchedApps: WatchedAppsRepository,
        private val launcherAppSource: LauncherAppSource,
        val clock: Clock,
        private val timing: Timing,
    ) : ViewModel() {
        private val packageName: String = checkNotNull(savedStateHandle[SitThroughRoute.ARG_PACKAGE_NAME])
        private val modeKey: String? = savedStateHandle[SitThroughRoute.ARG_MODE_KEY]

        private val state = MutableStateFlow<SitThroughUiState>(SitThroughUiState.Loading)
        val uiState: StateFlow<SitThroughUiState> = state.asStateFlow()

        private var sitThrough: SitThrough? = null
        private var ticking: Job? = null

        init {
            if (savedStateHandle.get<Boolean>(KEY_STARTED) == true) {
                Log.i(Log.Area.Mode) { "countdown cancel $packageName: restored after process death" }
                state.value = SitThroughUiState.Finished
            } else {
                viewModelScope.launch { start() }
            }
        }

        /** Why a countdown stopped short, for the log. */
        enum class LeaveReason { Back, Cancel, LeftScreen, Closed }

        /** Back, Cancel, or the screen being left: cancels a running countdown, and nothing changes. */
        fun onLeave(reason: LeaveReason) {
            val running = sitThrough as? SitThrough.Running
            if (running != null) {
                sitThrough = running.leave()
                ticking?.cancel()
                Log.i(Log.Area.Mode) {
                    "countdown cancel ${describe(running.change)} reason=$reason " +
                        "with ${(running.endsAt - clock.nowMillis()) / SECOND_MILLIS}s left"
                }
            }
            state.value = SitThroughUiState.Finished
        }

        suspend fun loadIcon(packageName: String): ImageBitmap? = launcherAppSource.icon(packageName)

        override fun onCleared() {
            onLeave(LeaveReason.Closed)
        }

        private suspend fun start() {
            val label =
                watchedApps
                    .observeWatched()
                    .first()
                    .find { it.packageName == packageName }
                    ?.label
                    ?: launcherAppSource.label(packageName)
                    ?: packageName
            val to = modeKey?.let(WatchMode::fromKey)
            val request = if (to == null) WatchRequest.Disable else WatchRequest.ChangeMode(to)
            val decision = changes.request(AppInfo(packageName, label), request)
            if (decision !is WatchDecision.NeedsCountdown) {
                // Tightening was applied by request(); a lock or a stale route means there is nothing to wait for.
                state.value = SitThroughUiState.Finished
                return
            }
            val running = SitThrough.start(decision.change, clock.nowMillis(), timing.sitThroughMillis)
            sitThrough = running
            savedStateHandle[KEY_STARTED] = true
            Log.i(Log.Area.Mode) {
                "countdown start ${describe(decision.change)} for ${timing.sitThroughMillis / SECOND_MILLIS}s"
            }
            state.value = SitThroughUiState.Running(packageName, label, to, running.endsAt)
            ticking =
                viewModelScope.launch {
                    Countdown(running.endsAt, clock).secondsLeft().collect { if (it == 0) complete() }
                }
        }

        private suspend fun complete() {
            val next = sitThrough?.tick(clock.nowMillis())
            if (next !is SitThrough.Completed) return
            sitThrough = next
            val applied = changes.complete(next.change)
            Log.i(Log.Area.Mode) { "countdown complete ${describe(next.change)} applied=$applied" }
            state.value = SitThroughUiState.Finished
        }

        private fun describe(change: PendingChange): String =
            when (change) {
                is PendingChange.Loosen -> "loosen ${change.packageName} ${change.from.key} -> ${change.to.key}"
                is PendingChange.Disable -> "disable ${change.packageName}"
            }

        internal companion object {
            /** Set once a countdown starts; finding it in a fresh view model means the process died meanwhile. */
            const val KEY_STARTED = "sitThroughStarted"
            private const val SECOND_MILLIS = 1_000L
        }
    }

package com.aladin.foreground2.feature.picker

import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aladin.foreground2.core.data.apps.LauncherAppSource
import com.aladin.foreground2.core.data.repo.LockRepository
import com.aladin.foreground2.core.data.repo.WatchChanges
import com.aladin.foreground2.core.data.repo.WatchedAppsRepository
import com.aladin.foreground2.core.domain.AppInfo
import com.aladin.foreground2.core.domain.Clock
import com.aladin.foreground2.core.domain.watch.WatchDecision
import com.aladin.foreground2.core.domain.watch.WatchMode
import com.aladin.foreground2.core.domain.watch.WatchRequest
import com.aladin.foreground2.feature.sitthrough.SitThroughRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The watched-apps picker. Switching an app on goes through the mode chooser ([onEnable] with the picked mode);
 * switching it off asks for the sit-through countdown ([sitThrough] emits its route), refused while it is locked.
 */
@HiltViewModel
class PickerViewModel
    @Inject
    constructor(
        private val launcherAppSource: LauncherAppSource,
        watchedAppsRepository: WatchedAppsRepository,
        lockRepository: LockRepository,
        private val changes: WatchChanges,
        /** Drives the "Locked · <time left>" text. */
        val clock: Clock,
    ) : ViewModel() {
        private val _query = MutableStateFlow("")

        /** Search text, kept outside [uiState] so the text field updates synchronously while typing. */
        val query: StateFlow<String> = _query.asStateFlow()

        private val launcherApps =
            flow<List<AppInfo>?> { emit(launcherAppSource.launcherApps()) }.onStart { emit(null) }

        val uiState: StateFlow<PickerUiState> =
            combine(
                launcherApps,
                watchedAppsRepository.observeWatched(),
                lockRepository.observeLocks(),
                _query,
            ) { apps, watched, locks, q ->
                val now = clock.nowMillis()
                PickerUiState(
                    loading = apps == null,
                    items =
                        if (apps ==
                            null
                        ) {
                            emptyList()
                        } else {
                            pickerItems(apps, watched, locks.filterValues { it > now }, q)
                        },
                )
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), PickerUiState())

        private val sitThroughRequests = Channel<SitThroughRoute>(Channel.BUFFERED)

        /** One route per disable that needs its countdown. */
        val sitThrough: Flow<SitThroughRoute> = sitThroughRequests.receiveAsFlow()

        fun onQueryChange(value: String) {
            _query.value = value
        }

        fun windowMillis(mode: WatchMode): Long = changes.windowMillis(mode)

        /** A mode was picked in the chooser opened by switching [app] on. */
        fun onEnable(
            app: AppInfo,
            mode: WatchMode,
        ) {
            viewModelScope.launch { changes.request(app, WatchRequest.Enable(mode)) }
        }

        /** [app]'s switch was turned off. */
        fun onDisable(app: AppInfo) {
            viewModelScope.launch {
                if (changes.request(app, WatchRequest.Disable) is WatchDecision.NeedsCountdown) {
                    sitThroughRequests.send(SitThroughRoute(app.packageName))
                }
            }
        }

        suspend fun loadIcon(packageName: String): ImageBitmap? = launcherAppSource.icon(packageName)

        private companion object {
            const val STOP_TIMEOUT_MILLIS = 5_000L
        }
    }

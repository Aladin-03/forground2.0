package com.aladin.foreground2.feature.picker

import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aladin.foreground2.core.data.apps.LauncherAppSource
import com.aladin.foreground2.core.data.repo.WatchedAppsRepository
import com.aladin.foreground2.core.domain.AppInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PickerViewModel
    @Inject
    constructor(
        private val launcherAppSource: LauncherAppSource,
        private val watchedAppsRepository: WatchedAppsRepository,
    ) : ViewModel() {
        private val _query = MutableStateFlow("")

        /** Search text, kept outside [uiState] so the text field updates synchronously while typing. */
        val query: StateFlow<String> = _query.asStateFlow()

        private val launcherApps =
            flow<List<AppInfo>?> { emit(launcherAppSource.launcherApps()) }.onStart { emit(null) }

        val uiState: StateFlow<PickerUiState> =
            combine(launcherApps, watchedAppsRepository.observeWatched(), _query) { apps, watched, q ->
                PickerUiState(
                    loading = apps == null,
                    items = if (apps == null) emptyList() else pickerItems(apps, watched, q),
                )
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), PickerUiState())

        fun onQueryChange(value: String) {
            _query.value = value
        }

        fun onToggle(
            app: AppInfo,
            watched: Boolean,
        ) {
            viewModelScope.launch {
                if (watched) {
                    watchedAppsRepository.watch(app)
                } else {
                    watchedAppsRepository.unwatch(app.packageName)
                }
            }
        }

        suspend fun loadIcon(packageName: String): ImageBitmap? = launcherAppSource.icon(packageName)

        private companion object {
            const val STOP_TIMEOUT_MILLIS = 5_000L
        }
    }

package com.aladin.foreground2.feature.stats

import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aladin.foreground2.core.data.apps.LauncherAppSource
import com.aladin.foreground2.core.data.repo.UsageStatsRepository
import com.aladin.foreground2.core.domain.AppInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class StatsViewModel
    @Inject
    constructor(
        private val usageStatsRepository: UsageStatsRepository,
        private val launcherAppSource: LauncherAppSource,
    ) : ViewModel() {
        private val state = MutableStateFlow(StatsUiState())
        val uiState: StateFlow<StatsUiState> = state.asStateFlow()

        private var loading: Job? = null

        /** Rereads today's stats. Called on each ON_RESUME, so coming back from another app shows fresh numbers. */
        fun refresh() {
            loading?.cancel()
            loading =
                viewModelScope.launch {
                    val usage = usageStatsRepository.today()
                    val apps =
                        usage.apps.map {
                            val label = launcherAppSource.label(it.packageName) ?: it.packageName
                            AppTime(AppInfo(it.packageName, label), it.foregroundMillis)
                        }
                    state.value =
                        StatsUiState(
                            loading = false,
                            unlocks = usage.unlocks,
                            totalMillis = usage.totalMillis,
                            apps = apps,
                        )
                }
        }

        suspend fun loadIcon(packageName: String): ImageBitmap? = launcherAppSource.icon(packageName)
    }

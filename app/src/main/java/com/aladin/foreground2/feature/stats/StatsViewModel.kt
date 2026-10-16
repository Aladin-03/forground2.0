package com.aladin.foreground2.feature.stats

import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aladin.foreground2.core.data.apps.LauncherAppSource
import com.aladin.foreground2.core.data.repo.UsageStatsRepository
import com.aladin.foreground2.core.data.usage.HistoryTrigger
import com.aladin.foreground2.core.data.usage.UsageHistory
import com.aladin.foreground2.core.domain.usage.buildWeeks
import com.aladin.foreground2.core.domain.usage.colourSlots
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class StatsViewModel
    @Inject
    constructor(
        private val usageStatsRepository: UsageStatsRepository,
        private val usageHistory: UsageHistory,
        private val launcherAppSource: LauncherAppSource,
    ) : ViewModel() {
        private val state = MutableStateFlow(StatsUiState())
        val uiState: StateFlow<StatsUiState> = state.asStateFlow()

        private var loading: Job? = null

        /**
         * Saves any finished day not stored yet, then reloads: past days from Room, today live. Called on each
         * ON_RESUME, so coming back from another app shows fresh numbers.
         */
        fun refresh() {
            loading?.cancel()
            loading =
                viewModelScope.launch {
                    usageHistory.catchUp(HistoryTrigger.StatsOpened)
                    val today = usageStatsRepository.today()
                    val days = usageStatsRepository.pastDays().filter { it.date < today.date } + today
                    val weeks = buildWeeks(days, today.date)
                    val labels = state.value.labels.toMutableMap()
                    days.flatMap { it.apps }.map { it.packageName }.distinct().forEach { pkg ->
                        if (pkg !in labels) labels[pkg] = launcherAppSource.label(pkg) ?: pkg
                    }
                    state.update {
                        it.copy(
                            loading = false,
                            today = today.date,
                            weeks = weeks,
                            colourSlots = colourSlots(weeks, CHART_SLOTS),
                            labels = labels,
                        )
                    }
                }
        }

        fun onSelectDay(
            weekStart: LocalDate,
            day: LocalDate,
        ) {
            state.update { it.copy(selection = it.selection + (weekStart to day)) }
        }

        suspend fun loadIcon(packageName: String): ImageBitmap? = launcherAppSource.icon(packageName)

        private companion object {
            /** Size of the fixed chart palette (core/designsystem ChartColors.series). */
            const val CHART_SLOTS = 10
        }
    }

package com.aladin.foreground2.feature.stats

import com.aladin.foreground2.core.domain.AppInfo

/** One app's foreground time today. */
data class AppTime(
    val app: AppInfo,
    val millis: Long,
)

data class StatsUiState(
    val loading: Boolean = true,
    val unlocks: Int = 0,
    val totalMillis: Long = 0L,
    /** Every app used today, most used first. */
    val apps: List<AppTime> = emptyList(),
)

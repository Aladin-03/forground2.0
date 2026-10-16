package com.aladin.foreground2.feature.stats

import kotlinx.serialization.Serializable

/** The stats screen's tabs. */
enum class StatsTab { Unlocks, ScreenTime }

/** Opens on [tab], the current week and today. */
@Serializable
data class StatsRoute(
    val tab: StatsTab = StatsTab.ScreenTime,
)

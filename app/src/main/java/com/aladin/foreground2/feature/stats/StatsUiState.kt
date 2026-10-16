package com.aladin.foreground2.feature.stats

import com.aladin.foreground2.core.domain.usage.Week
import java.time.LocalDate

data class StatsUiState(
    val loading: Boolean = true,
    val today: LocalDate? = null,
    /** From the earliest week with data to the current week (last). */
    val weeks: List<Week> = emptyList(),
    /** Chart colour slot per app, the same on every week. Apps without one are drawn as "Other". */
    val colourSlots: Map<String, Int> = emptyMap(),
    /** Launcher labels by package; falls back to the package name. */
    val labels: Map<String, String> = emptyMap(),
    /** The day the user picked, per week (keyed by its Monday). */
    val selection: Map<LocalDate, LocalDate> = emptyMap(),
) {
    /** The picked day, else today on the current week and the last day (Sunday) on past weeks. */
    fun selectedDay(week: Week): LocalDate =
        selection[week.start] ?: today?.takeIf { it in week.dates } ?: week.dates.last()

    fun label(packageName: String): String = labels[packageName] ?: packageName
}

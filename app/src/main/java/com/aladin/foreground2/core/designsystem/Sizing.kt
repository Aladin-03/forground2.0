package com.aladin.foreground2.core.designsystem

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Component sizes. Read via [ForegroundTheme.sizing]. */
@Immutable
data class Sizing(
    val appIcon: Dp = 40.dp,
    /** Fully expanded breathing circle on the breath overlay. */
    val breathCircle: Dp = 240.dp,
    /** App icon on the "Block this app?" prompt. */
    val promptIcon: Dp = 72.dp,
    /** App icon on the lock screen. */
    val lockIcon: Dp = 72.dp,
    /** One row of a number wheel on the duration picker. */
    val wheelItemHeight: Dp = 48.dp,
    /** Width of one number wheel on the duration picker. */
    val wheelWidth: Dp = 80.dp,
    /** Plot height of the weekly charts on the stats screen (columns scale to the week's busiest day). */
    val weekChartHeight: Dp = 180.dp,
    /** Width of one day's column in the weekly charts. */
    val weekBarWidth: Dp = 22.dp,
    /** Room above an unlocks column for its count. */
    val barLabelHeight: Dp = 20.dp,
    /** Height of the 24 hourly unlock bars. */
    val hourChartHeight: Dp = 96.dp,
    /** Colour swatch in the chart legend and per-app list. */
    val legendSwatch: Dp = 12.dp,
    /** Outline around the selected day's column. */
    val selectionBorder: Dp = 2.dp,
    /** App icon in the per-day app list and legend. */
    val smallAppIcon: Dp = 24.dp,
)

internal val LocalSizing = staticCompositionLocalOf { Sizing() }

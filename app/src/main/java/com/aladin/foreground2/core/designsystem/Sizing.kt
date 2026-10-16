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
    /** One row of a number wheel on the duration picker. */
    val wheelItemHeight: Dp = 48.dp,
    /** Width of one number wheel on the duration picker. */
    val wheelWidth: Dp = 80.dp,
)

internal val LocalSizing = staticCompositionLocalOf { Sizing() }

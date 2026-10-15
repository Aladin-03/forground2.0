package com.aladin.foreground2.core.designsystem

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Component sizes. Read via [ForegroundTheme.sizing]. */
@Immutable
data class Sizing(
    val appIcon: Dp = 40.dp,
)

internal val LocalSizing = staticCompositionLocalOf { Sizing() }

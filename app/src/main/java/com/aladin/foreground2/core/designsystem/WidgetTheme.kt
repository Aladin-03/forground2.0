package com.aladin.foreground2.core.designsystem

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.material3.ColorProviders

/** The app's light and dark colour schemes for Glance widgets; use with `GlanceTheme(colors = ...)`. */
val ForegroundWidgetColors = ColorProviders(light = LightColors, dark = DarkColors)

/** Sizes and text sizes for the home-screen widgets (Glance has no MaterialTheme typography). */
object WidgetTokens {
    val padding = 12.dp
    val gap = 4.dp
    val cornerRadius = 16.dp
    val labelText = 13.sp
    val valueText = 28.sp
    val rowText = 13.sp

    /** The smallest layout: label and one number. */
    val small = DpSize(110.dp, 40.dp)

    /** Wide and tall enough for the top-3 app list under the total. */
    val medium = DpSize(180.dp, 120.dp)
}

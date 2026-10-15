package com.aladin.foreground2.core.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable

private val LightColors =
    lightColorScheme(
        primary = Palette.Sage40,
        onPrimary = Palette.Ink99,
        primaryContainer = Palette.Sage90,
        onPrimaryContainer = Palette.Sage10,
        secondary = Palette.Sand40,
        onSecondary = Palette.Ink99,
        secondaryContainer = Palette.Sand90,
        onSecondaryContainer = Palette.Sand10,
        background = Palette.Ink99,
        onBackground = Palette.Ink10,
        surface = Palette.Ink99,
        onSurface = Palette.Ink10,
        surfaceVariant = Palette.Ink95,
        error = Palette.Error40,
    )

private val DarkColors =
    darkColorScheme(
        primary = Palette.Sage80,
        onPrimary = Palette.Sage20,
        primaryContainer = Palette.Sage30,
        onPrimaryContainer = Palette.Sage90,
        secondary = Palette.Sand80,
        onSecondary = Palette.Sand10,
        background = Palette.Ink10,
        onBackground = Palette.Ink90,
        surface = Palette.Ink10,
        onSurface = Palette.Ink90,
        surfaceVariant = Palette.Ink20,
        error = Palette.Error80,
    )

@Composable
fun ForegroundTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalSpacing provides Spacing()) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = ForegroundTypography,
            content = content,
        )
    }
}

/** Access to non-Material tokens, e.g. `ForegroundTheme.spacing.md`. */
object ForegroundTheme {
    val spacing: Spacing
        @Composable
        @ReadOnlyComposable
        get() = LocalSpacing.current
}

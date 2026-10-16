package com.aladin.foreground2.core.designsystem

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Fixed categorical palette for per-app chart segments. An app keeps its slot (index into [series]) on every week;
 * light and dark use the same hues at different tones. [other] is the grey "Other" segment. Read via
 * [ForegroundTheme.chartColors].
 */
@Immutable
data class ChartColors(
    val series: List<Color>,
    val other: Color,
) {
    /** The colour for a slot, or [other] for none. */
    fun forSlot(slot: Int?): Color = slot?.let { series[it % series.size] } ?: other
}

internal val LightChartColors =
    ChartColors(
        series =
            listOf(
                Color(0xFF2E7D6B),
                Color(0xFFC98A1B),
                Color(0xFF3A6EA5),
                Color(0xFFC8553D),
                Color(0xFF7B4B94),
                Color(0xFF7A8B2E),
                Color(0xFFB5487A),
                Color(0xFF2F8FA3),
                Color(0xFF8A5A3C),
                Color(0xFF5560B0),
            ),
        other = Color(0xFFB8BCB8),
    )

internal val DarkChartColors =
    ChartColors(
        series =
            listOf(
                Color(0xFF6FC2AE),
                Color(0xFFF0B652),
                Color(0xFF7FA8DA),
                Color(0xFFF08D78),
                Color(0xFFB58AD0),
                Color(0xFFB5C46A),
                Color(0xFFE58AB3),
                Color(0xFF6CC6D9),
                Color(0xFFC99777),
                Color(0xFF9AA3E8),
            ),
        other = Color(0xFF5C605D),
    )

internal val LocalChartColors = staticCompositionLocalOf { LightChartColors }

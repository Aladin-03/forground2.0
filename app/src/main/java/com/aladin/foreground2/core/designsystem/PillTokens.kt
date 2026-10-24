package com.aladin.foreground2.core.designsystem

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The usage pill shown during an allowed window (rule 10). It is meant to catch the eye: a solid dark chip with a
 * light outline and a shadow, so it reads as a popup over any app, light or dark, and ignores the app theme. Its
 * window is drawn at [WINDOW_ALPHA] on top of this.
 */
object PillTokens {
    val background = Palette.Ink10
    val content = Color.White
    val outline = Color.White.copy(alpha = 0.3f)
    val outlineWidth = 1.dp
    val shape = RoundedCornerShape(percent = 50)
    val elevation = 6.dp

    /** Room around the chip inside its window, so the shadow isn't clipped. */
    val shadowMargin = 6.dp
    val horizontalPadding = 12.dp
    val verticalPadding = 5.dp

    /** Tabular digits, so the pill doesn't change width as the seconds tick. */
    val text =
        TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp,
            lineHeight = 20.sp,
            fontFeatureSettings = "tnum",
        )

    /** Android 12+ blocks touches through untrusted overlays more opaque than 0.8; taps must reach the app. */
    const val WINDOW_ALPHA = 0.8f
}

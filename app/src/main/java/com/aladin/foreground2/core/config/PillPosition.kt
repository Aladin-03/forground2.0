package com.aladin.foreground2.core.config

/**
 * Where the usage pill's window (rule 10) sits, measured from the screen's top-right corner: just below the status
 * bar row, which on this phone is too crowded around the camera to hold it. The window includes a shadow margin
 * around the chip (PillTokens.shadowMargin, 6 dp), so the chip itself is 6 dp further in. Plain dp numbers (Dp is
 * design-system only), tuned by screenshot on the Infinix X6871, whose status bar is 36 dp tall. Fixed, so the pill
 * stays put in portrait, landscape and fullscreen video.
 */
object PillPosition {
    /** Chip's right edge 12 dp from the screen edge. */
    const val END_OFFSET_DP = 6

    /** Chip's top 4 dp below the 36 dp status bar. */
    const val TOP_OFFSET_DP = 34
}

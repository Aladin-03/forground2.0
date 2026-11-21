package com.aladin.foreground2.core.config

import com.aladin.foreground2.core.domain.watch.WatchMode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** Runs under testDebugUnitTest, so it checks the debug BuildConfig values. */
class TimingTest {
    @Test
    fun `debug build uses short timings`() {
        assertEquals(
            Timing(
                breathDurationMillis = 10_000L,
                mindfulWindowMillis = 60_000L,
                focusedWindowMillis = 30_000L,
                strictWindowMillis = 10_000L,
                pollIntervalMillis = 500L,
                permissionCheckIntervalMillis = 5_000L,
                widgetRefreshIntervalMillis = 300_000L,
                widgetMinRefreshIntervalMillis = 60_000L,
                sitThroughMillis = 20_000L,
            ),
            Timing.fromBuildConfig(),
        )
    }

    @Test
    fun `each mode has its own window`() {
        val timing = Timing.fromBuildConfig()
        assertEquals(60_000L, timing.windowMillis(WatchMode.MINDFUL))
        assertEquals(30_000L, timing.windowMillis(WatchMode.FOCUSED))
        assertEquals(10_000L, timing.windowMillis(WatchMode.STRICT))
    }
}

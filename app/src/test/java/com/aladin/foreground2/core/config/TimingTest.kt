package com.aladin.foreground2.core.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** Runs under testDebugUnitTest, so it checks the debug BuildConfig values. */
class TimingTest {
    @Test
    fun `debug build uses short timings`() {
        assertEquals(Timing(10_000L, 60_000L, 500L, 5_000L), Timing.fromBuildConfig())
    }
}

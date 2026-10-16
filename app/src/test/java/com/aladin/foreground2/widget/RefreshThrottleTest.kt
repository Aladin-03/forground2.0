package com.aladin.foreground2.widget

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

private const val MINUTE = 60_000L
private const val DAY = 24 * 60 * MINUTE
private const val TODAY = 100 * DAY

class RefreshThrottleTest {
    private val throttle = RefreshThrottle(MINUTE)

    @Test
    fun `a widget refreshes at most once a minute`() {
        assertTrue(throttle.tryAcquire(1, TODAY + 10 * MINUTE, TODAY))
        assertFalse(throttle.tryAcquire(1, TODAY + 10 * MINUTE + 59_999, TODAY))
        assertEquals(TODAY + 11 * MINUTE, throttle.nextAllowedAt(1))
        assertTrue(throttle.tryAcquire(1, TODAY + 11 * MINUTE, TODAY))
    }

    @Test
    fun `widgets are throttled independently`() {
        assertTrue(throttle.tryAcquire(1, TODAY, TODAY))
        assertTrue(throttle.tryAcquire(2, TODAY + 1, TODAY))
        assertFalse(throttle.tryAcquire(1, TODAY + 2, TODAY))
    }

    @Test
    fun `the first refresh of a new day always goes through`() {
        val beforeMidnight = TODAY + DAY - 30_000
        assertTrue(throttle.tryAcquire(1, beforeMidnight, TODAY))
        assertTrue(throttle.tryAcquire(1, TODAY + DAY + 10_000, TODAY + DAY))
        assertFalse(throttle.tryAcquire(1, TODAY + DAY + 20_000, TODAY + DAY))
    }

    @Test
    fun `a removed widget is forgotten`() {
        assertTrue(throttle.tryAcquire(1, TODAY, TODAY))
        throttle.retain(emptySet())
        assertTrue(throttle.tryAcquire(1, TODAY + 1, TODAY))
    }
}

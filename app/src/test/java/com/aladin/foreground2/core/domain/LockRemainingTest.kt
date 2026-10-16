package com.aladin.foreground2.core.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

private const val MINUTE = 60
private const val HOUR = 60 * MINUTE
private const val DAY = 24 * HOUR

class LockRemainingTest {
    @Test
    fun `zero or less has ended`() {
        assertEquals(LockRemaining.Ended, LockRemaining.of(0))
        assertEquals(LockRemaining.Ended, LockRemaining.of(-5))
    }

    @Test
    fun `under an hour shows minutes and seconds`() {
        assertEquals(LockRemaining.Minutes(0, 1), LockRemaining.of(1))
        assertEquals(LockRemaining.Minutes(4, 5), LockRemaining.of(4 * MINUTE + 5))
        assertEquals(LockRemaining.Minutes(59, 59), LockRemaining.of(HOUR - 1))
    }

    @Test
    fun `an hour to a day shows hours and minutes, rounding minutes up`() {
        assertEquals(LockRemaining.Hours(1, 0), LockRemaining.of(HOUR))
        assertEquals(LockRemaining.Hours(1, 1), LockRemaining.of(HOUR + 1))
        assertEquals(LockRemaining.Hours(3, 4), LockRemaining.of(3 * HOUR + 4 * MINUTE))
        assertEquals(LockRemaining.Hours(23, 59), LockRemaining.of(DAY - MINUTE))
    }

    @Test
    fun `a day or more shows days, hours and minutes`() {
        assertEquals(LockRemaining.Days(1, 0, 0), LockRemaining.of(DAY - 1))
        assertEquals(LockRemaining.Days(1, 0, 0), LockRemaining.of(DAY))
        assertEquals(LockRemaining.Days(2, 3, 4), LockRemaining.of(2 * DAY + 3 * HOUR + 4 * MINUTE))
        assertEquals(LockRemaining.Days(30, 23, 59), LockRemaining.of(30 * DAY + 23 * HOUR + 59 * MINUTE))
    }
}

package com.aladin.foreground2.core.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class LockDurationTest {
    @Test
    fun `zero is zero and nothing else is`() {
        assertTrue(LockDuration.Zero.isZero)
        assertFalse(LockDuration(0, 0, 1).isZero)
        assertFalse(LockDuration(1, 0, 0).isZero)
    }

    @Test
    fun `millis adds up days, hours and minutes`() {
        assertEquals(60_000L, LockDuration(0, 0, 1).millis)
        assertEquals(3_600_000L, LockDuration(0, 1, 0).millis)
        assertEquals(86_400_000L, LockDuration(1, 0, 0).millis)
        assertEquals(((30L * 24 + 23) * 60 + 59) * 60_000L, LockDuration(30, 23, 59).millis)
    }

    @Test
    fun `values outside the wheels are rejected`() {
        assertThrows<IllegalArgumentException> { LockDuration(31, 0, 0) }
        assertThrows<IllegalArgumentException> { LockDuration(0, 24, 0) }
        assertThrows<IllegalArgumentException> { LockDuration(0, 0, 60) }
        assertThrows<IllegalArgumentException> { LockDuration(0, 0, -1) }
    }
}

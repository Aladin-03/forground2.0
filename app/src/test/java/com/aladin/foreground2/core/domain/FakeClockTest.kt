package com.aladin.foreground2.core.domain

import app.cash.turbine.test
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class FakeClockTest {
    @Test
    fun `fake clock advances and is observable through a flow`() =
        runTest {
            val clock = FakeClock(now = 1_000L)
            val readings =
                flow {
                    emit(clock.nowMillis())
                    clock.advanceBy(500L)
                    emit(clock.nowMillis())
                }

            readings.test {
                assertEquals(1_000L, awaitItem())
                assertEquals(1_500L, awaitItem())
                awaitComplete()
            }
        }
}

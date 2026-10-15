package com.aladin.foreground2.service.overlay

import com.aladin.foreground2.core.domain.Clock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

private const val START = 1_700_000_000_000L

@OptIn(ExperimentalCoroutinesApi::class)
class BreathCountdownTest {
    /** Wall clock that moves with the test's virtual time. */
    private fun TestScope.clock() = Clock { START + currentTime }

    @Test
    fun `counts whole seconds down to zero on each second boundary`() =
        runTest {
            val countdown = BreathCountdown(endsAt = START + 3_000, clock = clock())
            val emitted = countdown.secondsLeft().map { it to currentTime }.toList()
            assertEquals(listOf(3 to 0L, 2 to 1_000L, 1 to 2_000L, 0 to 3_000L), emitted)
        }

    @Test
    fun `rounds a partial second up and lands exactly on the end time`() =
        runTest {
            val countdown = BreathCountdown(endsAt = START + 2_500, clock = clock())
            assertEquals(3, countdown.secondsLeftNow())
            val emitted = countdown.secondsLeft().map { it to currentTime }.toList()
            assertEquals(listOf(3 to 0L, 2 to 500L, 1 to 1_500L, 0 to 2_500L), emitted)
        }

    @Test
    fun `an already elapsed breath emits zero and completes`() =
        runTest {
            val countdown = BreathCountdown(endsAt = START - 1_000, clock = clock())
            assertEquals(listOf(0), countdown.secondsLeft().toList())
        }

    @Test
    fun `re-reads the clock so a clock jump shortens the wait`() =
        runTest {
            var offset = 0L
            val countdown = BreathCountdown(endsAt = START + 10_000, clock = { START + currentTime + offset })
            val emitted =
                countdown
                    .secondsLeft()
                    .map { seconds ->
                        if (seconds == 9) offset = 5_000
                        seconds
                    }.toList()
            assertEquals(listOf(10, 9, 3, 2, 1, 0), emitted)
        }
}

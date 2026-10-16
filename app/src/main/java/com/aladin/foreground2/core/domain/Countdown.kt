package com.aladin.foreground2.core.domain

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Counts down to [endsAt] (epoch ms) on [clock], for the breath and the lock screen. Remaining time is re-read from
 * the clock on every step rather than accumulated from delays, so the countdown can't drift from what the gate machine
 * will accept as a full breath or an ended lock.
 */
class Countdown(
    private val endsAt: Long,
    private val clock: Clock,
) {
    fun secondsLeftNow(): Int = remainingMillis().toWholeSecondsUp()

    /** Whole seconds left, rounded up. Emits now and at each second boundary; completes right after emitting 0. */
    fun secondsLeft(): Flow<Int> =
        flow {
            while (true) {
                val remaining = remainingMillis()
                emit(remaining.toWholeSecondsUp())
                if (remaining == 0L) break
                delay((remaining - 1) % MILLIS_PER_SECOND + 1)
            }
        }

    private fun remainingMillis(): Long = (endsAt - clock.nowMillis()).coerceAtLeast(0L)

    private fun Long.toWholeSecondsUp(): Int = ((this + MILLIS_PER_SECOND - 1) / MILLIS_PER_SECOND).toInt()

    private companion object {
        const val MILLIS_PER_SECOND = 1_000L
    }
}

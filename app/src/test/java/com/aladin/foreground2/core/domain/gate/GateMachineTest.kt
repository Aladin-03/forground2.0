package com.aladin.foreground2.core.domain.gate

import com.aladin.foreground2.core.config.Timing
import com.aladin.foreground2.core.domain.FakeClock
import com.aladin.foreground2.core.domain.gate.GateEffect.DeleteLock
import com.aladin.foreground2.core.domain.gate.GateEffect.HideOverlay
import com.aladin.foreground2.core.domain.gate.GateEffect.PersistLock
import com.aladin.foreground2.core.domain.gate.GateEffect.ShowBreath
import com.aladin.foreground2.core.domain.gate.GateEffect.ShowDurationPicker
import com.aladin.foreground2.core.domain.gate.GateEffect.ShowLockScreen
import com.aladin.foreground2.core.domain.gate.GateEffect.ShowPrompt
import com.aladin.foreground2.core.domain.gate.GateState.Allowed
import com.aladin.foreground2.core.domain.gate.GateState.Breathing
import com.aladin.foreground2.core.domain.gate.GateState.Idle
import com.aladin.foreground2.core.domain.gate.GateState.Locked
import com.aladin.foreground2.core.domain.gate.GateState.PickingDuration
import com.aladin.foreground2.core.domain.gate.GateState.Prompting
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

/** Debug timings (breath 10 s, window 1 min), matching the debug BuildConfig. */
private val DEBUG =
    Timing(
        breathDurationMillis = 10_000L,
        allowedWindowMillis = 60_000L,
        pollIntervalMillis = 500L,
        permissionCheckIntervalMillis = 5_000L,
        widgetRefreshIntervalMillis = 300_000L,
        widgetMinRefreshIntervalMillis = 60_000L,
    )

/** Release timings (breath 60 s, window 10 min). */
private val RELEASE =
    Timing(
        breathDurationMillis = 60_000L,
        allowedWindowMillis = 600_000L,
        pollIntervalMillis = 500L,
        permissionCheckIntervalMillis = 5_000L,
        widgetRefreshIntervalMillis = 300_000L,
        widgetMinRefreshIntervalMillis = 60_000L,
    )

private const val MINUTE = 60_000L
private const val DAY = 24 * 60 * MINUTE
private const val START = 1_700_000_000_000L

/** Drives one gate the way the service would: events stamped with a [FakeClock], effects recorded per step. */
private class GateDriver(
    val timing: Timing = DEBUG,
    initial: GateState = Idle,
) {
    val clock = FakeClock(now = START)
    var state: GateState = initial
        private set

    fun send(event: GateEvent): List<GateEffect> {
        val transition = GateMachine.reduce(state, event, timing)
        state = transition.state
        return transition.effects
    }

    fun now() = clock.now

    fun advance(millis: Long) = clock.advanceBy(millis)

    fun enter() = send(GateEvent.Entered(now()))

    fun leave() = send(GateEvent.Left(now()))

    fun tick() = send(GateEvent.Tick(now()))

    fun breathFinished() = send(GateEvent.BreathFinished(now()))

    fun answerNo() = send(GateEvent.AnsweredNo(now()))

    fun answerYes() = send(GateEvent.AnsweredYes)

    fun lockNow() = send(GateEvent.LockNowRequested(now()))

    fun cancelPicker() = send(GateEvent.PickerCancelled(now()))

    fun startLock(durationMillis: Long) = send(GateEvent.LockStarted(now() + durationMillis))

    fun lockAdded(until: Long) = send(GateEvent.LockAdded(until))

    /** Enter, sit through the full breath, and land on the prompt. */
    fun enterAndBreathe() {
        enter()
        advance(timing.breathDurationMillis)
        breathFinished()
    }

    /** Polls once per poll interval for [millis], returning the effects of every tick. */
    fun tickFor(millis: Long): List<GateEffect> {
        val effects = mutableListOf<GateEffect>()
        val end = now() + millis
        while (now() < end) {
            advance(timing.pollIntervalMillis)
            effects += tick()
        }
        return effects
    }
}

class GateMachineTest {
    @Nested
    inner class `Rule 6 - a lock started from Home` {
        @Test
        fun `locks an idle app without showing anything, and the next entry shows the lock screen`() {
            val gate = GateDriver()
            val until = START + DAY
            assertEquals(emptyList<GateEffect>(), gate.lockAdded(until))
            assertEquals(Locked(until), gate.state)
            assertEquals(listOf(ShowLockScreen(until)), gate.enter())
        }

        @Test
        fun `wins over an allowed window`() {
            val gate = GateDriver()
            gate.enterAndBreathe()
            gate.answerNo()
            val until = gate.now() + DAY
            assertEquals(emptyList<GateEffect>(), gate.lockAdded(until))
            assertEquals(Locked(until), gate.state)
            assertEquals(emptyList<GateEffect>(), gate.tickFor(DEBUG.allowedWindowMillis))
            gate.leave()
            assertEquals(listOf(ShowLockScreen(until)), gate.enter())
        }

        @Test
        fun `replaces an overlay that is up with the lock screen`() {
            val gate = GateDriver()
            gate.enter()
            val until = START + DAY
            assertEquals(listOf(ShowLockScreen(until)), gate.lockAdded(until))
            assertEquals(Locked(until), gate.state)
        }

        @Test
        fun `extends a shorter lock but never shortens one`() {
            val gate = GateDriver(initial = Locked(START + DAY))
            assertEquals(emptyList<GateEffect>(), gate.lockAdded(START + MINUTE))
            assertEquals(Locked(START + DAY), gate.state)
            gate.lockAdded(START + 2 * DAY)
            assertEquals(Locked(START + 2 * DAY), gate.state)
        }
    }

    @Nested
    inner class `Rule 2 - entry is a breath` {
        @Test
        fun `entering from idle starts a breath at now`() {
            val gate = GateDriver()
            assertEquals(listOf(ShowBreath), gate.enter())
            assertEquals(Breathing(START), gate.state)
        }

        @Test
        fun `switching away and back gives a fresh breath`() {
            val gate = GateDriver()
            gate.enterAndBreathe()
            gate.answerNo()
            gate.advance(MINUTE / 4)

            assertEquals(emptyList<GateEffect>(), gate.leave()) // e.g. to WhatsApp
            assertEquals(Idle, gate.state)
            gate.advance(5_000L)

            assertEquals(listOf(ShowBreath), gate.enter())
            assertEquals(Breathing(gate.now()), gate.state)
        }

        @Test
        fun `switching away mid-prompt and back gives a fresh breath`() {
            val gate = GateDriver()
            gate.enterAndBreathe()
            assertEquals(listOf(HideOverlay), gate.leave())
            gate.advance(1_000L)

            assertEquals(listOf(ShowBreath), gate.enter())
            assertEquals(Breathing(gate.now()), gate.state)
        }

        @Test
        fun `a repeated entry restarts the breath`() {
            val gate = GateDriver()
            gate.enter()
            gate.advance(3_000L)
            assertEquals(listOf(ShowBreath), gate.enter())
            assertEquals(Breathing(gate.now()), gate.state)
        }

        @Test
        fun `entering from every non-locked state starts a breath`() {
            listOf(Idle, Breathing(START - 1), Prompting, PickingDuration(), Allowed(START + MINUTE)).forEach { from ->
                val gate = GateDriver(initial = from)
                assertEquals(listOf(ShowBreath), gate.enter(), "from $from")
                assertEquals(Breathing(START), gate.state, "from $from")
            }
        }

        @Test
        fun `ticks during a breath change nothing`() {
            val gate = GateDriver()
            gate.enter()
            assertEquals(emptyList<GateEffect>(), gate.tickFor(DEBUG.breathDurationMillis * 2))
            assertEquals(Breathing(START), gate.state)
        }
    }

    @Nested
    inner class `Rule 3 - escaping does not skip` {
        @Test
        fun `Home mid-breath hides the overlay and goes idle`() {
            val gate = GateDriver()
            gate.enter()
            gate.advance(DEBUG.breathDurationMillis - 1_000L)
            assertEquals(listOf(HideOverlay), gate.leave())
            assertEquals(Idle, gate.state)
        }

        @Test
        fun `Home mid-breath then re-entry gives a full breath`() {
            val gate = GateDriver()
            gate.enter()
            gate.advance(DEBUG.breathDurationMillis - 1_000L)
            gate.leave()
            gate.advance(500L)

            gate.enter()
            val reEntry = gate.now()
            assertEquals(Breathing(reEntry), gate.state)

            // The time already breathed before Home doesn't count.
            gate.advance(1_000L)
            assertEquals(emptyList<GateEffect>(), gate.breathFinished())
            assertEquals(Breathing(reEntry), gate.state)

            gate.advance(DEBUG.breathDurationMillis - 1_000L)
            assertEquals(listOf(ShowPrompt), gate.breathFinished())
            assertEquals(Prompting, gate.state)
        }

        @Test
        fun `a stale breath timer after leaving is ignored`() {
            val gate = GateDriver()
            gate.enter()
            gate.leave()
            gate.advance(DEBUG.breathDurationMillis)
            assertEquals(emptyList<GateEffect>(), gate.breathFinished())
            assertEquals(Idle, gate.state)
        }

        @Test
        fun `leaving the duration picker hides it and goes idle`() {
            val gate = GateDriver()
            gate.enterAndBreathe()
            gate.answerYes()
            assertEquals(listOf(HideOverlay), gate.leave())
            assertEquals(Idle, gate.state)
        }

        @Test
        fun `leaving while idle stays idle and only hides a leftover overlay`() {
            // Idle in front only happens under a lock screen whose lock ended (rule 8); the host ignores it otherwise.
            val gate = GateDriver()
            assertEquals(listOf(HideOverlay), gate.leave())
            assertEquals(Idle, gate.state)
        }
    }

    @Nested
    inner class `Rule 4 - prompt after the breath` {
        @Test
        fun `breath finishing on time shows the prompt`() {
            val gate = GateDriver()
            gate.enter()
            gate.advance(DEBUG.breathDurationMillis)
            assertEquals(listOf(ShowPrompt), gate.breathFinished())
            assertEquals(Prompting, gate.state)
        }

        @Test
        fun `breath finishing early is ignored`() {
            val gate = GateDriver()
            gate.enter()
            gate.advance(DEBUG.breathDurationMillis - 1)
            assertEquals(emptyList<GateEffect>(), gate.breathFinished())
            assertEquals(Breathing(START), gate.state)
        }

        @Test
        fun `release breath is a full minute`() {
            val gate = GateDriver(timing = RELEASE)
            gate.enter()
            gate.advance(DEBUG.breathDurationMillis)
            assertEquals(emptyList<GateEffect>(), gate.breathFinished())
            gate.advance(RELEASE.breathDurationMillis - DEBUG.breathDurationMillis)
            assertEquals(listOf(ShowPrompt), gate.breathFinished())
        }

        @Test
        fun `answers outside the prompt are ignored`() {
            listOf(Idle, Breathing(START), PickingDuration(), Allowed(START + MINUTE), Locked(START + DAY)).forEach {
                val gate = GateDriver(initial = it)
                assertEquals(emptyList<GateEffect>(), gate.answerNo(), "No from $it")
                assertEquals(emptyList<GateEffect>(), gate.answerYes(), "Yes from $it")
                assertEquals(it, gate.state)
            }
        }
    }

    @Nested
    inner class `Rule 5 - No gives an allowed window` {
        @ParameterizedTest
        @MethodSource("com.aladin.foreground2.core.domain.gate.GateMachineTest#timings")
        fun `No allows the app until now plus the window`(timing: Timing) {
            val gate = GateDriver(timing)
            gate.enterAndBreathe()
            val answeredAt = gate.now()
            assertEquals(listOf(HideOverlay), gate.answerNo())
            assertEquals(Allowed(answeredAt + timing.allowedWindowMillis), gate.state)
        }

        @ParameterizedTest
        @MethodSource("com.aladin.foreground2.core.domain.gate.GateMachineTest#timings")
        fun `allowed window expiring while in-app breathes on the spot then prompts again`(timing: Timing) {
            val gate = GateDriver(timing)
            gate.enterAndBreathe()
            gate.answerNo()
            val until = (gate.state as Allowed).until

            // Usable for the whole window: no effects before it ends.
            assertEquals(emptyList<GateEffect>(), gate.tickFor(timing.allowedWindowMillis - timing.pollIntervalMillis))
            assertEquals(Allowed(until), gate.state)

            gate.advance(timing.pollIntervalMillis)
            assertEquals(until, gate.now())
            assertEquals(listOf(ShowBreath), gate.tick())
            assertEquals(Breathing(until), gate.state)

            gate.advance(timing.breathDurationMillis)
            assertEquals(listOf(ShowPrompt), gate.breathFinished())
            assertEquals(Prompting, gate.state)
        }

        @Test
        fun `tick exactly at the window end breathes, one ms before does not`() {
            val until = START + MINUTE
            assertEquals(
                Transition(Allowed(until)),
                GateMachine.reduce(Allowed(until), GateEvent.Tick(until - 1), DEBUG),
            )
            assertEquals(
                Transition(Breathing(until), listOf(ShowBreath)),
                GateMachine.reduce(Allowed(until), GateEvent.Tick(until), DEBUG),
            )
        }

        @Test
        fun `leaving during the window ends the sitting`() {
            val gate = GateDriver()
            gate.enterAndBreathe()
            gate.answerNo()
            gate.advance(10_000L)
            assertEquals(emptyList<GateEffect>(), gate.leave())
            assertEquals(Idle, gate.state)

            // Coming back within what would have been the window still breathes.
            gate.advance(10_000L)
            assertEquals(listOf(ShowBreath), gate.enter())
        }

        @Test
        fun `answering No again after an in-app expiry opens a new window`() {
            val gate = GateDriver()
            gate.enterAndBreathe()
            gate.answerNo()
            gate.advance(DEBUG.allowedWindowMillis)
            gate.tick()
            gate.advance(DEBUG.breathDurationMillis)
            gate.breathFinished()
            gate.answerNo()
            assertEquals(Allowed(gate.now() + DEBUG.allowedWindowMillis), gate.state)
        }
    }

    @Nested
    inner class `Rule 6 - Yes picks a lock duration` {
        @Test
        fun `Yes shows the duration picker`() {
            val gate = GateDriver()
            gate.enterAndBreathe()
            assertEquals(listOf(ShowDurationPicker), gate.answerYes())
            assertEquals(PickingDuration(), gate.state)
        }

        @Test
        fun `starting a lock persists it and shows the lock screen`() {
            val gate = GateDriver()
            gate.enterAndBreathe()
            gate.answerYes()
            val until = gate.now() + 2 * DAY + 3 * 60 * MINUTE + 4 * MINUTE
            assertEquals(listOf(PersistLock(until), ShowLockScreen(until)), gate.send(GateEvent.LockStarted(until)))
            assertEquals(Locked(until), gate.state)
        }

        @Test
        fun `LockStarted outside the picker is ignored`() {
            listOf(Idle, Breathing(START), Prompting, Allowed(START + MINUTE), Locked(START + DAY)).forEach {
                val gate = GateDriver(initial = it)
                assertEquals(emptyList<GateEffect>(), gate.startLock(MINUTE), "from $it")
                assertEquals(it, gate.state)
            }
        }

        @Test
        fun `a lock cannot be cancelled or shortened`() {
            val until = START + DAY
            val gate = GateDriver(initial = Locked(until))
            gate.enter()
            gate.answerNo()
            gate.answerYes()
            gate.startLock(MINUTE)
            gate.tick()
            gate.leave()
            assertEquals(Locked(until), gate.state)
        }
    }

    @Nested
    inner class `Lock it now - shortcut from the breath` {
        @Test
        fun `lock-now mid-breath opens the picker keeping the breath left`() {
            val gate = GateDriver()
            gate.enter()
            gate.advance(3_000L)
            assertEquals(listOf(ShowDurationPicker), gate.lockNow())
            assertEquals(PickingDuration(fromBreath = true, breathRemainingMs = 7_000L), gate.state)
        }

        @Test
        fun `lock-now then Start creates the lock`() {
            val gate = GateDriver()
            gate.enter()
            gate.advance(2_000L)
            gate.lockNow()
            gate.advance(5_000L)
            val until = gate.now() + 3 * 60 * MINUTE
            assertEquals(listOf(PersistLock(until), ShowLockScreen(until)), gate.send(GateEvent.LockStarted(until)))
            assertEquals(Locked(until), gate.state)
        }

        @Test
        fun `lock-now then cancel resumes the breath with the same remaining time`() {
            val gate = GateDriver()
            gate.enter()
            gate.advance(4_000L)
            gate.lockNow()
            gate.advance(3_000L) // Picker open: the breath is paused.

            assertEquals(listOf(ShowBreath), gate.cancelPicker())
            val resumedAt = gate.now()
            assertEquals(Breathing(startedAt = resumedAt - 4_000L), gate.state)

            // Still the 6 s that were left at lock-now, not 3 s.
            gate.advance(6_000L - 1)
            assertEquals(emptyList<GateEffect>(), gate.breathFinished())
            gate.advance(1)
            assertEquals(listOf(ShowPrompt), gate.breathFinished())
        }

        @Test
        fun `lock-now, wait longer than the breath, then cancel still returns to the breath`() {
            val gate = GateDriver()
            gate.enter()
            gate.advance(1_000L)
            gate.lockNow()
            gate.advance(DEBUG.breathDurationMillis * 5)

            // A breath timer that fired meanwhile doesn't count: the gate is in the picker.
            assertEquals(emptyList<GateEffect>(), gate.breathFinished())
            assertEquals(emptyList<GateEffect>(), gate.tick())

            assertEquals(listOf(ShowBreath), gate.cancelPicker())
            assertEquals(Breathing(startedAt = gate.now() - 1_000L), gate.state)
            assertEquals(emptyList<GateEffect>(), gate.breathFinished())
        }

        @Test
        fun `Home during the early picker returns to Idle and the next entry gets a full breath`() {
            val gate = GateDriver()
            gate.enter()
            gate.advance(8_000L)
            gate.lockNow()
            assertEquals(listOf(HideOverlay), gate.leave())
            assertEquals(Idle, gate.state)

            gate.advance(1_000L)
            assertEquals(listOf(ShowBreath), gate.enter())
            assertEquals(Breathing(gate.now()), gate.state)
        }

        @Test
        fun `lock-now outside the breath is ignored`() {
            listOf(Idle, Prompting, PickingDuration(), Allowed(START + MINUTE), Locked(START + DAY)).forEach {
                val gate = GateDriver(initial = it)
                assertEquals(emptyList<GateEffect>(), gate.lockNow(), "from $it")
                assertEquals(it, gate.state)
            }
        }

        @Test
        fun `the Yes-path picker cannot be cancelled`() {
            val gate = GateDriver()
            gate.enterAndBreathe()
            gate.answerYes()
            assertEquals(emptyList<GateEffect>(), gate.cancelPicker())
            assertEquals(PickingDuration(), gate.state)
        }

        @Test
        fun `cancel outside the picker is ignored`() {
            listOf(Idle, Breathing(START), Prompting, Allowed(START + MINUTE), Locked(START + DAY)).forEach {
                val gate = GateDriver(initial = it)
                assertEquals(emptyList<GateEffect>(), gate.cancelPicker(), "from $it")
                assertEquals(it, gate.state)
            }
        }
    }

    @Nested
    inner class `Rule 7 - locked app` {
        @Test
        fun `entering an active lock skips the breath and shows the lock screen`() {
            val until = START + DAY
            val gate = GateDriver(initial = Locked(until))
            assertEquals(listOf(ShowLockScreen(until)), gate.enter())
            assertEquals(Locked(until), gate.state)
        }

        @Test
        fun `going home from the lock screen hides it and keeps the lock`() {
            val until = START + DAY
            val gate = GateDriver(initial = Locked(until))
            gate.enter()
            assertEquals(listOf(HideOverlay), gate.leave())
            assertEquals(Locked(until), gate.state)
        }

        @Test
        fun `ticks while an active lock is in front change nothing`() {
            val gate = GateDriver(initial = Locked(START + MINUTE))
            gate.enter()
            assertEquals(emptyList<GateEffect>(), gate.tickFor(MINUTE - DEBUG.pollIntervalMillis))
            assertEquals(Locked(START + MINUTE), gate.state)
        }

        @Test
        fun `a lock in the far future stays locked`() {
            val until = START + 30 * DAY + 23 * 60 * MINUTE + 59 * MINUTE
            val gate = GateDriver(initial = Locked(until))
            gate.advance(30 * DAY)
            assertEquals(listOf(ShowLockScreen(until)), gate.enter())
            assertEquals(emptyList<GateEffect>(), gate.tick())
            gate.leave()
            gate.advance(until - gate.now() - 1)
            assertEquals(listOf(ShowLockScreen(until)), gate.enter())
            assertEquals(Locked(until), gate.state)
        }

        @Test
        fun `a lock ending near Long MAX does not overflow`() {
            val until = Long.MAX_VALUE
            val gate = GateDriver(initial = Locked(until))
            assertEquals(listOf(ShowLockScreen(until)), gate.enter())
            assertEquals(emptyList<GateEffect>(), gate.tick())
        }
    }

    @Nested
    inner class `Rule 8 - lock expiry` {
        @Test
        fun `entering after the lock expired deletes it and gives a breath`() {
            val until = START + MINUTE
            val gate = GateDriver(initial = Locked(until))
            gate.advance(MINUTE)
            assertEquals(listOf(DeleteLock, ShowBreath), gate.enter())
            assertEquals(Breathing(until), gate.state)
        }

        @Test
        fun `entering one ms before expiry still shows the lock screen`() {
            val until = START + MINUTE
            val gate = GateDriver(initial = Locked(until))
            gate.advance(MINUTE - 1)
            assertEquals(listOf(ShowLockScreen(until)), gate.enter())
        }

        @Test
        fun `leaving an expired lock deletes it, hides the lock screen and goes idle`() {
            val gate = GateDriver(initial = Locked(START + MINUTE))
            gate.enter()
            gate.advance(MINUTE)
            assertEquals(listOf(DeleteLock, HideOverlay), gate.leave())
            assertEquals(Idle, gate.state)
        }

        @Test
        fun `a lock ending under its lock screen deletes it and goes idle without a breath`() {
            val until = START + MINUTE
            val gate = GateDriver(initial = Locked(until))
            gate.enter()
            gate.advance(MINUTE)
            assertEquals(listOf(DeleteLock), gate.tick())
            assertEquals(Idle, gate.state)
            assertEquals(emptyList<GateEffect>(), gate.tickFor(10 * MINUTE))
            assertEquals(Idle, gate.state)
        }

        @Test
        fun `after a lock ends under its lock screen, leaving hides it and the next entry breathes`() {
            val until = START + MINUTE
            val gate = GateDriver(initial = Locked(until))
            gate.enter()
            gate.advance(MINUTE)
            gate.tick()
            assertEquals(listOf(HideOverlay), gate.leave())
            assertEquals(listOf(ShowBreath), gate.enter())
            assertEquals(Breathing(gate.now()), gate.state)
        }

        @Test
        fun `a lock ending while its app is elsewhere is deleted on the next tick`() {
            val gate = GateDriver(initial = Locked(START + MINUTE))
            gate.advance(MINUTE)
            assertEquals(listOf(DeleteLock), gate.tick())
            assertEquals(Idle, gate.state)
        }

        @Test
        fun `full cycle - breathe, lock, expire, breathe again`() {
            val gate = GateDriver()
            gate.enterAndBreathe()
            gate.answerYes()
            gate.startLock(5 * MINUTE)
            gate.leave()
            gate.advance(MINUTE)
            assertEquals(listOf(ShowLockScreen(START + DEBUG.breathDurationMillis + 5 * MINUTE)), gate.enter())
            gate.leave()
            gate.advance(4 * MINUTE)
            assertEquals(listOf(DeleteLock, ShowBreath), gate.enter())
        }
    }

    @Nested
    inner class `Rule 9 - persistence` {
        @Test
        fun `only starting a lock persists anything`() {
            val gate = GateDriver()
            val effects = mutableListOf<GateEffect>()
            effects += gate.enterAndBreatheEffects()
            effects += gate.answerNo()
            effects += gate.tickFor(DEBUG.allowedWindowMillis)
            effects += gate.leave()
            assertEquals(emptyList<GateEffect>(), effects.filterIsInstance<PersistLock>())
        }

        @Test
        fun `a lock restored from storage behaves like a fresh one`() {
            // After a reboot the service rebuilds the state from the stored end time.
            val until = START + 3 * DAY
            val gate = GateDriver(initial = Locked(until))
            assertEquals(listOf(ShowLockScreen(until)), gate.enter())
        }

        private fun GateDriver.enterAndBreatheEffects(): List<GateEffect> {
            val effects = enter()
            advance(timing.breathDurationMillis)
            return effects + breathFinished()
        }
    }

    companion object {
        @JvmStatic
        fun timings() = listOf(DEBUG, RELEASE)
    }
}

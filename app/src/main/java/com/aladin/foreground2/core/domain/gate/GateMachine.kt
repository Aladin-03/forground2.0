package com.aladin.foreground2.core.domain.gate

import com.aladin.foreground2.core.config.Timing
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

/**
 * The gate state machine for one watched app (docs/SPEC.md "Behaviour rules"). Pure: no clock, no I/O. Events
 * that don't apply to the current state (e.g. a stale BreathFinished after the user left) are ignored.
 */
object GateMachine {
    fun reduce(
        state: GateState,
        event: GateEvent,
        timing: Timing,
    ): Transition =
        when (event) {
            is GateEvent.Entered -> onEntered(state, event.now)
            is GateEvent.Left -> onLeft(state, event.now)
            is GateEvent.BreathFinished -> onBreathFinished(state, event.now, timing)
            is GateEvent.AnsweredNo -> onAnsweredNo(state, event.now, timing)
            GateEvent.AnsweredYes -> onAnsweredYes(state)
            is GateEvent.LockStarted -> onLockStarted(state, event.until)
            is GateEvent.Tick -> onTick(state, event.now)
        }

    /** Rule 2 / 7: every entry is a fresh breath, unless an active lock skips straight to the lock screen. */
    private fun onEntered(
        state: GateState,
        now: Long,
    ): Transition =
        if (state is Locked && state.isActive(now)) {
            Transition(state, listOf(ShowLockScreen(state.until)))
        } else {
            startBreath(now)
        }

    /** Rule 3 / 5: leaving ends the sitting. An active lock stays; anything else goes back to Idle. */
    private fun onLeft(
        state: GateState,
        now: Long,
    ): Transition =
        when (state) {
            Idle -> Transition(state)
            is Allowed -> Transition(Idle)
            is Breathing, Prompting, PickingDuration -> Transition(Idle, listOf(HideOverlay))
            is Locked -> Transition(if (state.isActive(now)) state else Idle, listOf(HideOverlay))
        }

    /** Rule 4: prompt only once the full breath has elapsed. */
    private fun onBreathFinished(
        state: GateState,
        now: Long,
        timing: Timing,
    ): Transition =
        if (state is Breathing && now - state.startedAt >= timing.breathDurationMillis) {
            Transition(Prompting, listOf(ShowPrompt))
        } else {
            Transition(state)
        }

    /** Rule 5. */
    private fun onAnsweredNo(
        state: GateState,
        now: Long,
        timing: Timing,
    ): Transition =
        if (state == Prompting) {
            Transition(Allowed(until = now + timing.allowedWindowMillis), listOf(HideOverlay))
        } else {
            Transition(state)
        }

    /** Rule 6. */
    private fun onAnsweredYes(state: GateState): Transition =
        if (state == Prompting) Transition(PickingDuration, listOf(ShowDurationPicker)) else Transition(state)

    /** Rule 6 / 9: the lock is persisted and the app (still in front) shows the lock screen. */
    private fun onLockStarted(
        state: GateState,
        until: Long,
    ): Transition =
        if (state == PickingDuration) {
            Transition(Locked(until), listOf(PersistLock(until), ShowLockScreen(until)))
        } else {
            Transition(state)
        }

    /**
     * Rule 5: an allowed window ending while in front breathes on the spot. Rule 8: a lock expiring while its lock
     * screen is in front does the same, so the app never becomes usable without a breath.
     */
    private fun onTick(
        state: GateState,
        now: Long,
    ): Transition =
        when {
            state is Allowed && now >= state.until -> startBreath(now)
            state is Locked && !state.isActive(now) -> startBreath(now)
            else -> Transition(state)
        }

    private fun startBreath(now: Long) = Transition(Breathing(startedAt = now), listOf(ShowBreath))

    private fun Locked.isActive(now: Long): Boolean = now < until
}

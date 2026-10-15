package com.aladin.foreground2.core.domain.gate

/** Where one watched app's gate is. Times are epoch ms. */
sealed interface GateState {
    /** Not in front, or in front with nothing pending. The next entry starts a breath. */
    data object Idle : GateState

    /** Breath overlay is showing; started at [startedAt]. */
    data class Breathing(
        val startedAt: Long,
    ) : GateState

    /** "Block this app?" prompt is showing. */
    data object Prompting : GateState

    /** Lock duration picker is showing. */
    data object PickingDuration : GateState

    /** App is usable without an overlay until [until]. In memory only. */
    data class Allowed(
        val until: Long,
    ) : GateState

    /** App is locked until [until]. Once `now >= until` the lock has expired and the state acts like [Idle]. */
    data class Locked(
        val until: Long,
    ) : GateState
}

/**
 * Inputs to the gate. The service sends [Entered]/[Left] when the app becomes / stops being the front app, and
 * [Tick] on each poll only while the app is in front. The overlay sends [BreathFinished] and the answers.
 */
sealed interface GateEvent {
    data class Entered(
        val now: Long,
    ) : GateEvent

    data class Left(
        val now: Long,
    ) : GateEvent

    data class BreathFinished(
        val now: Long,
    ) : GateEvent

    data class AnsweredNo(
        val now: Long,
    ) : GateEvent

    data object AnsweredYes : GateEvent

    data class LockStarted(
        val until: Long,
    ) : GateEvent

    data class Tick(
        val now: Long,
    ) : GateEvent
}

/** Side effects the caller (service) must carry out after a transition, in order. */
sealed interface GateEffect {
    data object ShowBreath : GateEffect

    data object ShowPrompt : GateEffect

    data object ShowDurationPicker : GateEffect

    data class ShowLockScreen(
        val until: Long,
    ) : GateEffect

    data object HideOverlay : GateEffect

    data class PersistLock(
        val until: Long,
    ) : GateEffect
}

/** Result of [GateMachine.reduce]. Same state and no effects means the event was ignored. */
data class Transition(
    val state: GateState,
    val effects: List<GateEffect> = emptyList(),
)

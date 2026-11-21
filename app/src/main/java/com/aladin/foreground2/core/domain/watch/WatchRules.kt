package com.aladin.foreground2.core.domain.watch

/** Something the user asked to change about one app's watching. */
sealed interface WatchRequest {
    /** Start watching an unwatched app in [mode]. */
    data class Enable(
        val mode: WatchMode,
    ) : WatchRequest

    /** Move a watched app to [mode]. */
    data class ChangeMode(
        val mode: WatchMode,
    ) : WatchRequest

    /** Stop watching the app. */
    data object Disable : WatchRequest
}

/** What [WatchRules.decide] allows for a [WatchRequest]. */
sealed interface WatchDecision {
    /** Apply it now: enabling, or tightening towards Strict. */
    data object ApplyNow : WatchDecision

    /** Loosening or disabling: only once a sit-through countdown for this change has completed. */
    data class NeedsCountdown(
        val change: PendingChange,
    ) : WatchDecision

    /** Loosening or disabling while the app has an active lock: refused. */
    data object BlockedByLock : WatchDecision

    /** Nothing would change (same mode, already watched, or not watched). */
    data object NoChange : WatchDecision
}

/** A loosen or disable that waits for its sit-through countdown. */
sealed interface PendingChange {
    val packageName: String

    data class Loosen(
        override val packageName: String,
        val from: WatchMode,
        val to: WatchMode,
    ) : PendingChange

    data class Disable(
        override val packageName: String,
        val mode: WatchMode,
    ) : PendingChange
}

/**
 * Rules for changing how an app is watched (docs/SPEC.md "Watch modes"). Making watching stricter is instant;
 * making it looser (a looser mode, or not watching at all) needs a completed sit-through countdown, and is refused
 * while the app has an active lock. Pure: the caller supplies the current mode and whether the app is locked.
 */
object WatchRules {
    /**
     * [current] is the app's mode, or null if it isn't watched. [locked] is whether it has an active lock right now.
     */
    fun decide(
        packageName: String,
        current: WatchMode?,
        request: WatchRequest,
        locked: Boolean,
    ): WatchDecision =
        when (request) {
            is WatchRequest.Enable -> if (current == null) WatchDecision.ApplyNow else WatchDecision.NoChange
            is WatchRequest.ChangeMode ->
                when {
                    current == null || current == request.mode -> WatchDecision.NoChange
                    request.mode.isStricterThan(current) -> WatchDecision.ApplyNow
                    locked -> WatchDecision.BlockedByLock
                    else -> WatchDecision.NeedsCountdown(PendingChange.Loosen(packageName, current, request.mode))
                }
            WatchRequest.Disable ->
                when {
                    current == null -> WatchDecision.NoChange
                    locked -> WatchDecision.BlockedByLock
                    else -> WatchDecision.NeedsCountdown(PendingChange.Disable(packageName, current))
                }
        }

    /**
     * Whether a [change] whose countdown just completed may still be applied, given the app's state now: it must
     * still be watched in the mode the countdown started from, and not have been locked meanwhile.
     */
    fun canApply(
        change: PendingChange,
        current: WatchMode?,
        locked: Boolean,
    ): Boolean {
        val from =
            when (change) {
                is PendingChange.Loosen -> change.from
                is PendingChange.Disable -> change.mode
            }
        return !locked && current == from
    }
}

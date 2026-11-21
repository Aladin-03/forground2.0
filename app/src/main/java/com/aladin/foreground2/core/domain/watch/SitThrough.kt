package com.aladin.foreground2.core.domain.watch

/**
 * The sit-through countdown in front of a loosen or disable. It only moves forward while the user stays on its
 * screen: leaving for any reason cancels it, nothing changes, and the next attempt starts from the full duration.
 * There is no way to shorten it. Times are epoch ms.
 */
sealed interface SitThrough {
    val change: PendingChange

    data class Running(
        override val change: PendingChange,
        val endsAt: Long,
    ) : SitThrough

    /** The full time passed on screen: [change] may now be applied (see [WatchRules.canApply]). */
    data class Completed(
        override val change: PendingChange,
    ) : SitThrough

    /** The user left or tapped Cancel: nothing changes. */
    data class Cancelled(
        override val change: PendingChange,
    ) : SitThrough

    companion object {
        fun start(
            change: PendingChange,
            now: Long,
            durationMillis: Long,
        ): Running = Running(change, endsAt = now + durationMillis)
    }
}

/** Completes a running countdown once [now] reaches its end. Completed and cancelled ones stay as they are. */
fun SitThrough.tick(now: Long): SitThrough =
    if (this is SitThrough.Running && now >= endsAt) SitThrough.Completed(change) else this

/** Leaving the screen (or Cancel) cancels a running countdown. A completed one has already been applied. */
fun SitThrough.leave(): SitThrough = if (this is SitThrough.Running) SitThrough.Cancelled(change) else this

package com.aladin.foreground2.service.detection

import com.aladin.foreground2.core.config.Timing
import com.aladin.foreground2.core.data.repo.LockRepository
import com.aladin.foreground2.core.domain.AppInfo
import com.aladin.foreground2.core.domain.Clock
import com.aladin.foreground2.core.domain.gate.GateEffect
import com.aladin.foreground2.core.domain.gate.GateEvent
import com.aladin.foreground2.core.domain.gate.GateMachine
import com.aladin.foreground2.core.domain.gate.GateState
import com.aladin.foreground2.core.logging.Log
import javax.inject.Inject
import javax.inject.Singleton

/** Everything that drives the gates, fed to [GateCoordinator.handle] one at a time. */
sealed interface GateInput {
    data class WatchedChanged(
        val apps: List<AppInfo>,
    ) : GateInput

    data class FrontChanged(
        val change: FrontAppChanged,
    ) : GateInput

    /** One poll interval passed while the screen is on. */
    data class Tick(
        val at: Long,
    ) : GateInput

    /** The breath overlay for [packageName] finished its countdown at [at]. */
    data class BreathFinished(
        val packageName: String,
        val at: Long,
    ) : GateInput

    /** The user tapped No on [packageName]'s prompt at [at]. */
    data class AnsweredNo(
        val packageName: String,
        val at: Long,
    ) : GateInput

    /** The user tapped Yes on [packageName]'s prompt. */
    data class AnsweredYes(
        val packageName: String,
    ) : GateInput

    /** The user tapped Start on [packageName]'s duration picker, locking it until [until]. */
    data class LockStarted(
        val packageName: String,
        val until: Long,
    ) : GateInput
}

/**
 * One gate moved: [packageName] (shown to the user as [label]) went [from] -> [to], and the caller must carry out
 * [effects].
 */
data class GateUpdate(
    val packageName: String,
    val from: GateState,
    val to: GateState,
    val effects: List<GateEffect>,
    val label: String,
)

/**
 * Holds every watched app's gate state in memory and turns front-app changes into Entered/Left, poll ticks into
 * Tick for the watched app in front (and for any app whose lock has ended), and overlay events into the matching gate
 * events. Locks are the exception to "in memory": [loadLocks] reads the active ones from [LockRepository] when the
 * service starts, and PersistLock / DeleteLock effects are written back before [handle] returns.
 * Not thread-safe: call [loadLocks] and [handle] from one coroutine.
 */
@Singleton
class GateCoordinator
    @Inject
    constructor(
        private val timing: Timing,
        private val clock: Clock,
        private val locks: LockRepository,
    ) {
        private var watched: Map<String, AppInfo> = emptyMap()
        private val states = mutableMapOf<String, GateState>()
        private var front: String? = null

        /** Unlock time per package, mirroring the repository. Survives an app being unwatched and rewatched. */
        private val lockedUntil = mutableMapOf<String, Long>()

        /** An app with no gate state yet (just loaded, or rewatched) starts Locked if it has a lock, else Idle. */
        fun stateOf(packageName: String): GateState =
            states[packageName] ?: lockedUntil[packageName]?.let(GateState::Locked) ?: GateState.Idle

        /**
         * Loads the active locks so a restarted service keeps enforcing them (rule 9), deleting any that ended while
         * it wasn't running. Call before [handle].
         */
        suspend fun loadLocks() {
            locks.deleteEnded()
            val active = locks.activeLocks()
            lockedUntil.putAll(active)
            Log.i(Log.Area.Gate) { "loaded ${active.size} active lock(s): $active" }
        }

        suspend fun handle(input: GateInput): List<GateUpdate> =
            when (input) {
                is GateInput.WatchedChanged -> onWatchedChanged(input.apps)
                is GateInput.FrontChanged -> onFrontChanged(input.change)
                is GateInput.Tick -> onTick(input.at)
                is GateInput.BreathFinished -> sendIfWatched(input.packageName, GateEvent.BreathFinished(input.at))
                is GateInput.AnsweredNo -> sendIfWatched(input.packageName, GateEvent.AnsweredNo(input.at))
                is GateInput.AnsweredYes -> sendIfWatched(input.packageName, GateEvent.AnsweredYes)
                is GateInput.LockStarted -> sendIfWatched(input.packageName, GateEvent.LockStarted(input.until))
            }

        /** Overlay events can arrive after the app was unwatched; those are dropped. */
        private suspend fun sendIfWatched(
            packageName: String,
            event: GateEvent,
        ): List<GateUpdate> = listOfNotNull(packageName.takeIf { it in watched }?.let { send(it, event) })

        private suspend fun onFrontChanged(change: FrontAppChanged): List<GateUpdate> {
            val previous = front
            front = change.packageName
            return listOfNotNull(
                previous?.takeIf { it in watched }?.let { send(it, GateEvent.Left(change.at)) },
                change.packageName.takeIf { it in watched }?.let { send(it, GateEvent.Entered(change.at)) },
            )
        }

        /**
         * Ticks the watched app in front, and every app whose lock has ended so its row is deleted promptly (rule 8)
         * even if it isn't in front. Ended locks of unwatched apps have no gate; they are just deleted.
         */
        private suspend fun onTick(at: Long): List<GateUpdate> {
            val ended = lockedUntil.filterValues { it <= at }.keys
            val endedUnwatched = ended - watched.keys
            if (endedUnwatched.isNotEmpty()) {
                lockedUntil.keys -= endedUnwatched
                locks.deleteEnded()
            }
            return (listOfNotNull(front) + ended)
                .distinct()
                .filter { it in watched }
                .mapNotNull { send(it, GateEvent.Tick(at)) }
        }

        /** Unwatching an app mid-gate acts like leaving it (hides any overlay), then forgets it. */
        private suspend fun onWatchedChanged(apps: List<AppInfo>): List<GateUpdate> {
            val next = apps.associateBy { it.packageName }
            val removed = watched.keys - next.keys
            val updates = removed.mapNotNull { send(it, GateEvent.Left(clock.nowMillis())) }
            removed.forEach { states.remove(it) }
            if (watched.keys != next.keys) {
                Log.i(Log.Area.Gate) { "watching ${next.values.map { it.label }}" }
            }
            watched = next
            return updates
        }

        private suspend fun send(
            packageName: String,
            event: GateEvent,
        ): GateUpdate? {
            val from = stateOf(packageName)
            var transition = GateMachine.reduce(from, event, timing)
            if (transition.state == from && transition.effects.isEmpty()) return null
            transition.effects.filterIsInstance<GateEffect.PersistLock>().singleOrNull()?.let { persist ->
                val stored = locks.lock(packageName, persist.until)
                lockedUntil[packageName] = stored
                // A lock can't be shortened: if a later one already existed, lock until that instead.
                if (stored !=
                    persist.until
                ) {
                    transition = GateMachine.reduce(from, GateEvent.LockStarted(stored), timing)
                }
            }
            if (GateEffect.DeleteLock in transition.effects) {
                lockedUntil.remove(packageName)
                locks.deleteEnded()
            }
            states[packageName] = transition.state
            val label = watched[packageName]?.label ?: packageName
            Log.i(Log.Area.Gate) {
                "$label ${from.name} -> ${transition.state.name}, " +
                    "effects=${transition.effects}"
            }
            return GateUpdate(packageName, from, transition.state, transition.effects, label)
        }

        private val GateState.name: String get() = this::class.simpleName.orEmpty()
    }

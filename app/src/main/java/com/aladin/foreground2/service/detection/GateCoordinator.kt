package com.aladin.foreground2.service.detection

import com.aladin.foreground2.core.config.Timing
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
}

/** One gate moved: [packageName] went [from] -> [to], and the caller must carry out [effects]. */
data class GateUpdate(
    val packageName: String,
    val from: GateState,
    val to: GateState,
    val effects: List<GateEffect>,
)

/**
 * Holds every watched app's gate state in memory and turns front-app changes into Entered/Left, poll ticks into
 * Tick for the watched app in front, and overlay events into the matching gate events. Not thread-safe: call
 * [handle] from one coroutine.
 */
@Singleton
class GateCoordinator
    @Inject
    constructor(
        private val timing: Timing,
        private val clock: Clock,
    ) {
        private var watched: Map<String, AppInfo> = emptyMap()
        private val states = mutableMapOf<String, GateState>()
        private var front: String? = null

        fun stateOf(packageName: String): GateState = states[packageName] ?: GateState.Idle

        fun handle(input: GateInput): List<GateUpdate> =
            when (input) {
                is GateInput.WatchedChanged -> onWatchedChanged(input.apps)
                is GateInput.FrontChanged -> onFrontChanged(input.change)
                is GateInput.Tick ->
                    listOfNotNull(
                        front?.takeIf { it in watched }?.let { send(it, GateEvent.Tick(input.at)) },
                    )
                is GateInput.BreathFinished ->
                    listOfNotNull(
                        input.packageName
                            .takeIf { it in watched }
                            ?.let { send(it, GateEvent.BreathFinished(input.at)) },
                    )
            }

        private fun onFrontChanged(change: FrontAppChanged): List<GateUpdate> {
            val previous = front
            front = change.packageName
            return listOfNotNull(
                previous?.takeIf { it in watched }?.let { send(it, GateEvent.Left(change.at)) },
                change.packageName.takeIf { it in watched }?.let { send(it, GateEvent.Entered(change.at)) },
            )
        }

        /** Unwatching an app mid-gate acts like leaving it (hides any overlay), then forgets it. */
        private fun onWatchedChanged(apps: List<AppInfo>): List<GateUpdate> {
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

        private fun send(
            packageName: String,
            event: GateEvent,
        ): GateUpdate? {
            val from = stateOf(packageName)
            val transition = GateMachine.reduce(from, event, timing)
            if (transition.state == from && transition.effects.isEmpty()) return null
            states[packageName] = transition.state
            Log.i(Log.Area.Gate) {
                "${watched[packageName]?.label ?: packageName} ${from.name} -> ${transition.state.name}, " +
                    "effects=${transition.effects}"
            }
            return GateUpdate(packageName, from, transition.state, transition.effects)
        }

        private val GateState.name: String get() = this::class.simpleName.orEmpty()
    }

package com.aladin.foreground2.service.overlay

import com.aladin.foreground2.core.config.Timing
import com.aladin.foreground2.core.domain.Clock
import com.aladin.foreground2.core.domain.gate.GateEffect
import com.aladin.foreground2.core.domain.gate.GateState
import com.aladin.foreground2.core.logging.Log
import com.aladin.foreground2.service.detection.GateInput
import com.aladin.foreground2.service.detection.GateUpdate
import com.aladin.foreground2.service.overlay.ui.BreathScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Carries out the overlay effects of each [GateUpdate] on [OverlayHost], and turns what the user does on an overlay
 * back into [inputs] for the coordinator. Remembers which app the overlay belongs to, so a HideOverlay for some other
 * app (e.g. an unwatched app that wasn't in front) leaves it alone.
 */
@Singleton
class OverlayEffects
    @Inject
    constructor(
        private val host: OverlayHost,
        private val clock: Clock,
        private val timing: Timing,
    ) {
        private val events = Channel<GateInput>(Channel.UNLIMITED)
        private var shownFor: String? = null

        /** Overlay events (e.g. the breath finishing), to be fed to the coordinator with everything else. */
        val inputs: Flow<GateInput> = events.receiveAsFlow()

        suspend fun apply(update: GateUpdate) =
            withContext(Dispatchers.Main.immediate) {
                update.effects.forEach { apply(update, it) }
            }

        /** Removes whatever is showing (the service is stopping). Main thread. */
        fun hideAll() {
            shownFor = null
            host.hide()
        }

        private fun apply(
            update: GateUpdate,
            effect: GateEffect,
        ) {
            val pkg = update.packageName
            when (effect) {
                GateEffect.ShowBreath -> showBreath(pkg, update.to)
                GateEffect.HideOverlay -> hide(pkg)
                // The prompt arrives in the next slice; until then the breath just ends and the app is usable.
                GateEffect.ShowPrompt -> hide(pkg)
                GateEffect.ShowDurationPicker, is GateEffect.ShowLockScreen, is GateEffect.PersistLock ->
                    Log.w(Log.Area.Overlay) { "$effect for $pkg not implemented yet" }
            }
        }

        private fun showBreath(
            pkg: String,
            state: GateState,
        ) {
            val startedAt =
                (state as? GateState.Breathing)?.startedAt ?: clock.nowMillis().also {
                    Log.w(Log.Area.Overlay) { "ShowBreath for $pkg in $state, starting the breath now" }
                }
            val endsAt = startedAt + timing.breathDurationMillis
            shownFor = pkg
            host.show("breath $pkg") {
                BreathScreen(
                    endsAt = endsAt,
                    clock = clock,
                    onFinished = { now -> events.trySend(GateInput.BreathFinished(pkg, now)) },
                )
            }
        }

        private fun hide(pkg: String) {
            if (shownFor != pkg) return
            shownFor = null
            host.hide()
        }
    }

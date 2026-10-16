package com.aladin.foreground2.service.overlay

import com.aladin.foreground2.core.config.Timing
import com.aladin.foreground2.core.data.apps.LauncherAppSource
import com.aladin.foreground2.core.domain.Clock
import com.aladin.foreground2.core.domain.gate.GateEffect
import com.aladin.foreground2.core.domain.gate.GateState
import com.aladin.foreground2.core.logging.Log
import com.aladin.foreground2.service.detection.GateInput
import com.aladin.foreground2.service.detection.GateUpdate
import com.aladin.foreground2.service.overlay.ui.BreathScreen
import com.aladin.foreground2.service.overlay.ui.DurationPickerScreen
import com.aladin.foreground2.service.overlay.ui.LockedPlaceholderScreen
import com.aladin.foreground2.service.overlay.ui.PromptScreen
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
        private val launcherApps: LauncherAppSource,
    ) {
        private val events = Channel<GateInput>(Channel.UNLIMITED)
        private var shownFor: String? = null

        /**
         * Overlay events (breath finished, prompt answered, lock started), to be fed to the coordinator with
         * everything else.
         */
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
                GateEffect.ShowPrompt -> showPrompt(pkg, update.label)
                GateEffect.ShowDurationPicker -> showDurationPicker(pkg, update.label)
                is GateEffect.ShowLockScreen -> showLockedPlaceholder(pkg, update.label, effect.until)
                // Written by the coordinator before the update reaches here.
                is GateEffect.PersistLock -> Unit
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

        /** Taps are answered by the gate (HideOverlay or ShowDurationPicker); a repeated tap is ignored there. */
        private fun showPrompt(
            pkg: String,
            label: String,
        ) {
            shownFor = pkg
            host.show("prompt $pkg") {
                PromptScreen(
                    label = label,
                    loadIcon = { launcherApps.icon(pkg) },
                    onYes = { events.trySend(GateInput.AnsweredYes(pkg)) },
                    onNo = { events.trySend(GateInput.AnsweredNo(pkg, clock.nowMillis())) },
                )
            }
        }

        /** Start locks until now + the picked duration; a repeated tap is ignored by the gate (already Locked). */
        private fun showDurationPicker(
            pkg: String,
            label: String,
        ) {
            shownFor = pkg
            host.show("duration picker $pkg") {
                DurationPickerScreen(
                    label = label,
                    clock = clock,
                    onStart = { duration ->
                        events.trySend(GateInput.LockStarted(pkg, clock.nowMillis() + duration.millis))
                    },
                )
            }
        }

        /** Placeholder until the rule 7 lock screen exists. */
        private fun showLockedPlaceholder(
            pkg: String,
            label: String,
            until: Long,
        ) {
            shownFor = pkg
            host.show("locked placeholder $pkg until=$until") {
                LockedPlaceholderScreen(label = label, until = until)
            }
        }

        private fun hide(pkg: String) {
            if (shownFor != pkg) return
            shownFor = null
            host.hide()
        }
    }

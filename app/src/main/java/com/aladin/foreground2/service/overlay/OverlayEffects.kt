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
import com.aladin.foreground2.service.overlay.ui.LockScreen
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
 * app (e.g. an unwatched app that wasn't in front) leaves it alone. Also shows the [UsagePill] while a gate is
 * Allowed (rule 10).
 */
@Singleton
class OverlayEffects
    @Inject
    constructor(
        private val host: OverlayHost,
        private val pill: UsagePill,
        private val clock: Clock,
        private val timing: Timing,
        private val launcherApps: LauncherAppSource,
        private val home: HomeLauncher,
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
                updatePill(update)
                update.effects.forEach { apply(update, it) }
            }

        /** Removes whatever is showing (the service is stopping). Main thread. */
        fun hideAll() {
            shownFor = null
            host.hide()
            pill.hideAll()
        }

        /**
         * Rule 10: the pill is up exactly while the gate is Allowed, which only the app in front can be. Every way
         * out (leaving, the window ending into a breath, a lock from Home, being unwatched) leaves Allowed, so it
         * never shares the screen with the breath, prompt, picker or lock screen.
         */
        private fun updatePill(update: GateUpdate) {
            val to = update.to
            when {
                to is GateState.Allowed -> pill.show(update.packageName, to.until)
                update.from is GateState.Allowed -> pill.hide(update.packageName)
            }
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
                GateEffect.ShowDurationPicker -> showDurationPicker(pkg, update.label, update.to)
                is GateEffect.ShowLockScreen -> showLockScreen(pkg, update.label, effect.until)
                // Written by the coordinator before the update reaches here.
                is GateEffect.PersistLock, GateEffect.DeleteLock -> Unit
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
                    onLockNow = { events.trySend(GateInput.LockNowRequested(pkg, clock.nowMillis())) },
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

        /**
         * Start locks until now + the picked duration; a repeated tap is ignored by the gate (already Locked). Opened
         * from the breath, it also has Back, which resumes the breath.
         */
        private fun showDurationPicker(
            pkg: String,
            label: String,
            state: GateState,
        ) {
            val fromBreath = (state as? GateState.PickingDuration)?.fromBreath == true
            shownFor = pkg
            host.show("duration picker $pkg" + if (fromBreath) " (from breath)" else "") {
                DurationPickerScreen(
                    label = label,
                    clock = clock,
                    onStart = { duration ->
                        events.trySend(GateInput.LockStarted(pkg, clock.nowMillis() + duration.millis))
                    },
                    onBack =
                        if (fromBreath) {
                            { events.trySend(GateInput.PickerCancelled(pkg, clock.nowMillis())) }
                        } else {
                            null
                        },
                )
            }
        }

        /** Rule 7. Go home is the only way out: it fires the HOME intent and hides the lock screen. */
        private fun showLockScreen(
            pkg: String,
            label: String,
            until: Long,
        ) {
            shownFor = pkg
            host.show("lock screen $pkg until=$until") {
                LockScreen(
                    label = label,
                    until = until,
                    clock = clock,
                    loadIcon = { launcherApps.icon(pkg) },
                    onGoHome = { goHome(pkg) },
                )
            }
        }

        /** Main thread (a button tap). The Left that follows finds the overlay already gone. */
        internal fun goHome(pkg: String) {
            Log.i(Log.Area.Overlay) { "go home from lock screen $pkg" }
            home.goHome()
            hide(pkg)
        }

        private fun hide(pkg: String) {
            if (shownFor != pkg) return
            shownFor = null
            host.hide()
        }
    }

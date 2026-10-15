package com.aladin.foreground2.service.overlay

import androidx.compose.runtime.Composable
import com.aladin.foreground2.core.config.Timing
import com.aladin.foreground2.core.domain.FakeClock
import com.aladin.foreground2.core.domain.gate.GateEffect
import com.aladin.foreground2.core.domain.gate.GateState
import com.aladin.foreground2.service.detection.GateUpdate
import com.aladin.foreground2.testing.MainDispatcherExtension
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

private const val YOUTUBE = "com.google.android.youtube"
private const val INSTAGRAM = "com.instagram.android"
private const val START = 1_700_000_000_000L
private val TIMING = Timing(breathDurationMillis = 10_000L, allowedWindowMillis = 60_000L, pollIntervalMillis = 500L)

class OverlayEffectsTest {
    @JvmField
    @RegisterExtension
    val main = MainDispatcherExtension()

    private class FakeHost : OverlayHost {
        val calls = mutableListOf<String>()

        override fun show(
            name: String,
            content: @Composable () -> Unit,
        ) {
            calls += "show $name"
        }

        override fun hide() {
            calls += "hide"
        }
    }

    private val host = FakeHost()
    private val effects = OverlayEffects(host, FakeClock(START), TIMING)

    private fun breath(pkg: String) =
        GateUpdate(pkg, GateState.Idle, GateState.Breathing(START), listOf(GateEffect.ShowBreath))

    private fun update(
        pkg: String,
        vararg effect: GateEffect,
    ) = GateUpdate(pkg, GateState.Breathing(START), GateState.Idle, effect.toList())

    @Test
    fun `show breath then hide`() =
        runTest {
            effects.apply(breath(YOUTUBE))
            effects.apply(update(YOUTUBE, GateEffect.HideOverlay))
            assertEquals(listOf("show breath $YOUTUBE", "hide"), host.calls)
        }

    @Test
    fun `the prompt just hides the breath for now`() =
        runTest {
            effects.apply(breath(YOUTUBE))
            effects.apply(update(YOUTUBE, GateEffect.ShowPrompt))
            assertEquals(listOf("show breath $YOUTUBE", "hide"), host.calls)
        }

    @Test
    fun `hiding another app's overlay leaves the current one`() =
        runTest {
            effects.apply(breath(YOUTUBE))
            effects.apply(update(INSTAGRAM, GateEffect.HideOverlay))
            assertEquals(listOf("show breath $YOUTUBE"), host.calls)
        }

    @Test
    fun `switching apps hides one breath and shows the next`() =
        runTest {
            effects.apply(breath(YOUTUBE))
            effects.apply(update(YOUTUBE, GateEffect.HideOverlay))
            effects.apply(breath(INSTAGRAM))
            assertEquals(listOf("show breath $YOUTUBE", "hide", "show breath $INSTAGRAM"), host.calls)
        }
}

package com.aladin.foreground2.service.overlay

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap
import com.aladin.foreground2.core.config.Timing
import com.aladin.foreground2.core.data.apps.LauncherAppSource
import com.aladin.foreground2.core.domain.AppInfo
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

    private object NoIcons : LauncherAppSource {
        override suspend fun launcherApps(): List<AppInfo> = emptyList()

        override suspend fun icon(packageName: String): ImageBitmap? = null
    }

    private val host = FakeHost()
    private val effects = OverlayEffects(host, FakeClock(START), TIMING, NoIcons)

    private fun breath(pkg: String) =
        GateUpdate(pkg, GateState.Idle, GateState.Breathing(START), listOf(GateEffect.ShowBreath), pkg)

    private fun update(
        pkg: String,
        vararg effect: GateEffect,
    ) = GateUpdate(pkg, GateState.Breathing(START), GateState.Idle, effect.toList(), pkg)

    @Test
    fun `show breath then hide`() =
        runTest {
            effects.apply(breath(YOUTUBE))
            effects.apply(update(YOUTUBE, GateEffect.HideOverlay))
            assertEquals(listOf("show breath $YOUTUBE", "hide"), host.calls)
        }

    @Test
    fun `the prompt replaces the breath and No's HideOverlay removes it`() =
        runTest {
            effects.apply(breath(YOUTUBE))
            effects.apply(update(YOUTUBE, GateEffect.ShowPrompt))
            effects.apply(update(YOUTUBE, GateEffect.HideOverlay))
            assertEquals(listOf("show breath $YOUTUBE", "show prompt $YOUTUBE", "hide"), host.calls)
        }

    @Test
    fun `Yes replaces the prompt with the duration picker, which leaving removes`() =
        runTest {
            effects.apply(update(YOUTUBE, GateEffect.ShowPrompt))
            effects.apply(update(YOUTUBE, GateEffect.ShowDurationPicker))
            effects.apply(update(YOUTUBE, GateEffect.HideOverlay))
            assertEquals(
                listOf("show prompt $YOUTUBE", "show duration picker $YOUTUBE", "hide"),
                host.calls,
            )
        }

    @Test
    fun `Start's lock replaces the picker with the lock screen and PersistLock shows nothing`() =
        runTest {
            val until = START + 60_000L
            effects.apply(update(YOUTUBE, GateEffect.ShowDurationPicker))
            effects.apply(update(YOUTUBE, GateEffect.PersistLock(until), GateEffect.ShowLockScreen(until)))
            assertEquals(
                listOf("show duration picker $YOUTUBE", "show locked placeholder $YOUTUBE until=$until"),
                host.calls,
            )
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

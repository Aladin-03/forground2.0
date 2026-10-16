package com.aladin.foreground2.service.detection

import com.aladin.foreground2.core.config.Timing
import com.aladin.foreground2.core.domain.AppInfo
import com.aladin.foreground2.core.domain.FakeClock
import com.aladin.foreground2.core.domain.gate.GateEffect.HideOverlay
import com.aladin.foreground2.core.domain.gate.GateEffect.ShowBreath
import com.aladin.foreground2.core.domain.gate.GateEffect.ShowDurationPicker
import com.aladin.foreground2.core.domain.gate.GateEffect.ShowPrompt
import com.aladin.foreground2.core.domain.gate.GateState.Allowed
import com.aladin.foreground2.core.domain.gate.GateState.Breathing
import com.aladin.foreground2.core.domain.gate.GateState.Idle
import com.aladin.foreground2.core.domain.gate.GateState.PickingDuration
import com.aladin.foreground2.core.domain.gate.GateState.Prompting
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

private const val YOUTUBE = "com.google.android.youtube"
private const val INSTAGRAM = "com.instagram.android"
private const val LAUNCHER = "com.transsion.hilauncher"
private const val WHATSAPP = "com.whatsapp"
private const val START = 1_700_000_000_000L
private val TIMING = Timing(breathDurationMillis = 10_000L, allowedWindowMillis = 60_000L, pollIntervalMillis = 500L)

class GateCoordinatorTest {
    private val clock = FakeClock(now = START)
    private val coordinator = GateCoordinator(TIMING, clock)

    @BeforeEach
    fun watch() {
        coordinator.handle(
            GateInput.WatchedChanged(listOf(AppInfo(YOUTUBE, "YouTube"), AppInfo(INSTAGRAM, "Instagram"))),
        )
    }

    private fun front(
        pkg: String,
        at: Long = clock.now,
    ) = coordinator.handle(GateInput.FrontChanged(FrontAppChanged(pkg, at)))

    private fun tick() = coordinator.handle(GateInput.Tick(clock.now))

    @Test
    fun `entering a watched app starts a breath`() {
        val updates = front(YOUTUBE)
        assertEquals(listOf(GateUpdate(YOUTUBE, Idle, Breathing(START), listOf(ShowBreath), "YouTube")), updates)
        assertEquals(Breathing(START), coordinator.stateOf(YOUTUBE))
    }

    @Test
    fun `leaving for the launcher ends the breath`() {
        front(YOUTUBE)
        clock.advanceBy(3_000)
        val updates = front(LAUNCHER)
        assertEquals(listOf(GateUpdate(YOUTUBE, Breathing(START), Idle, listOf(HideOverlay), "YouTube")), updates)
    }

    @Test
    fun `unwatched apps are never touched`() {
        assertTrue(front(WHATSAPP).isEmpty())
        assertTrue(front(LAUNCHER).isEmpty())
        assertTrue(tick().isEmpty())
    }

    @Test
    fun `switching between two watched apps leaves one and enters the other`() {
        front(YOUTUBE)
        clock.advanceBy(1_000)
        val updates = front(INSTAGRAM)
        assertEquals(
            listOf(YOUTUBE to Idle, INSTAGRAM to Breathing(START + 1_000)),
            updates.map {
                it.packageName to
                    it.to
            },
        )
    }

    @Test
    fun `coming back from another app is a fresh entry`() {
        front(YOUTUBE)
        clock.advanceBy(4_000)
        front(WHATSAPP)
        clock.advanceBy(1_000)
        front(YOUTUBE)
        assertEquals(Breathing(START + 5_000), coordinator.stateOf(YOUTUBE))
    }

    @Test
    fun `ticks reach only the watched app in front and are silent when nothing changes`() {
        front(YOUTUBE)
        clock.advanceBy(500)
        assertTrue(tick().isEmpty())
        assertEquals(Breathing(START), coordinator.stateOf(YOUTUBE))
    }

    @Test
    fun `unwatching the app mid-breath hides the overlay and forgets it`() {
        front(YOUTUBE)
        val updates = coordinator.handle(GateInput.WatchedChanged(listOf(AppInfo(INSTAGRAM, "Instagram"))))
        assertEquals(listOf(GateUpdate(YOUTUBE, Breathing(START), Idle, listOf(HideOverlay), "YouTube")), updates)
        assertEquals(Idle, coordinator.stateOf(YOUTUBE))
        assertTrue(front(LAUNCHER).isEmpty())
    }

    @Test
    fun `a finished breath moves the app to the prompt`() {
        front(YOUTUBE)
        clock.advanceBy(TIMING.breathDurationMillis)
        val updates = coordinator.handle(GateInput.BreathFinished(YOUTUBE, clock.now))
        assertEquals(listOf(GateUpdate(YOUTUBE, Breathing(START), Prompting, listOf(ShowPrompt), "YouTube")), updates)
    }

    @Test
    fun `a breath finishing after the user left and came back is ignored`() {
        front(YOUTUBE)
        clock.advanceBy(3_000)
        front(LAUNCHER)
        front(YOUTUBE)
        clock.advanceBy(TIMING.breathDurationMillis - 3_000)
        assertTrue(coordinator.handle(GateInput.BreathFinished(YOUTUBE, clock.now)).isEmpty())
        assertEquals(Breathing(START + 3_000), coordinator.stateOf(YOUTUBE))
    }

    @Test
    fun `a breath finishing for an unwatched app is ignored`() {
        assertTrue(coordinator.handle(GateInput.BreathFinished(WHATSAPP, clock.now)).isEmpty())
    }

    /** Enters [pkg] and lets the breath finish, leaving it on the prompt. */
    private fun prompt(pkg: String) {
        front(pkg)
        clock.advanceBy(TIMING.breathDurationMillis)
        coordinator.handle(GateInput.BreathFinished(pkg, clock.now))
    }

    @Test
    fun `No hides the overlay and allows the app for the window`() {
        prompt(YOUTUBE)
        val updates = coordinator.handle(GateInput.AnsweredNo(YOUTUBE, clock.now))
        val until = clock.now + TIMING.allowedWindowMillis
        assertEquals(listOf(GateUpdate(YOUTUBE, Prompting, Allowed(until), listOf(HideOverlay), "YouTube")), updates)
    }

    @Test
    fun `an allowed window ending while in front breathes on the spot`() {
        prompt(YOUTUBE)
        coordinator.handle(GateInput.AnsweredNo(YOUTUBE, clock.now))
        val until = clock.now + TIMING.allowedWindowMillis
        clock.advanceBy(TIMING.allowedWindowMillis - 1)
        assertTrue(tick().isEmpty())
        clock.advanceBy(1)
        assertEquals(
            listOf(GateUpdate(YOUTUBE, Allowed(until), Breathing(until), listOf(ShowBreath), "YouTube")),
            tick(),
        )
    }

    @Test
    fun `leaving during the allowed window ends it and the next entry breathes`() {
        prompt(YOUTUBE)
        coordinator.handle(GateInput.AnsweredNo(YOUTUBE, clock.now))
        clock.advanceBy(5_000)
        front(LAUNCHER)
        assertEquals(Idle, coordinator.stateOf(YOUTUBE))
        val updates = front(YOUTUBE)
        assertEquals(listOf(GateUpdate(YOUTUBE, Idle, Breathing(clock.now), listOf(ShowBreath), "YouTube")), updates)
    }

    @Test
    fun `Yes moves the app to the duration picker and leaving returns it to Idle`() {
        prompt(YOUTUBE)
        val updates = coordinator.handle(GateInput.AnsweredYes(YOUTUBE))
        assertEquals(
            listOf(GateUpdate(YOUTUBE, Prompting, PickingDuration, listOf(ShowDurationPicker), "YouTube")),
            updates,
        )
        assertEquals(
            listOf(GateUpdate(YOUTUBE, PickingDuration, Idle, listOf(HideOverlay), "YouTube")),
            front(LAUNCHER),
        )
    }

    @Test
    fun `a second tap after answering is ignored`() {
        prompt(YOUTUBE)
        coordinator.handle(GateInput.AnsweredNo(YOUTUBE, clock.now))
        assertTrue(coordinator.handle(GateInput.AnsweredNo(YOUTUBE, clock.now)).isEmpty())
        assertTrue(coordinator.handle(GateInput.AnsweredYes(YOUTUBE)).isEmpty())
    }

    @Test
    fun `answers for an unwatched app are ignored`() {
        assertTrue(coordinator.handle(GateInput.AnsweredNo(WHATSAPP, clock.now)).isEmpty())
        assertTrue(coordinator.handle(GateInput.AnsweredYes(WHATSAPP)).isEmpty())
    }
}

package com.aladin.foreground2.service.overlay

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/** Records the keys sent; [playing] stands in for whatever the system reports. */
private class FakeMediaControls : MediaControls {
    var playing = false
    val keys = mutableListOf<String>()
    var onChange: (() -> Unit)? = null

    override fun isPlaying() = playing

    override fun sendPause() {
        keys += "pause"
        playing = false
    }

    override fun sendPlay() {
        keys += "play"
        playing = true
    }

    override fun watch(onChange: () -> Unit) {
        this.onChange = onChange
    }

    override fun stopWatching() {
        onChange = null
    }

    /** Another app starts a player, and the system reports the change. */
    fun startPlayer() {
        playing = true
        onChange?.invoke()
    }
}

class KeyEventMediaPauserTest {
    private val controls = FakeMediaControls()
    private val pauser = KeyEventMediaPauser(controls)

    @Test
    fun `pauses what is playing, and resumes it on release`() {
        controls.playing = true
        pauser.pause()
        assertEquals(listOf("pause"), controls.keys)
        pauser.release()
        assertEquals(listOf("pause", "play"), controls.keys)
    }

    @Test
    fun `nothing playing, nothing sent, and release never starts playback`() {
        pauser.pause()
        pauser.release()
        assertEquals(emptyList<String>(), controls.keys)
    }

    @Test
    fun `a player starting under the overlay is paused, then resumed on release`() {
        pauser.pause()
        controls.startPlayer()
        assertEquals(listOf("pause"), controls.keys)
        pauser.release()
        assertEquals(listOf("pause", "play"), controls.keys)
    }

    @Test
    fun `stops watching on release, so later playback is left alone`() {
        pauser.pause()
        pauser.release()
        assertNull(controls.onChange)
        controls.playing = true
        pauser.release()
        assertEquals(emptyList<String>(), controls.keys)
    }

    @Test
    fun `repeated pause, as overlays replace each other, sends one pause and one play`() {
        controls.playing = true
        pauser.pause()
        pauser.pause()
        pauser.pause()
        pauser.release()
        pauser.release()
        assertEquals(listOf("pause", "play"), controls.keys)
    }
}

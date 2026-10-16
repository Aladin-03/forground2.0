package com.aladin.foreground2.service.overlay

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Records the keys sent; [playing] and [muted] stand in for whatever the system reports. */
private class FakeMediaControls : MediaControls {
    var playing = false
    var muted = false
    val keys = mutableListOf<String>()
    var onChange: (() -> Unit)? = null

    override fun isPlaying() = playing

    override fun sendPause() {
        keys += "pause"
        playing = false
    }

    override fun isMuted() = muted

    override fun mute() {
        muted = true
    }

    override fun unmute() {
        muted = false
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

private class FakeMuteRecord : MuteRecord {
    var saved = false

    override fun save(muted: Boolean) {
        saved = muted
    }

    override suspend fun load() = saved
}

class KeyEventMediaPauserTest {
    private val controls = FakeMediaControls()
    private val record = FakeMuteRecord()
    private val pauser = KeyEventMediaPauser(controls, record)

    @Test
    fun `pauses what is playing, and leaves it paused on release`() {
        controls.playing = true
        pauser.pause()
        assertEquals(listOf("pause"), controls.keys)
        pauser.release()
        assertEquals(listOf("pause"), controls.keys)
        assertFalse(controls.playing)
    }

    @Test
    fun `nothing playing, nothing sent, and release never starts playback`() {
        pauser.pause()
        pauser.release()
        assertEquals(emptyList<String>(), controls.keys)
    }

    @Test
    fun `a player starting under the overlay is paused, and stays paused on release`() {
        pauser.pause()
        controls.startPlayer()
        assertEquals(listOf("pause"), controls.keys)
        pauser.release()
        assertEquals(listOf("pause"), controls.keys)
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
    fun `repeated pause, as overlays replace each other, sends one pause and never play`() {
        controls.playing = true
        pauser.pause()
        pauser.pause()
        pauser.pause()
        pauser.release()
        pauser.release()
        assertEquals(listOf("pause"), controls.keys)
    }

    @Test
    fun `mutes while held and unmutes on release, recording both`() {
        pauser.pause()
        assertTrue(controls.muted)
        assertTrue(record.saved)
        pauser.release()
        assertFalse(controls.muted)
        assertFalse(record.saved)
    }

    @Test
    fun `a player that ignores the pause key, like a Short, is still silent while held`() {
        controls.playing = true
        val ignoresKeys =
            object : MediaControls by controls {
                override fun sendPause() {
                    controls.keys += "pause"
                }
            }
        val shortsPauser = KeyEventMediaPauser(ignoresKeys, record)
        shortsPauser.pause()
        assertTrue(controls.playing)
        assertTrue(controls.muted)
    }

    @Test
    fun `media the user muted stays muted after release`() {
        controls.muted = true
        pauser.pause()
        pauser.release()
        assertTrue(controls.muted)
        assertFalse(record.saved)
    }

    @Test
    fun `restore unmutes media left muted by a run that died mid-overlay`() =
        runTest {
            record.saved = true
            controls.muted = true
            pauser.restoreAfterCrash()
            assertFalse(controls.muted)
            assertFalse(record.saved)
        }

    @Test
    fun `restore leaves a mute it did not make alone`() =
        runTest {
            controls.muted = true
            pauser.restoreAfterCrash()
            assertTrue(controls.muted)
        }
}

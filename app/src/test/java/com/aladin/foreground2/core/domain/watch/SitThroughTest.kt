package com.aladin.foreground2.core.domain.watch

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

private const val START = 1_700_000_000_000L
private const val COUNTDOWN = 300_000L
private val CHANGE = PendingChange.Disable("com.google.android.youtube", WatchMode.STRICT)

class SitThroughTest {
    private val running = SitThrough.start(CHANGE, START, COUNTDOWN)

    @Test
    fun `starts running for the full duration`() {
        assertEquals(SitThrough.Running(CHANGE, START + COUNTDOWN), running)
    }

    @Test
    fun `completes only once the full duration has passed`() {
        assertEquals(running, running.tick(START + COUNTDOWN - 1))
        assertEquals(SitThrough.Completed(CHANGE), running.tick(START + COUNTDOWN))
    }

    @Test
    fun `leaving cancels it, and a cancelled countdown never completes`() {
        val cancelled = running.leave()
        assertEquals(SitThrough.Cancelled(CHANGE), cancelled)
        assertEquals(cancelled, cancelled.tick(START + COUNTDOWN))
    }

    @Test
    fun `leaving one second before the end still cancels`() {
        val almost = running.tick(START + COUNTDOWN - 1_000)
        assertEquals(SitThrough.Cancelled(CHANGE), almost.leave())
    }

    @Test
    fun `the next attempt starts again from the full duration`() {
        running.tick(START + 4 * 60_000).leave()
        val later = START + 10 * 60_000
        assertEquals(SitThrough.Running(CHANGE, later + COUNTDOWN), SitThrough.start(CHANGE, later, COUNTDOWN))
    }

    @Test
    fun `leaving after completing changes nothing`() {
        val completed = running.tick(START + COUNTDOWN)
        assertEquals(completed, completed.leave())
    }
}

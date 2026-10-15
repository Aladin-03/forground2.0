package com.aladin.foreground2.service.detection

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

private const val OWN = "com.aladin.foreground2"
private const val YOUTUBE = "com.google.android.youtube"
private const val LAUNCHER = "com.transsion.hilauncher"
private const val WHATSAPP = "com.whatsapp"

class FrontAppTrackerTest {
    private val tracker = FrontAppTracker(OWN).apply { consume(emptyList(), now = 0L) }

    private fun resumed(
        pkg: String,
        at: Long,
    ) = ResumedEvent(pkg, at)

    @Test
    fun `first batch reports only the latest app, stamped now`() {
        val fresh = FrontAppTracker(OWN)
        val changes = fresh.consume(listOf(resumed(LAUNCHER, 10), resumed(YOUTUBE, 20)), now = 500)
        assertEquals(listOf(FrontAppChanged(YOUTUBE, 500)), changes)
        assertEquals(YOUTUBE, fresh.current)
    }

    @Test
    fun `first batch with nothing in it leaves the front unknown`() {
        val fresh = FrontAppTracker(OWN)
        assertTrue(fresh.consume(emptyList(), now = 500).isEmpty())
        assertNull(fresh.current)
        assertEquals(listOf(FrontAppChanged(YOUTUBE, 600)), fresh.consume(listOf(resumed(YOUTUBE, 600)), now = 700))
    }

    @Test
    fun `reports each switch with its event time`() {
        val changes = tracker.consume(listOf(resumed(YOUTUBE, 100), resumed(LAUNCHER, 200)), now = 300)
        assertEquals(listOf(FrontAppChanged(YOUTUBE, 100), FrontAppChanged(LAUNCHER, 200)), changes)
    }

    @Test
    fun `quick round trip inside one poll is a leave and a fresh entry`() {
        tracker.consume(listOf(resumed(YOUTUBE, 100)), now = 150)
        val changes = tracker.consume(listOf(resumed(WHATSAPP, 200), resumed(YOUTUBE, 300)), now = 400)
        assertEquals(listOf(FrontAppChanged(WHATSAPP, 200), FrontAppChanged(YOUTUBE, 300)), changes)
    }

    @Test
    fun `another activity of the same app is not a change`() {
        tracker.consume(listOf(resumed(YOUTUBE, 100)), now = 150)
        assertTrue(tracker.consume(listOf(resumed(YOUTUBE, 200)), now = 250).isEmpty())
    }

    @Test
    fun `own package never counts as leaving`() {
        tracker.consume(listOf(resumed(YOUTUBE, 100)), now = 150)
        assertTrue(tracker.consume(listOf(resumed(OWN, 200)), now = 250).isEmpty())
        assertTrue(tracker.consume(listOf(resumed(YOUTUBE, 300)), now = 350).isEmpty())
        assertEquals(YOUTUBE, tracker.current)
    }

    @Test
    fun `events already seen in an overlapping window are skipped`() {
        tracker.consume(listOf(resumed(YOUTUBE, 100), resumed(LAUNCHER, 200)), now = 250)
        val changes = tracker.consume(listOf(resumed(YOUTUBE, 100), resumed(LAUNCHER, 200)), now = 300)
        assertTrue(changes.isEmpty())
    }

    @Test
    fun `out-of-order events are sorted by time`() {
        val changes = tracker.consume(listOf(resumed(LAUNCHER, 200), resumed(YOUTUBE, 100)), now = 300)
        assertEquals(listOf(YOUTUBE, LAUNCHER), changes.map { it.packageName })
    }
}

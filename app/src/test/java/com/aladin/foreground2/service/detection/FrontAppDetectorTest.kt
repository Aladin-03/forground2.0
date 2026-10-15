package com.aladin.foreground2.service.detection

import app.cash.turbine.test
import com.aladin.foreground2.core.config.Timing
import com.aladin.foreground2.core.domain.FakeClock
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

private const val OWN = "com.aladin.foreground2"
private const val YOUTUBE = "com.google.android.youtube"
private const val LAUNCHER = "com.transsion.hilauncher"
private const val START = 1_700_000_000_000L
private val TIMING = Timing(breathDurationMillis = 10_000L, allowedWindowMillis = 60_000L, pollIntervalMillis = 500L)

/** Usage log backed by a list; records each queried window. */
private class FakeUsageEventSource : UsageEventSource {
    val events = mutableListOf<ResumedEvent>()
    val windows = mutableListOf<LongRange>()

    override fun resumedEvents(
        from: Long,
        to: Long,
    ): List<ResumedEvent> {
        windows += from until to
        return events.filter { it.timestamp in from until to }
    }
}

class FrontAppDetectorTest {
    private val clock = FakeClock(now = START)
    private val source = FakeUsageEventSource()
    private val detector = FrontAppDetector(source, clock, TIMING, OWN)

    @Test
    fun `first poll looks back, later polls cover the time since the last one`() {
        detector.poll()
        clock.advanceBy(500)
        detector.poll()
        assertEquals(
            listOf(
                START - FrontAppDetector.INITIAL_LOOKBACK_MILLIS until START,
                START - FrontAppDetector.OVERLAP_MILLIS until START + 500,
            ),
            source.windows,
        )
    }

    @Test
    fun `reports the app already in front, then each switch`() {
        source.events += ResumedEvent(YOUTUBE, START - 60_000)
        assertEquals(listOf(FrontAppChanged(YOUTUBE, START)), detector.poll())

        source.events += ResumedEvent(LAUNCHER, START + 200)
        clock.advanceBy(500)
        assertEquals(listOf(FrontAppChanged(LAUNCHER, START + 200)), detector.poll())
    }

    @Test
    fun `an event logged late, just before the last poll, is still caught`() {
        detector.poll()
        clock.advanceBy(500)
        detector.poll()
        // Timestamped before the previous poll but only visible now.
        source.events += ResumedEvent(YOUTUBE, START + 400)
        clock.advanceBy(500)
        assertEquals(listOf(FrontAppChanged(YOUTUBE, START + 400)), detector.poll())
    }

    @Test
    fun `changes polls every interval while collected`() =
        runTest {
            detector.changes().test {
                source.events += ResumedEvent(YOUTUBE, START + 100)
                clock.advanceBy(500)
                assertEquals(FrontAppChanged(YOUTUBE, START + 100), awaitItem())

                source.events += ResumedEvent(LAUNCHER, START + 700)
                clock.advanceBy(500)
                assertEquals(FrontAppChanged(LAUNCHER, START + 700), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
            assertEquals(3, source.windows.size)
        }
}

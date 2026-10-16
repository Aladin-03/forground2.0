package com.aladin.foreground2.widget

import app.cash.turbine.test
import com.aladin.foreground2.core.config.Timing
import com.aladin.foreground2.core.domain.Clock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.time.ZoneId

private const val MINUTE = 60_000L
private const val SETTLE = WidgetRefreshTriggers.UNLOCK_SETTLE_MILLIS
private val ZONE = ZoneId.of("Asia/Kolkata")
private val TIMING =
    Timing(
        breathDurationMillis = 10_000L,
        allowedWindowMillis = 60_000L,
        pollIntervalMillis = 500L,
        permissionCheckIntervalMillis = 5_000L,
        widgetRefreshIntervalMillis = 5 * MINUTE,
        widgetMinRefreshIntervalMillis = MINUTE,
    )

private fun millisAt(
    hour: Int,
    minute: Int,
) = LocalDateTime.of(2026, 10, 16, hour, minute).atZone(ZONE).toEpochSecond() * 1000

/** Wall clock following the test scheduler's virtual time from [start]. */
private fun TestScope.clockFrom(start: Long) = Clock { start + currentTime }

@OptIn(ExperimentalCoroutinesApi::class)
class WidgetRefreshTriggersTest {
    @Test
    fun `refreshes on unlock, then every 5 minutes while unlocked`() =
        runTest {
            val unlocked = MutableStateFlow(true)
            WidgetRefreshTriggers(clockFrom(millisAt(10, 0)), TIMING) { ZONE }.triggers(unlocked).test {
                advanceTimeBy(SETTLE + 1)
                assertEquals(RefreshReason.Unlock, awaitItem())
                advanceTimeBy(5 * MINUTE)
                assertEquals(RefreshReason.Tick, awaitItem())
                advanceTimeBy(5 * MINUTE)
                assertEquals(RefreshReason.Tick, awaitItem())
                expectNoEvents()
            }
        }

    @Test
    fun `nothing while the screen is off, and a fresh unlock refresh on the next unlock`() =
        runTest {
            val unlocked = MutableStateFlow(true)
            WidgetRefreshTriggers(clockFrom(millisAt(10, 0)), TIMING) { ZONE }.triggers(unlocked).test {
                advanceTimeBy(SETTLE + 1)
                assertEquals(RefreshReason.Unlock, awaitItem())
                unlocked.value = false
                advanceTimeBy(60 * MINUTE)
                expectNoEvents()
                unlocked.value = true
                runCurrent()
                advanceTimeBy(SETTLE + 1)
                assertEquals(RefreshReason.Unlock, awaitItem())
            }
        }

    @Test
    fun `ticks just after midnight instead of waiting a full interval`() =
        runTest {
            val start = millisAt(23, 58)
            val unlocked = MutableStateFlow(true)
            WidgetRefreshTriggers(clockFrom(start), TIMING) { ZONE }.triggers(unlocked).test {
                advanceTimeBy(SETTLE + 1)
                assertEquals(RefreshReason.Unlock, awaitItem())
                assertEquals(RefreshReason.Midnight, awaitItem())
                val midnight = millisAt(0, 0) + 24 * 60 * MINUTE
                val at = start + currentTime
                assert(at in midnight..midnight + 2_000) { "midnight tick at ${at - midnight} ms after midnight" }
                advanceTimeBy(5 * MINUTE)
                assertEquals(RefreshReason.Tick, awaitItem())
            }
        }
}

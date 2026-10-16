package com.aladin.foreground2.core.domain.usage

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.time.ZoneId

private const val MINUTE = 60_000L
private const val HOUR = 60 * MINUTE

// Local midnight is at 0; events before it are yesterday.
private const val MIDNIGHT = 0L
private const val NOW = 10 * HOUR

private const val YOUTUBE = "com.google.android.youtube"
private const val CHROME = "com.android.chrome"
private const val LAUNCHER = "com.transsion.hilauncher"
private const val SELF = "com.aladin.foreground2"
private val EXCLUDED = setOf(LAUNCHER, SELF, "com.android.systemui")

private fun resumed(
    pkg: String,
    at: Long,
    cls: String = "$pkg.Main",
) = UsageEvent(UsageEventType.ActivityResumed, pkg, cls, at)

private fun paused(
    pkg: String,
    at: Long,
    cls: String = "$pkg.Main",
) = UsageEvent(UsageEventType.ActivityPaused, pkg, cls, at)

private fun keyguardHidden(at: Long) = UsageEvent(UsageEventType.KeyguardHidden, "android", null, at)

private fun keyguardShown(at: Long) = UsageEvent(UsageEventType.KeyguardShown, "android", null, at)

private fun screenOff(at: Long) = UsageEvent(UsageEventType.ScreenNonInteractive, "android", null, at)

private fun times(vararg events: UsageEvent) = foregroundTimes(events.toList(), MIDNIGHT, NOW, EXCLUDED)

class UnlockCountTest {
    @Test
    fun `counts keyguard-hidden events today only`() {
        val events =
            listOf(
                keyguardHidden(MIDNIGHT - MINUTE),
                keyguardHidden(HOUR),
                keyguardShown(2 * HOUR),
                keyguardHidden(3 * HOUR),
                keyguardHidden(NOW + MINUTE),
                resumed(YOUTUBE, 4 * HOUR),
            )
        assertEquals(2, countUnlocks(events, MIDNIGHT, NOW))
    }

    @Test
    fun `an event delivered twice counts once`() {
        val events = listOf(keyguardHidden(HOUR), keyguardHidden(HOUR), keyguardHidden(2 * HOUR))
        assertEquals(2, countUnlocks(events, MIDNIGHT, NOW))
    }

    @Test
    fun `counts unlocks at midnight and at now`() {
        assertEquals(2, countUnlocks(listOf(keyguardHidden(MIDNIGHT), keyguardHidden(NOW)), MIDNIGHT, NOW))
    }

    @Test
    fun `no events, no unlocks`() {
        assertEquals(0, countUnlocks(emptyList(), MIDNIGHT, NOW))
    }
}

class ForegroundTimeTest {
    @Test
    fun `sums resumed-paused pairs per app`() {
        val result =
            times(
                resumed(YOUTUBE, HOUR),
                paused(YOUTUBE, HOUR + 10 * MINUTE),
                resumed(CHROME, 2 * HOUR),
                paused(CHROME, 2 * HOUR + 5 * MINUTE),
                resumed(YOUTUBE, 3 * HOUR),
                paused(YOUTUBE, 3 * HOUR + 20 * MINUTE),
            )
        assertEquals(mapOf(YOUTUBE to 30 * MINUTE, CHROME to 5 * MINUTE), result)
    }

    @Test
    fun `moving between activities of one app adds up`() {
        val result =
            times(
                resumed(YOUTUBE, HOUR, cls = "Main"),
                paused(YOUTUBE, HOUR + MINUTE, cls = "Main"),
                resumed(YOUTUBE, HOUR + MINUTE, cls = "Player"),
                paused(YOUTUBE, HOUR + 5 * MINUTE, cls = "Player"),
            )
        assertEquals(mapOf(YOUTUBE to 5 * MINUTE), result)
    }

    @Test
    fun `a session that started before midnight is clipped at midnight`() {
        val result =
            times(
                resumed(YOUTUBE, MIDNIGHT - 30 * MINUTE),
                paused(YOUTUBE, MIDNIGHT + 15 * MINUTE),
            )
        assertEquals(mapOf(YOUTUBE to 15 * MINUTE), result)
    }

    @Test
    fun `a session entirely before midnight counts nothing`() {
        val result = times(resumed(YOUTUBE, MIDNIGHT - HOUR), paused(YOUTUBE, MIDNIGHT - MINUTE))
        assertEquals(emptyMap<String, Long>(), result)
    }

    @Test
    fun `a pause with no resume, before anything else, counts from midnight`() {
        // Queried from midnight: the resume happened yesterday, so only the pause is in the log.
        val result =
            times(
                paused(YOUTUBE, 20 * MINUTE),
                resumed(CHROME, HOUR),
                paused(CHROME, HOUR + MINUTE),
            )
        assertEquals(mapOf(YOUTUBE to 20 * MINUTE, CHROME to MINUTE), result)
    }

    @Test
    fun `a still-open session counts up to now`() {
        val result = times(resumed(CHROME, HOUR), paused(CHROME, 2 * HOUR), resumed(YOUTUBE, NOW - 7 * MINUTE))
        assertEquals(mapOf(CHROME to HOUR, YOUTUBE to 7 * MINUTE), result)
    }

    @Test
    fun `a session open since before midnight and still open counts the whole day`() {
        assertEquals(mapOf(YOUTUBE to NOW - MIDNIGHT), times(resumed(YOUTUBE, MIDNIGHT - HOUR)))
    }

    @Test
    fun `a missing pause is closed by the next resume`() {
        val result =
            times(
                resumed(YOUTUBE, HOUR),
                // No YouTube pause: Chrome coming to the front ends it.
                resumed(CHROME, HOUR + 10 * MINUTE),
                paused(CHROME, HOUR + 12 * MINUTE),
            )
        assertEquals(mapOf(YOUTUBE to 10 * MINUTE, CHROME to 2 * MINUTE), result)
    }

    @Test
    fun `a missing pause is closed by the launcher, which gets no time`() {
        val result = times(resumed(YOUTUBE, HOUR), resumed(LAUNCHER, HOUR + 3 * MINUTE))
        assertEquals(mapOf(YOUTUBE to 3 * MINUTE), result)
    }

    @Test
    fun `a missing pause is closed by the screen going off`() {
        val result = times(resumed(YOUTUBE, HOUR), screenOff(HOUR + 4 * MINUTE), keyguardHidden(5 * HOUR))
        assertEquals(mapOf(YOUTUBE to 4 * MINUTE), result)
    }

    @Test
    fun `an orphan pause after the front is known is ignored`() {
        val result =
            times(
                resumed(CHROME, HOUR),
                paused(CHROME, HOUR + MINUTE),
                paused(YOUTUBE, 2 * HOUR),
            )
        assertEquals(mapOf(CHROME to MINUTE), result)
    }

    @Test
    fun `duplicate events count once`() {
        val result =
            times(
                resumed(YOUTUBE, HOUR),
                resumed(YOUTUBE, HOUR),
                // Overlapping reads can also repeat a resume later, while the activity is still open.
                resumed(YOUTUBE, HOUR + MINUTE),
                paused(YOUTUBE, HOUR + 10 * MINUTE),
                paused(YOUTUBE, HOUR + 10 * MINUTE),
                paused(YOUTUBE, HOUR + 11 * MINUTE),
            )
        assertEquals(mapOf(YOUTUBE to 10 * MINUTE), result)
    }

    @Test
    fun `events out of order are sorted by time`() {
        val result = times(paused(YOUTUBE, HOUR + 5 * MINUTE), resumed(YOUTUBE, HOUR))
        assertEquals(mapOf(YOUTUBE to 5 * MINUTE), result)
    }

    @Test
    fun `events after now are ignored`() {
        val result = times(resumed(YOUTUBE, NOW - MINUTE), paused(YOUTUBE, NOW + HOUR))
        assertEquals(mapOf(YOUTUBE to MINUTE), result)
    }

    @Test
    fun `excluded packages get no time`() {
        val result =
            times(
                resumed(LAUNCHER, HOUR),
                paused(LAUNCHER, 2 * HOUR),
                resumed(SELF, 2 * HOUR),
                paused(SELF, 3 * HOUR),
                resumed("com.android.systemui", 3 * HOUR),
                paused("com.android.systemui", 4 * HOUR),
            )
        assertEquals(emptyMap<String, Long>(), result)
    }
}

class DailyUsageTest {
    @Test
    fun `combines unlocks and app times, most used first`() {
        val events =
            listOf(
                keyguardHidden(HOUR - MINUTE),
                resumed(CHROME, HOUR),
                paused(CHROME, HOUR + 5 * MINUTE),
                resumed(YOUTUBE, HOUR + 5 * MINUTE),
                paused(YOUTUBE, HOUR + 45 * MINUTE),
                resumed(LAUNCHER, HOUR + 45 * MINUTE),
            )
        val usage = dailyUsage(events, MIDNIGHT, NOW, EXCLUDED)
        assertEquals(1, usage.unlocks)
        assertEquals(listOf(AppUsage(YOUTUBE, 40 * MINUTE), AppUsage(CHROME, 5 * MINUTE)), usage.apps)
        assertEquals(45 * MINUTE, usage.totalMillis)
    }

    @Test
    fun `start of day is local midnight`() {
        val zone = ZoneId.of("Asia/Kolkata")

        fun at(minute: Int) = LocalDateTime.of(2026, 10, 16, 0, minute).atZone(zone).toEpochSecond() * 1000

        val now = at(20)
        val midnight = at(0)
        assertEquals(midnight, startOfDayMillis(now, zone))
        assertEquals(midnight, startOfDayMillis(midnight, zone))
    }
}

class UsageDurationTest {
    @Test
    fun `rounds down to whole minutes`() {
        assertEquals(UsageDuration.Minutes(0), UsageDuration.of(0))
        assertEquals(UsageDuration.UnderMinute, UsageDuration.of(MINUTE - 1))
        assertEquals(UsageDuration.Minutes(1), UsageDuration.of(MINUTE))
        assertEquals(UsageDuration.Minutes(59), UsageDuration.of(HOUR - 1))
        assertEquals(UsageDuration.Hours(1, 0), UsageDuration.of(HOUR))
        assertEquals(UsageDuration.Hours(3, 7), UsageDuration.of(3 * HOUR + 7 * MINUTE + 59_999))
    }
}

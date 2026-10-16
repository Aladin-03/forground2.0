package com.aladin.foreground2.core.data.usage

import com.aladin.foreground2.core.data.db.DailyAppUsageEntity
import com.aladin.foreground2.core.data.db.DailyStatsDao
import com.aladin.foreground2.core.data.db.DailyStatsEntity
import com.aladin.foreground2.core.domain.FakeClock
import com.aladin.foreground2.core.domain.ZoneProvider
import com.aladin.foreground2.core.domain.usage.UsageEvent
import com.aladin.foreground2.core.domain.usage.UsageEventType
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.time.ZoneId

private const val MINUTE = 60_000L
private const val YOUTUBE = "com.google.android.youtube"
private const val LAUNCHER = "com.transsion.hilauncher"
private val INDIA = ZoneId.of("Asia/Kolkata")

private fun at(
    day: Int,
    hour: Int,
    minute: Int = 0,
    zone: ZoneId = INDIA,
) = LocalDateTime
    .of(2026, 10, day, hour, minute)
    .atZone(zone)
    .toInstant()
    .toEpochMilli()

private fun resumed(
    pkg: String,
    at: Long,
) = UsageEvent(UsageEventType.ActivityResumed, pkg, "$pkg.Main", at)

private fun paused(
    pkg: String,
    at: Long,
) = UsageEvent(UsageEventType.ActivityPaused, pkg, "$pkg.Main", at)

private fun unlock(at: Long) = UsageEvent(UsageEventType.KeyguardHidden, "android", null, at)

/** The usage log as a list; like the real one, a range is `[from, to)`. */
private class FakeUsageEventReader(
    val log: MutableList<UsageEvent> = mutableListOf(),
) : UsageEventReader {
    override fun events(
        from: Long,
        to: Long,
    ) = log.filter { it.timestamp in from until to }.sortedBy { it.timestamp }

    override fun excludedPackages() = setOf(LAUNCHER)
}

/** Implements only the queries, so [DailyStatsDao.upsertDay]'s real default body runs. */
private class FakeDailyStatsDao : DailyStatsDao {
    val days = sortedMapOf<String, DailyStatsEntity>()
    val apps = mutableMapOf<Pair<String, String>, DailyAppUsageEntity>()
    var writes = 0

    override suspend fun savedDates() = days.keys.toList()

    override suspend fun allDays() = days.values.toList()

    override suspend fun allAppUsage() = apps.values.toList()

    override suspend fun upsertStats(stats: DailyStatsEntity) {
        writes++
        days[stats.date] = stats
    }

    override suspend fun deleteAppUsage(date: String) {
        apps.keys.removeAll { it.first == date }
    }

    override suspend fun upsertAppUsage(apps: List<DailyAppUsageEntity>) {
        apps.forEach { this.apps[it.date to it.packageName] = it }
    }

    fun minutes(
        date: String,
        pkg: String,
    ) = apps[date to pkg]?.foregroundMs?.div(MINUTE)
}

class UsageHistoryRecorderTest {
    private val reader = FakeUsageEventReader()
    private val dao = FakeDailyStatsDao()
    private val clock = FakeClock(at(16, 10))
    private var zone: ZoneId = INDIA
    private val recorder = UsageHistoryRecorder(reader, dao, clock, ZoneProvider { zone })

    private fun session(
        from: Long,
        to: Long,
        pkg: String = YOUTUBE,
    ) {
        reader.log += resumed(pkg, from)
        reader.log += paused(pkg, to)
    }

    @Test
    fun `backfills every finished day fully in the log, skipping the partial first one and today`() =
        runTest {
            // The log starts on the 12th at 08:00, so the 12th may be missing its early hours.
            session(at(12, 8), at(12, 9))
            session(at(13, 8), at(13, 8, 20))
            session(at(14, 21), at(14, 21, 5))
            reader.log += unlock(at(15, 7))
            session(at(16, 9), at(16, 9, 30))

            assertEquals(3, recorder.catchUp(HistoryTrigger.AppStart))

            assertEquals(listOf("2026-10-13", "2026-10-14", "2026-10-15"), dao.days.keys.toList())
            assertEquals(20L, dao.minutes("2026-10-13", YOUTUBE))
            assertEquals(5L, dao.minutes("2026-10-14", YOUTUBE))
            assertEquals(1, dao.days.getValue("2026-10-15").unlocks)
            assertEquals(1, dao.days.getValue("2026-10-15").unlocksByHour[7])
        }

    @Test
    fun `running again writes nothing, rewriting yesterday writes only yesterday`() =
        runTest {
            session(at(13, 8), at(13, 9))
            session(at(15, 8), at(15, 9))
            recorder.catchUp(HistoryTrigger.AppStart)
            val writes = dao.writes

            assertEquals(0, recorder.catchUp(HistoryTrigger.StatsOpened))
            assertEquals(writes, dao.writes)

            // More of yesterday arrives in the log late; the midnight snapshot picks it up.
            session(at(15, 22), at(15, 22, 30))
            assertEquals(1, recorder.catchUp(HistoryTrigger.Midnight, rewriteYesterday = true))
            assertEquals(90L, dao.minutes("2026-10-15", YOUTUBE))
        }

    @Test
    fun `a session across midnight is split between the two days`() =
        runTest {
            session(at(13, 8), at(13, 9))
            session(at(14, 23, 50), at(15, 0, 10))
            reader.log += unlock(at(15, 0))

            recorder.catchUp(HistoryTrigger.AppStart)

            assertEquals(10L, dao.minutes("2026-10-14", YOUTUBE))
            assertEquals(10L, dao.minutes("2026-10-15", YOUTUBE))
            // An unlock at exactly midnight belongs to the new day, in its first hour.
            assertEquals(0, dao.days.getValue("2026-10-14").unlocks)
            assertEquals(1, dao.days.getValue("2026-10-15").unlocksByHour[0])
        }

    @Test
    fun `day boundaries follow the device time zone`() =
        runTest {
            zone = ZoneId.of("UTC")
            clock.now = at(16, 10, zone = zone)
            session(at(13, 8, zone = zone), at(13, 9, zone = zone))
            // 23:00-23:30 UTC on the 14th is 04:30-05:00 IST on the 15th.
            session(at(14, 23, zone = zone), at(14, 23, 30, zone = zone))

            recorder.catchUp(HistoryTrigger.AppStart)

            assertEquals(30L, dao.minutes("2026-10-14", YOUTUBE))
            assertNull(dao.minutes("2026-10-15", YOUTUBE))
        }

    @Test
    fun `nothing is written without a usage log`() =
        runTest {
            assertEquals(0, recorder.catchUp(HistoryTrigger.Worker, rewriteYesterday = true))
            assertEquals(0, dao.writes)
        }

    @Test
    fun `excluded packages get no time in saved days`() =
        runTest {
            session(at(13, 8), at(13, 9))
            session(at(14, 8), at(14, 9), pkg = LAUNCHER)

            recorder.catchUp(HistoryTrigger.AppStart)

            assertNull(dao.minutes("2026-10-14", LAUNCHER))
            assertEquals(0L, dao.days.getValue("2026-10-14").totalScreenMs)
        }
}

package com.aladin.foreground2.core.data.usage

import com.aladin.foreground2.core.data.db.DailyAppUsageEntity
import com.aladin.foreground2.core.data.db.DailyStatsDao
import com.aladin.foreground2.core.data.db.DailyStatsEntity
import com.aladin.foreground2.core.domain.Clock
import com.aladin.foreground2.core.domain.ZoneProvider
import com.aladin.foreground2.core.domain.usage.dailyUsage
import com.aladin.foreground2.core.domain.usage.localDate
import com.aladin.foreground2.core.domain.usage.missingDays
import com.aladin.foreground2.core.domain.usage.startOfDayMillis
import com.aladin.foreground2.core.logging.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/** What asked for a history catch-up, for the log. */
enum class HistoryTrigger { AppStart, ServiceStart, Midnight, Worker, StatsOpened }

/** Saves finished days' stats into Room, so history outlives the system's usage log. */
interface UsageHistory {
    /**
     * Saves every finished day in the lookback that isn't stored yet and is still fully in the usage log. With
     * [rewriteYesterday], yesterday is recomputed and saved even if already stored. Safe to run any number of times.
     * Returns how many days were written.
     */
    suspend fun catchUp(
        trigger: HistoryTrigger,
        rewriteYesterday: Boolean = false,
    ): Int
}

@Singleton
class UsageHistoryRecorder
    @Inject
    constructor(
        private val reader: UsageEventReader,
        private val dao: DailyStatsDao,
        private val clock: Clock,
        private val zones: ZoneProvider,
    ) : UsageHistory {
        private val mutex = Mutex()

        override suspend fun catchUp(
            trigger: HistoryTrigger,
            rewriteYesterday: Boolean,
        ): Int =
            mutex.withLock {
                withContext(Dispatchers.IO) {
                    val zone = zones.zone()
                    val today = localDate(clock.nowMillis(), zone)
                    val saved = dao.savedDates().map(LocalDate::parse).toSet()
                    val earliest = earliestEventMillis(today, zone)
                    val missing = missingDays(today, saved, earliest, zone, LOOKBACK_DAYS)
                    val yesterday = today.minusDays(1)
                    val yesterdayInLog = earliest != null && startOfDayMillis(yesterday, zone) >= earliest
                    val days = if (rewriteYesterday && yesterdayInLog) (missing + yesterday).distinct() else missing
                    val excluded = if (days.isEmpty()) emptySet() else reader.excludedPackages()
                    days.forEach { save(it, zone, excluded, trigger) }
                    Log.i(Log.Area.Stats) {
                        "history trigger=$trigger saved=${saved.size} missing=${missing.size} wrote=${days.size} " +
                            "logFrom=${earliest?.let { localDate(it, zone) }} zone=$zone"
                    }
                    days.size
                }
            }

        private suspend fun save(
            date: LocalDate,
            zone: ZoneId,
            excluded: Set<String>,
            trigger: HistoryTrigger,
        ) {
            val start = startOfDayMillis(date, zone)
            val end = startOfDayMillis(date.plusDays(1), zone)
            val usage = dailyUsage(reader.events(start, end), date, zone, end, excluded)
            val key = date.toString()
            dao.upsertDay(
                DailyStatsEntity(key, usage.unlocks, usage.totalMillis, usage.unlocksByHour),
                usage.apps.map { DailyAppUsageEntity(key, it.packageName, it.foregroundMillis) },
            )
            Log.i(Log.Area.Stats) {
                "snapshot date=$date trigger=$trigger unlocks=${usage.unlocks} " +
                    "totalMin=${usage.totalMillis / MINUTE_MILLIS} apps=${usage.apps.size}"
            }
        }

        /**
         * The oldest event still in the usage log within the lookback, found by reading day by day from the oldest,
         * stopping at the first day with any event. Null when there is none (e.g. no Usage access).
         */
        private fun earliestEventMillis(
            today: LocalDate,
            zone: ZoneId,
        ): Long? {
            for (back in LOOKBACK_DAYS downTo 0) {
                val day = today.minusDays(back.toLong())
                val events = reader.events(startOfDayMillis(day, zone), startOfDayMillis(day.plusDays(1), zone))
                events.minOfOrNull { it.timestamp }?.let { return it }
            }
            return null
        }

        internal companion object {
            /** How far back missing days are looked for; Android keeps its event log for roughly this long or less. */
            const val LOOKBACK_DAYS = 30
            private const val MINUTE_MILLIS = 60_000L
        }
    }

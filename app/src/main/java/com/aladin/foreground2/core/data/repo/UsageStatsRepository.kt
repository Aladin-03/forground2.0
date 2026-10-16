package com.aladin.foreground2.core.data.repo

import com.aladin.foreground2.core.data.db.DailyStatsDao
import com.aladin.foreground2.core.data.usage.UsageEventReader
import com.aladin.foreground2.core.domain.Clock
import com.aladin.foreground2.core.domain.ZoneProvider
import com.aladin.foreground2.core.domain.usage.AppUsage
import com.aladin.foreground2.core.domain.usage.DailyUsage
import com.aladin.foreground2.core.domain.usage.dailyUsage
import com.aladin.foreground2.core.domain.usage.localDate
import com.aladin.foreground2.core.domain.usage.startOfDayMillis
import com.aladin.foreground2.core.logging.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/** Usage stats: today live from the system's usage log, past days from Room. */
interface UsageStatsRepository {
    /** Unlocks and per-app foreground time since local midnight. Empty without Usage access. */
    suspend fun today(): DailyUsage

    /** Every saved finished day, oldest first. */
    suspend fun pastDays(): List<DailyUsage>
}

@Singleton
class SystemUsageStatsRepository
    @Inject
    constructor(
        private val reader: UsageEventReader,
        private val dao: DailyStatsDao,
        private val clock: Clock,
        private val zones: ZoneProvider,
    ) : UsageStatsRepository {
        override suspend fun today(): DailyUsage =
            withContext(Dispatchers.IO) {
                val zone = zones.zone()
                val now = clock.nowMillis()
                val today = localDate(now, zone)
                val events = reader.events(startOfDayMillis(today, zone), now)
                val excluded = reader.excludedPackages()
                dailyUsage(events, today, zone, now, excluded).also { usage ->
                    Log.i(Log.Area.Stats) {
                        "today date=$today now=$now events=${events.size} unlocks=${usage.unlocks} " +
                            "total=${minSec(usage.totalMillis)} apps=${usage.apps.size} excluded=$excluded"
                    }
                    Log.d(Log.Area.Stats) {
                        "top " +
                            usage.apps.take(LOGGED_APPS).joinToString {
                                "${it.packageName}=${minSec(it.foregroundMillis)}"
                            }
                    }
                }
            }

        override suspend fun pastDays(): List<DailyUsage> {
            val apps = dao.allAppUsage().groupBy { it.date }
            return dao.allDays().map { day ->
                DailyUsage(
                    date = LocalDate.parse(day.date),
                    unlocksByHour = day.unlocksByHour,
                    apps =
                        apps[day.date]
                            .orEmpty()
                            .map { AppUsage(it.packageName, it.foregroundMs) }
                            .sortedByDescending { it.foregroundMillis },
                )
            }
        }

        private fun minSec(millis: Long): String =
            "${millis / MINUTE_MILLIS}m${millis % MINUTE_MILLIS / SECOND_MILLIS}s"

        private companion object {
            const val LOGGED_APPS = 10
            const val MINUTE_MILLIS = 60_000L
            const val SECOND_MILLIS = 1_000L
        }
    }

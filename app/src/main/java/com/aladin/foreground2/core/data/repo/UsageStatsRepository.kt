package com.aladin.foreground2.core.data.repo

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import com.aladin.foreground2.core.domain.Clock
import com.aladin.foreground2.core.domain.usage.DailyUsage
import com.aladin.foreground2.core.domain.usage.UsageEvent
import com.aladin.foreground2.core.domain.usage.UsageEventType
import com.aladin.foreground2.core.domain.usage.dailyUsage
import com.aladin.foreground2.core.domain.usage.startOfDayMillis
import com.aladin.foreground2.core.logging.Log
import com.aladin.foreground2.di.OwnPackageName
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/** Today's usage stats, read from the system's usage log. */
interface UsageStatsRepository {
    /** Unlocks and per-app foreground time since local midnight. Empty without Usage access. */
    suspend fun today(): DailyUsage
}

/**
 * Reads `UsageStatsManager.queryEvents` from local midnight to now and computes the stats in core/domain. This app,
 * system UI and the default launcher get no time.
 */
@Singleton
class SystemUsageStatsRepository
    @Inject
    constructor(
        @ApplicationContext context: Context,
        private val clock: Clock,
        @param:OwnPackageName private val ownPackage: String,
    ) : UsageStatsRepository {
        private val usageStats = context.getSystemService(UsageStatsManager::class.java)
        private val packageManager = context.packageManager

        override suspend fun today(): DailyUsage =
            withContext(Dispatchers.IO) {
                val now = clock.nowMillis()
                val startOfDay = startOfDayMillis(now, ZoneId.systemDefault())
                val events = readEvents(startOfDay, now)
                val excluded = setOfNotNull(ownPackage, SYSTEM_UI_PACKAGE, defaultLauncher())
                dailyUsage(events, startOfDay, now, excluded).also { usage ->
                    Log.i(Log.Area.Stats) {
                        "today from=$startOfDay now=$now events=${events.size} unlocks=${usage.unlocks} " +
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

        private fun readEvents(
            from: Long,
            to: Long,
        ): List<UsageEvent> {
            // Null while the user is still locked after boot (credential-encrypted storage unavailable).
            val events = usageStats.queryEvents(from, to) ?: return emptyList()
            val event = UsageEvents.Event()
            return buildList {
                while (events.hasNextEvent()) {
                    events.getNextEvent(event)
                    event.toDomain()?.let(::add)
                }
            }
        }

        /** The default home app, or null when none is set (the chooser resolves to "android"). */
        private fun defaultLauncher(): String? {
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            val info =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    packageManager.resolveActivity(
                        intent,
                        PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong()),
                    )
                } else {
                    @Suppress("DEPRECATION")
                    packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
                }
            return info?.activityInfo?.packageName?.takeIf { it != ANDROID_PACKAGE }
        }

        private fun UsageEvents.Event.toDomain(): UsageEvent? {
            val type =
                when (eventType) {
                    UsageEvents.Event.ACTIVITY_RESUMED -> UsageEventType.ActivityResumed
                    UsageEvents.Event.ACTIVITY_PAUSED -> UsageEventType.ActivityPaused
                    UsageEvents.Event.KEYGUARD_SHOWN -> UsageEventType.KeyguardShown
                    UsageEvents.Event.KEYGUARD_HIDDEN -> UsageEventType.KeyguardHidden
                    UsageEvents.Event.SCREEN_NON_INTERACTIVE -> UsageEventType.ScreenNonInteractive
                    else -> return null
                }
            return UsageEvent(type, packageName, className, timeStamp)
        }

        private fun minSec(millis: Long): String =
            "${millis / MINUTE_MILLIS}m${millis % MINUTE_MILLIS / SECOND_MILLIS}s"

        private companion object {
            const val SYSTEM_UI_PACKAGE = "com.android.systemui"
            const val ANDROID_PACKAGE = "android"
            const val LOGGED_APPS = 10
            const val MINUTE_MILLIS = 60_000L
            const val SECOND_MILLIS = 1_000L
        }
    }

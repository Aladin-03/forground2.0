package com.aladin.foreground2.core.data.usage

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import com.aladin.foreground2.core.domain.usage.UsageEvent
import com.aladin.foreground2.core.domain.usage.UsageEventType
import com.aladin.foreground2.di.OwnPackageName
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Reads the system's usage log as domain [UsageEvent]s. */
interface UsageEventReader {
    /** Events with `from <= timestamp < to`, oldest first. Empty without Usage access. */
    fun events(
        from: Long,
        to: Long,
    ): List<UsageEvent>

    /** Packages that get no screen time: this app, system UI and the default launcher. */
    fun excludedPackages(): Set<String>
}

@Singleton
class SystemUsageEventReader
    @Inject
    constructor(
        @ApplicationContext context: Context,
        @param:OwnPackageName private val ownPackage: String,
    ) : UsageEventReader {
        private val usageStats = context.getSystemService(UsageStatsManager::class.java)
        private val packageManager = context.packageManager

        override fun events(
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

        override fun excludedPackages(): Set<String> = setOfNotNull(ownPackage, SYSTEM_UI_PACKAGE, defaultLauncher())

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

        private companion object {
            const val SYSTEM_UI_PACKAGE = "com.android.systemui"
            const val ANDROID_PACKAGE = "android"
        }
    }

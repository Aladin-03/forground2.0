package com.aladin.foreground2.service.detection

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** An activity of [packageName] was resumed at [timestamp] (epoch ms). */
data class ResumedEvent(
    val packageName: String,
    val timestamp: Long,
)

/** Reads activity-resumed events from the system's usage log. */
fun interface UsageEventSource {
    /** Resumed events with `from <= timestamp < to`, oldest first. */
    fun resumedEvents(
        from: Long,
        to: Long,
    ): List<ResumedEvent>
}

@Singleton
class UsageStatsEventSource
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) : UsageEventSource {
        private val usageStats = context.getSystemService(UsageStatsManager::class.java)

        override fun resumedEvents(
            from: Long,
            to: Long,
        ): List<ResumedEvent> {
            // Null while the user is still locked after boot (credential-encrypted storage unavailable).
            val events = usageStats.queryEvents(from, to) ?: return emptyList()
            val event = UsageEvents.Event()
            return buildList {
                while (events.hasNextEvent()) {
                    events.getNextEvent(event)
                    if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                        add(ResumedEvent(event.packageName, event.timeStamp))
                    }
                }
            }
        }
    }

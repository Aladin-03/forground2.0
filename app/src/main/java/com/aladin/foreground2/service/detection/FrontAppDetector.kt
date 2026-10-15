package com.aladin.foreground2.service.detection

import com.aladin.foreground2.core.config.Timing
import com.aladin.foreground2.core.domain.Clock
import com.aladin.foreground2.core.logging.Log
import com.aladin.foreground2.di.OwnPackageName
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Polls the usage log every [Timing.pollIntervalMillis] for the window since the previous poll and emits each
 * front-app change. Polling runs only while [changes] is collected; collecting again later (e.g. after the screen
 * comes back on) carries on from the last poll, so nothing in between is lost.
 */
@Singleton
class FrontAppDetector
    @Inject
    constructor(
        private val source: UsageEventSource,
        private val clock: Clock,
        private val timing: Timing,
        @OwnPackageName ownPackage: String,
    ) {
        private val tracker = FrontAppTracker(ownPackage)
        private var lastPollAt: Long? = null

        fun changes(): Flow<FrontAppChanged> =
            flow {
                Log.d(Log.Area.Detect) { "polling started every ${timing.pollIntervalMillis} ms" }
                while (true) {
                    poll().forEach { emit(it) }
                    delay(timing.pollIntervalMillis)
                }
            }

        /** One poll. The first looks back [INITIAL_LOOKBACK_MILLIS] to find what is already in front. */
        internal fun poll(): List<FrontAppChanged> {
            val now = clock.nowMillis()
            // Events can land in the log slightly after their timestamp, so windows overlap; the tracker dedupes.
            val from = lastPollAt?.let { it - OVERLAP_MILLIS } ?: (now - INITIAL_LOOKBACK_MILLIS)
            val changes = tracker.consume(source.resumedEvents(from, now), now)
            lastPollAt = now
            changes.forEach { Log.d(Log.Area.Detect) { "front -> ${it.packageName} at=${it.at}" } }
            return changes
        }

        internal companion object {
            const val INITIAL_LOOKBACK_MILLIS = 60 * 60 * 1000L
            const val OVERLAP_MILLIS = 2_000L
        }
    }

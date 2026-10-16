package com.aladin.foreground2.widget

import com.aladin.foreground2.core.config.Timing
import com.aladin.foreground2.core.domain.Clock
import com.aladin.foreground2.core.domain.usage.startOfDayMillis
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import java.time.ZoneId
import javax.inject.Inject

/** Why the widgets are being refreshed, for the log. */
enum class RefreshReason { Unlock, Tick, Midnight, System, Deferred }

/**
 * When the detection service refreshes the widgets: on every unlock, then every [Timing.widgetRefreshIntervalMillis]
 * while unlocked, with an extra tick just after local midnight so the numbers reset on time. Nothing while the
 * screen is off or locked.
 */
class WidgetRefreshTriggers(
    private val clock: Clock,
    private val timing: Timing,
    private val zone: () -> ZoneId,
) {
    @Inject
    constructor(clock: Clock, timing: Timing) : this(clock, timing, ZoneId::systemDefault)

    @OptIn(ExperimentalCoroutinesApi::class)
    fun triggers(unlocked: Flow<Boolean>): Flow<RefreshReason> =
        unlocked.flatMapLatest { isUnlocked ->
            if (!isUnlocked) {
                emptyFlow()
            } else {
                flow {
                    // The usage log can lag the unlock slightly; give the KEYGUARD_HIDDEN event time to land.
                    delay(UNLOCK_SETTLE_MILLIS)
                    emit(RefreshReason.Unlock)
                    while (true) {
                        val now = clock.nowMillis()
                        val nextMidnight = nextMidnight(now)
                        val untilMidnight = nextMidnight - now + MIDNIGHT_SLACK_MILLIS
                        if (untilMidnight <= timing.widgetRefreshIntervalMillis) {
                            delay(untilMidnight)
                            emit(RefreshReason.Midnight)
                        } else {
                            delay(timing.widgetRefreshIntervalMillis)
                            emit(RefreshReason.Tick)
                        }
                    }
                }
            }
        }

    private fun nextMidnight(now: Long): Long {
        val zone = zone()
        val today = startOfDayMillis(now, zone)
        // A day after today's midnight, re-anchored so a DST change doesn't shift it by an hour.
        return startOfDayMillis(today + DAY_MILLIS + DST_MARGIN_MILLIS, zone)
    }

    internal companion object {
        const val UNLOCK_SETTLE_MILLIS = 2_000L
        const val MIDNIGHT_SLACK_MILLIS = 1_000L
        private const val DAY_MILLIS = 24 * 60 * 60 * 1000L
        private const val DST_MARGIN_MILLIS = 2 * 60 * 60 * 1000L
    }
}

package com.aladin.foreground2.core.domain.usage

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Foreground time of one app on one day. */
data class AppUsage(
    val packageName: String,
    val foregroundMillis: Long,
)

/** One day's stats: unlocks (in total and per local hour), and foreground time per app, most used first. */
data class DailyUsage(
    val date: LocalDate,
    val unlocksByHour: List<Int>,
    val apps: List<AppUsage>,
) {
    val unlocks: Int get() = unlocksByHour.sum()
    val totalMillis: Long get() = apps.sumOf { it.foregroundMillis }

    companion object {
        const val HOURS = 24

        fun empty(date: LocalDate) = DailyUsage(date, List(HOURS) { 0 }, emptyList())
    }
}

/** Local midnight (epoch ms) of the day containing [nowMillis] in [zone]. */
fun startOfDayMillis(
    nowMillis: Long,
    zone: ZoneId,
): Long = startOfDayMillis(localDate(nowMillis, zone), zone)

/** Local midnight (epoch ms) starting [date] in [zone]. */
fun startOfDayMillis(
    date: LocalDate,
    zone: ZoneId,
): Long = date.atStartOfDay(zone).toInstant().toEpochMilli()

/** The local date at [millis] in [zone]. */
fun localDate(
    millis: Long,
    zone: ZoneId,
): LocalDate = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()

/**
 * [date]'s stats from its local midnight up to [until] (exclusive): now for today, the next midnight for a finished
 * day. [excluded] packages get no time.
 */
fun dailyUsage(
    events: List<UsageEvent>,
    date: LocalDate,
    zone: ZoneId,
    until: Long,
    excluded: Set<String>,
): DailyUsage {
    val start = startOfDayMillis(date, zone)
    return DailyUsage(
        date = date,
        unlocksByHour = unlocksByHour(events, start, until, zone),
        apps =
            foregroundTimes(events, start, until, excluded)
                .map { (pkg, millis) -> AppUsage(pkg, millis) }
                .sortedWith(compareByDescending<AppUsage> { it.foregroundMillis }.thenBy { it.packageName }),
    )
}

/** Keyguard-hidden events in `[start, end)`. An event delivered twice counts once. */
fun countUnlocks(
    events: List<UsageEvent>,
    start: Long,
    end: Long,
): Int = unlockTimes(events, start, end).size

/**
 * Unlocks in `[start, end)` by the local hour (0-23, in [zone]) they happened in. On a day the clocks go back, the
 * repeated hour's unlocks land in the same slot.
 */
fun unlocksByHour(
    events: List<UsageEvent>,
    start: Long,
    end: Long,
    zone: ZoneId,
): List<Int> {
    val hours = IntArray(DailyUsage.HOURS)
    unlockTimes(events, start, end).forEach { hours[Instant.ofEpochMilli(it).atZone(zone).hour]++ }
    return hours.toList()
}

private fun unlockTimes(
    events: List<UsageEvent>,
    start: Long,
    end: Long,
): List<Long> =
    events
        .distinct()
        .filter { it.type == UsageEventType.KeyguardHidden && it.timestamp >= start && it.timestamp < end }
        .map { it.timestamp }

/**
 * Foreground time per package from resumed/paused pairs, clipped to `[startOfDay, now]`.
 *
 * Only one activity is in front at a time, so a resume (of any app, the launcher included) or the screen going off
 * also ends the open session; a missing pause therefore costs nothing. A repeated resume of the open activity, or a
 * pause of one that isn't open, is a duplicate and ignored, except a pause before anything was seen in front: that
 * app was already in front at the start of the log (e.g. since before midnight). A session still open at the end
 * counts up to [now]. [excluded] packages still end sessions but get no time.
 */
fun foregroundTimes(
    events: List<UsageEvent>,
    startOfDay: Long,
    now: Long,
    excluded: Set<String>,
): Map<String, Long> {
    val timeline = ForegroundTimeline(startOfDay, now, excluded)
    // sortedBy is stable, so events with equal timestamps keep the log's order.
    events
        .distinct()
        .sortedBy { it.timestamp }
        .takeWhile { it.timestamp <= now }
        .forEach(timeline::feed)
    return timeline.finish()
}

/** Walks one day's events in time order, crediting each finished session. See [foregroundTimes]. */
private class ForegroundTimeline(
    private val startOfDay: Long,
    private val now: Long,
    private val excluded: Set<String>,
) {
    private val totals = mutableMapOf<String, Long>()

    /** The resume of the activity in front, if any. */
    private var open: UsageEvent? = null
    private var frontKnown = false

    fun feed(event: UsageEvent) {
        when (event.type) {
            UsageEventType.ActivityResumed -> onResumed(event)
            UsageEventType.ActivityPaused -> onPaused(event)
            UsageEventType.ScreenNonInteractive -> close(event.timestamp)
            UsageEventType.KeyguardShown, UsageEventType.KeyguardHidden -> return
        }
        frontKnown = true
    }

    fun finish(): Map<String, Long> {
        close(now)
        return totals
    }

    private fun onResumed(event: UsageEvent) {
        if (open?.sameActivity(event) == true) return
        close(event.timestamp)
        open = event
    }

    private fun onPaused(event: UsageEvent) {
        when {
            open?.sameActivity(event) == true -> close(event.timestamp)
            !frontKnown -> credit(event.packageName, startOfDay, event.timestamp)
        }
    }

    private fun close(at: Long) {
        open?.let { credit(it.packageName, it.timestamp, at) }
        open = null
    }

    private fun credit(
        packageName: String,
        from: Long,
        to: Long,
    ) {
        val start = maxOf(from, startOfDay)
        val end = minOf(to, now)
        if (packageName !in excluded && end > start) totals.merge(packageName, end - start, Long::plus)
    }

    private fun UsageEvent.sameActivity(other: UsageEvent): Boolean =
        packageName == other.packageName && className == other.className
}

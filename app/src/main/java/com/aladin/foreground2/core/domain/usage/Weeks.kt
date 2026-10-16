package com.aladin.foreground2.core.domain.usage

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/** One segment of a day's stacked screen-time column: a top app, or every other app together (null). */
data class Segment(
    val packageName: String?,
    val millis: Long,
)

/** A Monday-Sunday week. [days] has 7 entries; null where nothing was recorded (or the day hasn't come yet). */
data class Week(
    val start: LocalDate,
    val days: List<DailyUsage?>,
    /** The week's most-used apps (up to [TOP_APPS]), most used first; every other app is "Other". */
    val topApps: List<String>,
) {
    val dates: List<LocalDate> get() = List(DAYS) { start.plusDays(it.toLong()) }
    val hasData: Boolean get() = days.any { it != null }
    private val recorded: List<DailyUsage> get() = days.filterNotNull()

    /** Average over the days with data, or null for an empty week. */
    val averageMillis: Long? get() =
        recorded.takeIf { it.isNotEmpty() }?.let { days ->
            days.sumOf { it.totalMillis } /
                days.size
        }
    val averageUnlocks: Int? get() =
        recorded.takeIf { it.isNotEmpty() }?.let { days ->
            days.sumOf { it.unlocks } /
                days.size
        }

    fun day(date: LocalDate): DailyUsage? = days.getOrNull(start.until(date).days)

    /** [day]'s stacked column, bottom up: the top apps in rank order, then Other. Empty segments are left out. */
    fun segments(day: DailyUsage): List<Segment> {
        val byApp = day.apps.associate { it.packageName to it.foregroundMillis }
        val top = topApps.map { Segment(it, byApp[it] ?: 0L) }
        val other = day.totalMillis - top.sumOf { it.millis }
        return (top + Segment(null, other)).filter { it.millis > 0 }
    }

    /** Week total per top app, then Other, for the legend. */
    fun totals(): List<Segment> {
        val all = recorded.flatMap { it.apps }.groupBy({ it.packageName }, { it.foregroundMillis })
        val top = topApps.map { Segment(it, all[it].orEmpty().sum()) }
        val other = recorded.sumOf { it.totalMillis } - top.sumOf { it.millis }
        return (top + Segment(null, other)).filter { it.millis > 0 }
    }

    companion object {
        const val DAYS = 7
        const val TOP_APPS = 5
    }
}

/** The Monday on or before [date]. */
fun weekStart(date: LocalDate): LocalDate = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

/**
 * Every week from the earliest one with data up to [today]'s, oldest first, so the last is the current week. Days
 * after [today] are always empty.
 */
fun buildWeeks(
    days: List<DailyUsage>,
    today: LocalDate,
): List<Week> {
    val byDate = days.filter { it.date <= today }.associateBy { it.date }
    val current = weekStart(today)
    var start = byDate.keys.minOrNull()?.let(::weekStart) ?: current
    return buildList {
        while (start <= current) {
            val week = List(Week.DAYS) { byDate[start.plusDays(it.toLong())] }
            add(Week(start, week, topApps(week.filterNotNull())))
            start = start.plusWeeks(1)
        }
    }
}

private fun topApps(days: List<DailyUsage>): List<String> =
    days
        .flatMap { it.apps }
        .groupBy({ it.packageName }, { it.foregroundMillis })
        .mapValues { it.value.sum() }
        .filterValues { it > 0 }
        .entries
        .sortedWith(compareByDescending<Map.Entry<String, Long>> { it.value }.thenBy { it.key })
        .take(Week.TOP_APPS)
        .map { it.key }

/**
 * A colour slot (0 until [slots]) for every app that is ever in a week's top apps, the same on every week. Apps are
 * assigned in order of first appearance (oldest week first, by rank), each taking the next slot in rotation not
 * already used by the other top apps of that week. Earlier assignments never change as newer weeks are added. With
 * more distinct top apps than slots, two apps can share a slot, but never within the week they first appear in.
 */
fun colourSlots(
    weeks: List<Week>,
    slots: Int,
): Map<String, Int> {
    val slotOf = LinkedHashMap<String, Int>()
    weeks.forEach { week ->
        week.topApps.forEach { app ->
            if (app !in slotOf) {
                val used = week.topApps.mapNotNull { slotOf[it] }.toSet()
                val next = slotOf.size % slots
                slotOf[app] = (0 until slots).map { (next + it) % slots }.firstOrNull { it !in used } ?: next
            }
        }
    }
    return slotOf
}

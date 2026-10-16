package com.aladin.foreground2.core.domain.usage

import java.time.LocalDate
import java.time.ZoneId

/**
 * Finished days in the last [lookbackDays] (before [today]) that aren't [saved] yet and whose whole day is still in
 * the system's usage log, i.e. start at or after [earliestEventMillis] (the day holding the oldest event may have lost
 * its morning, so it is skipped). Oldest first.
 */
fun missingDays(
    today: LocalDate,
    saved: Set<LocalDate>,
    earliestEventMillis: Long?,
    zone: ZoneId,
    lookbackDays: Int,
): List<LocalDate> {
    if (earliestEventMillis == null) return emptyList()
    return (lookbackDays downTo 1)
        .map { today.minusDays(it.toLong()) }
        .filter { it !in saved && startOfDayMillis(it, zone) >= earliestEventMillis }
}

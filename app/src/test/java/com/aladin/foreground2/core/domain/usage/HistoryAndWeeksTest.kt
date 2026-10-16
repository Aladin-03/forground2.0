package com.aladin.foreground2.core.domain.usage

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.ZoneId

private const val MINUTE = 60_000L
private const val HOUR = 60 * MINUTE
private val ZONE = ZoneId.of("Asia/Kolkata")

// A Friday.
private val TODAY = LocalDate.of(2026, 10, 16)

private fun day(
    date: LocalDate,
    vararg apps: Pair<String, Long>,
    unlocks: Int = 0,
) = DailyUsage(
    date,
    List(DailyUsage.HOURS) { if (it == 0) unlocks else 0 },
    apps.map { AppUsage(it.first, it.second) },
)

class MissingDaysTest {
    @Test
    fun `lists unsaved finished days fully inside the log, oldest first`() {
        // The log starts mid-morning 5 days ago, so that day is partial and skipped.
        val logStart = startOfDayMillis(TODAY.minusDays(5), ZONE) + 9 * HOUR
        val saved = setOf(TODAY.minusDays(3))
        assertEquals(
            listOf(TODAY.minusDays(4), TODAY.minusDays(2), TODAY.minusDays(1)),
            missingDays(TODAY, saved, logStart, ZONE, lookbackDays = 30),
        )
    }

    @Test
    fun `a day starting exactly at the oldest event counts as fully logged`() {
        val logStart = startOfDayMillis(TODAY.minusDays(2), ZONE)
        assertEquals(
            listOf(TODAY.minusDays(2), TODAY.minusDays(1)),
            missingDays(TODAY, emptySet(), logStart, ZONE, lookbackDays = 30),
        )
    }

    @Test
    fun `nothing without a usage log, and never today`() {
        assertEquals(emptyList<LocalDate>(), missingDays(TODAY, emptySet(), null, ZONE, lookbackDays = 30))
        val all = missingDays(TODAY, emptySet(), 0L, ZONE, lookbackDays = 3)
        assertEquals(listOf(TODAY.minusDays(3), TODAY.minusDays(2), TODAY.minusDays(1)), all)
    }
}

class WeeksTest {
    @Test
    fun `weeks run Monday to Sunday from the earliest data to the current week`() {
        val weeks = buildWeeks(listOf(day(LocalDate.of(2026, 9, 30), "a" to MINUTE), day(TODAY)), TODAY)
        assertEquals(
            listOf(LocalDate.of(2026, 9, 28), LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 12)),
            weeks.map { it.start },
        )
        // The middle week has no data: it is still a page, shown as empty.
        assertFalse(weeks[1].hasData)
        assertTrue(weeks[2].hasData)
        assertEquals(TODAY, weeks[2].dates[4])
    }

    @Test
    fun `with only today there is one week`() {
        assertEquals(listOf(LocalDate.of(2026, 10, 12)), buildWeeks(listOf(day(TODAY)), TODAY).map { it.start })
    }

    @Test
    fun `days after today are empty and averages use only days with data`() {
        val week =
            buildWeeks(
                listOf(
                    day(TODAY.minusDays(1), "a" to 2 * HOUR, unlocks = 30),
                    day(TODAY, "a" to HOUR, unlocks = 10),
                    // A stray future row is ignored.
                    day(TODAY.plusDays(1), "a" to 9 * HOUR, unlocks = 99),
                ),
                TODAY,
            ).last()
        assertNull(week.day(TODAY.plusDays(1)))
        assertEquals(90 * MINUTE, week.averageMillis)
        assertEquals(20, week.averageUnlocks)
    }

    @Test
    fun `top five apps by week total, the rest stacked as Other`() {
        val monday = LocalDate.of(2026, 10, 12)
        val week =
            buildWeeks(
                listOf(
                    day(monday, "a" to 60 * MINUTE, "b" to 50 * MINUTE, "c" to 40 * MINUTE, "small" to 5 * MINUTE),
                    day(monday.plusDays(1), "d" to 30 * MINUTE, "e" to 20 * MINUTE, "f" to 10 * MINUTE),
                ),
                TODAY,
            ).last()
        assertEquals(listOf("a", "b", "c", "d", "e"), week.topApps)
        assertEquals(
            listOf(
                Segment("a", 60 * MINUTE),
                Segment("b", 50 * MINUTE),
                Segment("c", 40 * MINUTE),
                Segment(null, 5 * MINUTE),
            ),
            week.segments(week.day(monday)!!),
        )
        assertEquals(Segment(null, 15 * MINUTE), week.totals().last())
    }

    @Test
    fun `an app keeps its colour slot on every week`() {
        val w1 = LocalDate.of(2026, 9, 28)
        val w2 = w1.plusWeeks(1)
        val weeks =
            buildWeeks(
                listOf(
                    day(w1, "a" to 3 * HOUR, "b" to 2 * HOUR, "c" to HOUR),
                    // b and a swap ranks, d is new.
                    day(w2, "d" to 4 * HOUR, "b" to 3 * HOUR, "a" to HOUR),
                ),
                w2.plusDays(6),
            )
        val slots = colourSlots(weeks, slots = 10)
        assertEquals(mapOf("a" to 0, "b" to 1, "c" to 2, "d" to 3), slots)
        // Adding a later week never changes earlier assignments.
        val later = buildWeeks(weeks.flatMap { it.days.filterNotNull() } + day(w2.plusWeeks(1), "z" to HOUR), TODAY)
        assertEquals(slots, colourSlots(later, slots = 10).filterKeys { it in slots })
    }

    @Test
    fun `apps sharing a week never share a slot when they first appear`() {
        val start = LocalDate.of(2026, 9, 7)
        // 3 slots, weeks of 2-3 apps, more apps than slots in total.
        val days =
            listOf(
                day(start, "a" to 3 * HOUR, "b" to 2 * HOUR, "c" to HOUR),
                day(start.plusWeeks(1), "d" to 2 * HOUR, "a" to HOUR),
            )
        val slots = colourSlots(buildWeeks(days, start.plusWeeks(1)), slots = 3)
        assertNotEquals(slots["d"], slots["a"])
    }
}

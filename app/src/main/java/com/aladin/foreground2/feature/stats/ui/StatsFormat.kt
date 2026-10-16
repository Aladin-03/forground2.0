package com.aladin.foreground2.feature.stats.ui

import android.content.Context
import android.text.format.DateFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle

private fun Context.formatter(skeleton: String): DateTimeFormatter {
    val locale = resources.configuration.locales[0]
    return DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, skeleton), locale)
}

/** e.g. "13 Oct". */
fun formatDayMonth(
    context: Context,
    date: LocalDate,
): String = context.formatter("dMMM").format(date)

/** e.g. "Thursday 16 October". */
fun formatFullDay(
    context: Context,
    date: LocalDate,
): String = context.formatter("EEEEdMMMM").format(date)

/** The weekday's initial, e.g. "T". */
fun formatWeekdayLetter(
    context: Context,
    date: LocalDate,
): String = date.dayOfWeek.getDisplayName(TextStyle.NARROW, context.resources.configuration.locales[0])

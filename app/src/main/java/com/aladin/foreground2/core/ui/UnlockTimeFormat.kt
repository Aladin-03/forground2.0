package com.aladin.foreground2.core.ui

import android.content.Context
import android.text.format.DateFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** [millis] (epoch ms) as a short local date and time, e.g. "Fri 17 Oct, 14:30", honouring the 12/24 h setting. */
fun formatUnlockTime(
    context: Context,
    millis: Long,
): String {
    val locale = context.resources.configuration.locales[0]
    val skeleton = if (DateFormat.is24HourFormat(context)) "EEEdMMMHHmm" else "EEEdMMMhmma"
    val formatter = DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, skeleton), locale)
    return formatter.format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))
}

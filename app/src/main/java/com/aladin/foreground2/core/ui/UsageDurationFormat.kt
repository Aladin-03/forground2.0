package com.aladin.foreground2.core.ui

import android.content.Context
import com.aladin.foreground2.R
import com.aladin.foreground2.core.domain.usage.UsageDuration

/** A usage time as "3h 7m", "7m" or "<1m". Plain function so Glance widgets can use it too. */
fun formatUsageDuration(
    context: Context,
    millis: Long,
): String =
    when (val duration = UsageDuration.of(millis)) {
        is UsageDuration.Hours -> context.getString(R.string.stats_duration_hours, duration.hours, duration.minutes)
        is UsageDuration.Minutes -> context.getString(R.string.stats_duration_minutes, duration.minutes)
        UsageDuration.UnderMinute -> context.getString(R.string.stats_duration_under_minute)
    }

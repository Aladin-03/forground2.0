package com.aladin.foreground2.service.detection

import com.aladin.foreground2.core.domain.ZoneProvider
import com.aladin.foreground2.core.domain.usage.localDate
import java.time.LocalDate

/** Tells, poll by poll, when the local date has moved on since the previous poll. */
class DayChangeWatcher(
    private val zones: ZoneProvider,
) {
    private var lastDay: LocalDate? = null

    /** True on the first poll of a new local day. The very first poll only sets the day (service start covers it). */
    fun dayChanged(nowMillis: Long): Boolean {
        val day = localDate(nowMillis, zones.zone())
        val previous = lastDay
        lastDay = day
        return previous != null && day != previous
    }
}

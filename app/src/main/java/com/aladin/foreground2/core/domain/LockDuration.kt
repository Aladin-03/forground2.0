package com.aladin.foreground2.core.domain

/** How long to lock an app, as picked on the duration wheels (spec rule 6). */
data class LockDuration(
    val days: Int,
    val hours: Int,
    val minutes: Int,
) {
    init {
        require(days in DAYS) { "days=$days" }
        require(hours in HOURS) { "hours=$hours" }
        require(minutes in MINUTES) { "minutes=$minutes" }
    }

    /** Start is disabled at zero. */
    val isZero: Boolean get() = days == 0 && hours == 0 && minutes == 0

    val millis: Long get() = ((days * HOURS_PER_DAY + hours) * MINUTES_PER_HOUR + minutes) * MILLIS_PER_MINUTE

    companion object {
        val DAYS = 0..30
        val HOURS = 0..23
        val MINUTES = 0..59
        val Zero = LockDuration(0, 0, 0)

        private const val HOURS_PER_DAY = 24L
        private const val MINUTES_PER_HOUR = 60L
        private const val MILLIS_PER_MINUTE = 60_000L
    }
}

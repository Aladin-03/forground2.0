package com.aladin.foreground2.core.domain.usage

/** A usage time as the stats screen shows it, rounded down to whole minutes. */
sealed interface UsageDuration {
    /** An hour or more: "Xh Ym". */
    data class Hours(
        val hours: Int,
        val minutes: Int,
    ) : UsageDuration

    /** Under an hour: "Ym" (zero for no use at all). */
    data class Minutes(
        val minutes: Int,
    ) : UsageDuration

    /** Some use, but under a minute: "<1m". */
    data object UnderMinute : UsageDuration

    companion object {
        private const val MINUTE_MILLIS = 60_000L
        private const val MINUTES_PER_HOUR = 60

        fun of(millis: Long): UsageDuration {
            val totalMinutes = (millis / MINUTE_MILLIS).toInt()
            return when {
                totalMinutes >= MINUTES_PER_HOUR ->
                    Hours(totalMinutes / MINUTES_PER_HOUR, totalMinutes % MINUTES_PER_HOUR)
                totalMinutes == 0 && millis > 0 -> UnderMinute
                else -> Minutes(totalMinutes)
            }
        }
    }
}

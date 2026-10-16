package com.aladin.foreground2.core.domain

/** The lock screen's "Unlocks in ..." countdown (rule 7), split into the units it shows. */
sealed interface LockRemaining {
    /** A day or more left: "Xd Yh Zm". */
    data class Days(
        val days: Int,
        val hours: Int,
        val minutes: Int,
    ) : LockRemaining

    /** An hour to a day left: "Yh Zm". */
    data class Hours(
        val hours: Int,
        val minutes: Int,
    ) : LockRemaining

    /** Under an hour left: "Zm Ss", ticking every second. */
    data class Minutes(
        val minutes: Int,
        val seconds: Int,
    ) : LockRemaining

    /** The lock has ended. */
    data object Ended : LockRemaining

    companion object {
        private const val SECONDS_PER_MINUTE = 60
        private const val MINUTES_PER_HOUR = 60
        private const val MINUTES_PER_DAY = 24 * MINUTES_PER_HOUR

        /**
         * [secondsLeft] is whole seconds, rounded up (see [Countdown]). Above an hour the minutes round up too, so
         * the screen never shows less time than is left.
         */
        fun of(secondsLeft: Int): LockRemaining {
            val minutes = (secondsLeft + SECONDS_PER_MINUTE - 1) / SECONDS_PER_MINUTE
            val days = minutes / MINUTES_PER_DAY
            val hours = minutes % MINUTES_PER_DAY / MINUTES_PER_HOUR
            return when {
                secondsLeft <= 0 -> Ended
                secondsLeft < SECONDS_PER_MINUTE * MINUTES_PER_HOUR ->
                    Minutes(secondsLeft / SECONDS_PER_MINUTE, secondsLeft % SECONDS_PER_MINUTE)
                days > 0 -> Days(days, hours, minutes % MINUTES_PER_HOUR)
                else -> Hours(hours, minutes % MINUTES_PER_HOUR)
            }
        }
    }
}

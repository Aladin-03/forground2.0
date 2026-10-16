package com.aladin.foreground2.widget

/**
 * Lets each widget refresh at most once per [minIntervalMillis], except that the first refresh of a new day always
 * goes through, so the numbers reset at midnight even if the last refresh was seconds before it.
 */
class RefreshThrottle(
    private val minIntervalMillis: Long,
) {
    private class Last(
        val at: Long,
        val startOfDay: Long,
    )

    private val last = mutableMapOf<Int, Last>()

    /** Whether [appWidgetId] may refresh now; if so, records it as refreshed. */
    fun tryAcquire(
        appWidgetId: Int,
        now: Long,
        startOfDay: Long,
    ): Boolean {
        val previous = last[appWidgetId]
        val allowed = previous == null || previous.startOfDay != startOfDay || now - previous.at >= minIntervalMillis
        if (allowed) last[appWidgetId] = Last(now, startOfDay)
        return allowed
    }

    /** When [appWidgetId] may next refresh (now or earlier if it may already). */
    fun nextAllowedAt(appWidgetId: Int): Long = last[appWidgetId]?.let { it.at + minIntervalMillis } ?: Long.MIN_VALUE

    /** Forgets widgets that are no longer placed. */
    fun retain(appWidgetIds: Set<Int>) {
        last.keys.retainAll(appWidgetIds)
    }
}

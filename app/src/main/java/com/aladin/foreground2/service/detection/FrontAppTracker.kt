package com.aladin.foreground2.service.detection

/**
 * Turns batches of resumed events into front-app changes. Our own package is ignored entirely, so our overlay
 * coming to the front never counts as leaving the watched app below it. The launcher and system UI are ordinary
 * apps here, which is how leaving a watched app for Home is noticed.
 */
class FrontAppTracker(
    private val ownPackage: String,
) {
    var current: String? = null
        private set

    private var lastEventAt = Long.MIN_VALUE
    private var initialised = false

    /**
     * Feeds one poll's events (windows may overlap; events already seen are skipped). Every switch inside the batch
     * is reported in order, so a quick A -> B -> A between two polls still yields a leave and a fresh entry. The first
     * batch only establishes the current app, reported once at [now].
     */
    fun consume(
        events: List<ResumedEvent>,
        now: Long,
    ): List<FrontAppChanged> {
        val fresh = events.filter { it.timestamp > lastEventAt }.sortedBy { it.timestamp }
        fresh.lastOrNull()?.let { lastEventAt = it.timestamp }
        val relevant = fresh.filter { it.packageName != ownPackage }

        if (!initialised) {
            initialised = true
            val last = relevant.lastOrNull()
            current = last?.packageName
            return listOfNotNull(last?.let { FrontAppChanged(it.packageName, now) })
        }
        return relevant.mapNotNull { event ->
            if (event.packageName == current) {
                null
            } else {
                current = event.packageName
                FrontAppChanged(event.packageName, event.timestamp)
            }
        }
    }
}

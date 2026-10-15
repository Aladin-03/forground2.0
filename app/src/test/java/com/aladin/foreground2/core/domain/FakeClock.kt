package com.aladin.foreground2.core.domain

/** Manually driven [Clock] for tests. */
class FakeClock(
    var now: Long = 0L,
) : Clock {
    override fun nowMillis(): Long = now

    fun advanceBy(millis: Long) {
        now += millis
    }
}

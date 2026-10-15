package com.aladin.foreground2.core.domain

/** Source of wall-clock time. Inject this instead of calling System.currentTimeMillis() so logic is testable. */
fun interface Clock {
    fun nowMillis(): Long
}

/** Real clock, bound in di/. */
object SystemClock : Clock {
    override fun nowMillis(): Long = System.currentTimeMillis()
}

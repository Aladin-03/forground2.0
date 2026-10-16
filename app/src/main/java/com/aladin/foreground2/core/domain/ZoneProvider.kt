package com.aladin.foreground2.core.domain

import java.time.ZoneId

/** The device's current time zone. Injected (like [Clock]) so day boundaries are testable in any zone. */
fun interface ZoneProvider {
    fun zone(): ZoneId
}

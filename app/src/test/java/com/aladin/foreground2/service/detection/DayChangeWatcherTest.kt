package com.aladin.foreground2.service.detection

import com.aladin.foreground2.core.domain.ZoneProvider
import com.aladin.foreground2.core.domain.usage.startOfDayMillis
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.ZoneId

class DayChangeWatcherTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val midnight = startOfDayMillis(LocalDate.of(2026, 10, 17), zone)

    @Test
    fun `fires once, on the first poll after local midnight`() {
        val watcher = DayChangeWatcher { zone }
        assertFalse(watcher.dayChanged(midnight - 1_000))
        assertFalse(watcher.dayChanged(midnight - 1))
        assertTrue(watcher.dayChanged(midnight))
        assertFalse(watcher.dayChanged(midnight + 500))
    }

    @Test
    fun `the first poll only sets the day`() {
        assertFalse(DayChangeWatcher { zone }.dayChanged(midnight + 1))
    }

    @Test
    fun `follows the device zone`() {
        var current = ZoneId.of("UTC")
        val watcher = DayChangeWatcher(ZoneProvider { current })
        // 23:00 on the 16th in UTC is already 04:30 on the 17th in India.
        val at = startOfDayMillis(LocalDate.of(2026, 10, 17), current) - 3_600_000
        assertFalse(watcher.dayChanged(at))
        current = zone
        assertTrue(watcher.dayChanged(at))
    }
}

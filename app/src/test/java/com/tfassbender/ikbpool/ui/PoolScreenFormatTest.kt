package com.tfassbender.ikbpool.ui

import com.tfassbender.ikbpool.data.model.OpeningHours
import com.tfassbender.ikbpool.data.model.OpeningWindow
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime

class PoolScreenFormatTest {

    private val mondayHours = OpeningHours(
        mapOf(
            DayOfWeek.MONDAY to OpeningWindow(LocalTime.of(6, 30), LocalTime.of(22, 0)),
            DayOfWeek.TUESDAY to null,
        )
    )

    @Test fun `open right now reports closing time`() {
        val now = LocalDateTime.of(2026, 5, 25, 12, 0) // Monday noon
        assertEquals("Geöffnet bis 22:00 Uhr", formatOpenStatus(mondayHours, now))
    }

    @Test fun `before opening on a working day reports opening time`() {
        val now = LocalDateTime.of(2026, 5, 25, 5, 0) // Monday 05:00
        assertEquals("Öffnet heute um 06:30 Uhr", formatOpenStatus(mondayHours, now))
    }

    @Test fun `after closing reports closed`() {
        val now = LocalDateTime.of(2026, 5, 25, 23, 30) // Monday 23:30
        assertEquals("Heute geschlossen", formatOpenStatus(mondayHours, now))
    }

    @Test fun `closed day reports closed`() {
        val now = LocalDateTime.of(2026, 5, 26, 12, 0) // Tuesday noon (null window)
        assertEquals("Heute geschlossen", formatOpenStatus(mondayHours, now))
    }

    @Test fun `null hours reports unknown`() {
        val now = LocalDateTime.of(2026, 5, 25, 12, 0)
        assertEquals("Öffnungszeiten unbekannt", formatOpenStatus(null, now))
    }
}

package com.tfassbender.ikbpool.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime

class OpeningHoursTest {

    private val hours = OpeningHours(
        mapOf(
            DayOfWeek.MONDAY to OpeningWindow(LocalTime.of(6, 30), LocalTime.of(22, 0)),
            DayOfWeek.TUESDAY to null,
            DayOfWeek.WEDNESDAY to OpeningWindow(LocalTime.of(6, 30), LocalTime.of(22, 0)),
        )
    )

    @Test fun `closed when day has null window`() {
        assertFalse(hours.isOpenAt(LocalDateTime.of(2026, 5, 26, 12, 0))) // Tuesday
    }

    @Test fun `open at exact opening time`() {
        assertTrue(hours.isOpenAt(LocalDateTime.of(2026, 5, 25, 6, 30))) // Monday
    }

    @Test fun `closed at exact closing time`() {
        assertFalse(hours.isOpenAt(LocalDateTime.of(2026, 5, 25, 22, 0)))
    }

    @Test fun `closed before opening`() {
        assertFalse(hours.isOpenAt(LocalDateTime.of(2026, 5, 25, 6, 29)))
    }

    @Test fun `windowFor returns null for missing day`() {
        assertEquals(null, hours.windowFor(DayOfWeek.SUNDAY))
    }
}

package com.tfassbender.ikbpool.domain

import com.tfassbender.ikbpool.data.model.LaneReservations
import com.tfassbender.ikbpool.data.model.Occupancy
import com.tfassbender.ikbpool.data.model.OpeningHours
import com.tfassbender.ikbpool.data.model.OpeningWindow
import com.tfassbender.ikbpool.data.model.PoolStatus
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime

class RecommendationEngineTest {

    // Monday 12:00 — open per fixture below
    private val noon = LocalDateTime.of(2026, 5, 25, 12, 0)

    // Monday-only schedule, 06:30–22:00 → noon falls inside
    private val openHours = OpeningHours(
        mapOf(DayOfWeek.MONDAY to OpeningWindow(LocalTime.of(6, 30), LocalTime.of(22, 0)))
    )
    private val closedHours = OpeningHours(mapOf(DayOfWeek.MONDAY to null))

    private fun status(
        hours: OpeningHours? = openHours,
        occupancy: Occupancy? = Occupancy(20),
        reservations: LaneReservations? = LaneReservations(false, false, false),
    ) = PoolStatus(
        openingHours = hours,
        occupancy = occupancy,
        reservations = reservations,
        fetchedAt = Instant.parse("2026-05-25T12:00:00Z"),
        warnings = emptyList(),
    )

    private val engine = RecommendationEngine()

    @Test fun `SUPER when open and occupancy below 18`() {
        assertEquals(Recommendation.SUPER, engine.evaluate(status(occupancy = Occupancy(0)), noon))
        assertEquals(Recommendation.SUPER, engine.evaluate(status(occupancy = Occupancy(17)), noon))
    }

    @Test fun `GUT at boundary 18 percent`() {
        assertEquals(Recommendation.GUT, engine.evaluate(status(occupancy = Occupancy(18)), noon))
    }

    @Test fun `GUT when occupancy below 30`() {
        assertEquals(Recommendation.GUT, engine.evaluate(status(occupancy = Occupancy(29)), noon))
    }

    @Test fun `SCHLECHT in borderline 30-40 without lane reservation`() {
        assertEquals(
            Recommendation.SCHLECHT,
            engine.evaluate(status(occupancy = Occupancy(35), reservations = LaneReservations(true, true, false)), noon)
        )
    }

    @Test fun `OKAY at 35 percent with all three lower lanes reserved`() {
        assertEquals(
            Recommendation.OKAY,
            engine.evaluate(status(occupancy = Occupancy(35), reservations = LaneReservations(true, true, true)), noon)
        )
    }

    @Test fun `OKAY at boundary 40 percent with lanes reserved`() {
        assertEquals(
            Recommendation.OKAY,
            engine.evaluate(status(occupancy = Occupancy(40), reservations = LaneReservations(true, true, true)), noon)
        )
    }

    @Test fun `SCHLECHT when occupancy above 40 regardless of lane reservation`() {
        assertEquals(
            Recommendation.SCHLECHT,
            engine.evaluate(status(occupancy = Occupancy(41), reservations = LaneReservations(true, true, true)), noon)
        )
    }

    @Test fun `SCHLECHT when pool closed per schedule even with low occupancy`() {
        assertEquals(
            Recommendation.SCHLECHT,
            engine.evaluate(status(hours = closedHours, occupancy = Occupancy(5)), noon)
        )
    }

    @Test fun `closed beats missing occupancy`() {
        assertEquals(
            Recommendation.SCHLECHT,
            engine.evaluate(status(hours = closedHours, occupancy = null), noon)
        )
    }

    @Test fun `UNBEKANNT when occupancy missing and open`() {
        assertEquals(
            Recommendation.UNBEKANNT,
            engine.evaluate(status(occupancy = null), noon)
        )
    }

    @Test fun `UNBEKANNT when both hours and occupancy missing`() {
        assertEquals(
            Recommendation.UNBEKANNT,
            engine.evaluate(status(hours = null, occupancy = null), noon)
        )
    }

    @Test fun `applies tiers when hours missing but occupancy known`() {
        // Optimistic: trust occupancy even without schedule confirmation
        assertEquals(
            Recommendation.SUPER,
            engine.evaluate(status(hours = null, occupancy = Occupancy(10)), noon)
        )
    }

    @Test fun `SCHLECHT in 30-40 range when reservations data missing`() {
        // Conservative: don't promote to OKAY without evidence
        assertEquals(
            Recommendation.SCHLECHT,
            engine.evaluate(status(occupancy = Occupancy(35), reservations = null), noon)
        )
    }

    @Test fun `clock provides default now when not passed`() {
        var clockCalls = 0
        val frozen = LocalDateTime.of(2026, 5, 25, 12, 0)
        val e = RecommendationEngine(clock = { clockCalls++; frozen })
        assertEquals(Recommendation.SUPER, e.evaluate(status(occupancy = Occupancy(10))))
        assertEquals(1, clockCalls)
    }

    @Test fun `messages match spec wording`() {
        assertEquals("Super Zeit zum Schwimmen", Recommendation.SUPER.message)
        assertEquals("Gut geeignet zum Schwimmen", Recommendation.GUT.message)
        assertEquals("Noch akzeptabel trotz Reservierung", Recommendation.OKAY.message)
        assertEquals("Aktuell nicht empfehlenswert", Recommendation.SCHLECHT.message)
    }
}

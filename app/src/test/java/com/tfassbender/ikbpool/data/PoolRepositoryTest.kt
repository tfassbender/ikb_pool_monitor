package com.tfassbender.ikbpool.data

import com.tfassbender.ikbpool.data.model.LaneReservations
import com.tfassbender.ikbpool.data.model.Occupancy
import com.tfassbender.ikbpool.data.model.OpeningHours
import com.tfassbender.ikbpool.data.model.OpeningWindow
import com.tfassbender.ikbpool.data.model.Source
import com.tfassbender.ikbpool.data.source.PoolDataSource
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime

class PoolRepositoryTest {

    private val sampleHours = OpeningHours(
        mapOf(DayOfWeek.MONDAY to OpeningWindow(LocalTime.of(6, 30), LocalTime.of(22, 0)))
    )
    private val sampleOccupancy = Occupancy(14)
    private val sampleReservations = LaneReservations(true, true, true)

    private class FakeSource<T>(private val value: T?, private val error: Throwable? = null) : PoolDataSource<T> {
        override suspend fun fetch(): T {
            if (error != null) throw error
            @Suppress("UNCHECKED_CAST")
            return value as T
        }
    }

    @Test fun `aggregates all successful sources into PoolStatus`() = runTest {
        val repo = PoolRepository(
            openingHours = FakeSource(sampleHours),
            occupancy = FakeSource(sampleOccupancy),
            reservations = FakeSource(sampleReservations),
            clock = { Instant.parse("2026-05-23T10:00:00Z") },
        )

        val status = repo.load()

        assertEquals(sampleHours, status.openingHours)
        assertEquals(sampleOccupancy, status.occupancy)
        assertEquals(sampleReservations, status.reservations)
        assertEquals(Instant.parse("2026-05-23T10:00:00Z"), status.fetchedAt)
        assertTrue(status.warnings.isEmpty())
    }

    @Test fun `occupancy null (pool reported closed) is success, not warning`() = runTest {
        val repo = PoolRepository(
            openingHours = FakeSource(sampleHours),
            occupancy = FakeSource<Occupancy?>(null),
            reservations = FakeSource(sampleReservations),
        )

        val status = repo.load()

        assertNull(status.occupancy)
        assertTrue(status.warnings.isEmpty())
    }

    @Test fun `failing source becomes a warning and other fields remain`() = runTest {
        val repo = PoolRepository(
            openingHours = FakeSource(sampleHours),
            occupancy = FakeSource(null, IOException("HTTP 500")),
            reservations = FakeSource(sampleReservations),
        )

        val status = repo.load()

        assertEquals(sampleHours, status.openingHours)
        assertNull(status.occupancy)
        assertEquals(sampleReservations, status.reservations)
        assertEquals(1, status.warnings.size)
        assertEquals(Source.OCCUPANCY, status.warnings[0].source)
        assertTrue(status.warnings[0].message.contains("500"))
    }

    @Test fun `all sources failing produces 3 warnings and null fields`() = runTest {
        val repo = PoolRepository(
            openingHours = FakeSource(null, IOException("boom1")),
            occupancy = FakeSource(null, IOException("boom2")),
            reservations = FakeSource(null, IOException("boom3")),
        )

        val status = repo.load()

        assertNull(status.openingHours)
        assertNull(status.occupancy)
        assertNull(status.reservations)
        assertEquals(3, status.warnings.size)
        assertEquals(
            setOf(Source.OPENING_HOURS, Source.OCCUPANCY, Source.RESERVATIONS),
            status.warnings.map { it.source }.toSet(),
        )
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test fun `sources are fetched in parallel`() = runTest {
        // Three sources, each sleeping 1s of virtual time. If parallel, total ~1s; if sequential, ~3s.
        val slow = object : PoolDataSource<OpeningHours> {
            override suspend fun fetch(): OpeningHours { delay(1000); return sampleHours }
        }
        val slowOcc = object : PoolDataSource<Occupancy?> {
            override suspend fun fetch(): Occupancy? { delay(1000); return sampleOccupancy }
        }
        val slowRes = object : PoolDataSource<LaneReservations> {
            override suspend fun fetch(): LaneReservations { delay(1000); return sampleReservations }
        }
        val repo = PoolRepository(slow, slowOcc, slowRes)

        val start = testScheduler.currentTime
        repo.load()
        val elapsed = testScheduler.currentTime - start

        // Parallel: should be ~1000ms, definitely under 2500ms
        assertTrue("Expected parallel fetch (~1s) but took ${elapsed}ms", elapsed < 2500)
    }

    @Test fun `fetchedAt comes from injected clock`() = runTest {
        val instant = Instant.parse("2026-12-25T08:00:00Z")
        val repo = PoolRepository(
            FakeSource(sampleHours), FakeSource(sampleOccupancy), FakeSource(sampleReservations),
            clock = { instant },
        )
        assertEquals(instant, repo.load().fetchedAt)
    }

    @Test fun `warning message falls back to exception class name when message is null`() = runTest {
        val repo = PoolRepository(
            FakeSource(sampleHours),
            FakeSource(null, RuntimeException()),
            FakeSource(sampleReservations),
        )

        val warning = repo.load().warnings.single()

        assertEquals(Source.OCCUPANCY, warning.source)
        assertEquals("RuntimeException", warning.message)
    }
}

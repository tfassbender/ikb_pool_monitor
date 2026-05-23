package com.tfassbender.ikbpool.ui

import com.tfassbender.ikbpool.data.PoolRepository
import com.tfassbender.ikbpool.data.model.LaneReservations
import com.tfassbender.ikbpool.data.model.Occupancy
import com.tfassbender.ikbpool.data.model.OpeningHours
import com.tfassbender.ikbpool.data.model.OpeningWindow
import com.tfassbender.ikbpool.data.source.PoolDataSource
import com.tfassbender.ikbpool.domain.Recommendation
import com.tfassbender.ikbpool.domain.RecommendationEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime

@OptIn(ExperimentalCoroutinesApi::class)
class PoolViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    private val workingHours = OpeningHours(
        mapOf(DayOfWeek.MONDAY to OpeningWindow(LocalTime.of(6, 30), LocalTime.of(22, 0)))
    )
    private val engine = RecommendationEngine(
        clock = { LocalDateTime.of(2026, 5, 25, 12, 0) }
    )

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    private class FakeSource<T>(private val value: T?, private val error: Throwable? = null) : PoolDataSource<T> {
        override suspend fun fetch(): T {
            if (error != null) throw error
            @Suppress("UNCHECKED_CAST")
            return value as T
        }
    }

    private fun repoOf(
        hours: OpeningHours? = workingHours,
        occupancy: Occupancy? = Occupancy(14),
        reservations: LaneReservations? = LaneReservations(false, false, false),
    ): PoolRepository = PoolRepository(
        openingHours = FakeSource(hours ?: throw AssertionError("use error path instead")),
        occupancy = FakeSource(occupancy),
        reservations = FakeSource(reservations ?: throw AssertionError("use error path instead")),
    )

    @Test fun `initial state before any dispatching is loading`() = runTest(dispatcher) {
        val vm = PoolViewModel(repoOf(), engine)
        // Don't run dispatcher: ViewModel just queued a refresh in init
        assertTrue(vm.state.value.isRefreshing)
        assertFalse(vm.state.value.hasLoadedOnce)
        assertNull(vm.state.value.status)
    }

    @Test fun `after init refresh completes, state has status and recommendation`() = runTest(dispatcher) {
        val vm = PoolViewModel(repoOf(occupancy = Occupancy(10)), engine)
        advanceUntilIdle()

        val s = vm.state.value
        assertFalse(s.isRefreshing)
        assertTrue(s.hasLoadedOnce)
        assertNotNull(s.status)
        assertEquals(Recommendation.SUPER, s.recommendation)
        assertNull(s.errorMessage)
    }

    @Test fun `manual refresh re-runs the load`() = runTest(dispatcher) {
        var fetchCount = 0
        val countingSource = object : PoolDataSource<Occupancy?> {
            override suspend fun fetch(): Occupancy { fetchCount++; return Occupancy(20) }
        }
        val repo = PoolRepository(
            openingHours = FakeSource(workingHours),
            occupancy = countingSource,
            reservations = FakeSource(LaneReservations(false, false, false)),
        )
        val vm = PoolViewModel(repo, engine)
        advanceUntilIdle()
        assertEquals(1, fetchCount)

        vm.refresh()
        advanceUntilIdle()
        assertEquals(2, fetchCount)
    }

    @Test fun `concurrent refresh calls collapse — only one fetch`() = runTest(dispatcher) {
        var fetchCount = 0
        val countingSource = object : PoolDataSource<Occupancy?> {
            override suspend fun fetch(): Occupancy { fetchCount++; return Occupancy(20) }
        }
        val repo = PoolRepository(
            openingHours = FakeSource(workingHours),
            occupancy = countingSource,
            reservations = FakeSource(LaneReservations(false, false, false)),
        )
        val vm = PoolViewModel(repo, engine)
        // init kicked off one refresh; calling again while still in flight should no-op
        vm.refresh()
        vm.refresh()
        advanceUntilIdle()
        assertEquals(1, fetchCount)
    }

    @Test fun `repository never throws — partial failures only show as warnings, not errorMessage`() = runTest(dispatcher) {
        // One source fails → repo wraps as SourceWarning, load() succeeds
        val repo = PoolRepository(
            openingHours = FakeSource(workingHours),
            occupancy = FakeSource(null, IOException("HTTP 500")),
            reservations = FakeSource(LaneReservations(false, false, false)),
        )
        val vm = PoolViewModel(repo, engine)
        advanceUntilIdle()

        val s = vm.state.value
        assertNull(s.errorMessage) // not catastrophic
        assertNotNull(s.status)
        assertEquals(1, s.status!!.warnings.size)
    }
}

package com.tfassbender.ikbpool.data

import com.tfassbender.ikbpool.data.model.LaneReservations
import com.tfassbender.ikbpool.data.model.Occupancy
import com.tfassbender.ikbpool.data.model.OpeningHours
import com.tfassbender.ikbpool.data.model.PoolStatus
import com.tfassbender.ikbpool.data.model.Source
import com.tfassbender.ikbpool.data.model.SourceWarning
import com.tfassbender.ikbpool.data.source.PoolDataSource
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.time.Instant

class PoolRepository(
    private val openingHours: PoolDataSource<OpeningHours>,
    private val occupancy: PoolDataSource<Occupancy?>,
    private val reservations: PoolDataSource<LaneReservations>,
    private val clock: () -> Instant = Instant::now,
) {
    suspend fun load(): PoolStatus = coroutineScope {
        val hoursDeferred = async { runCatching { openingHours.fetch() } }
        val occupancyDeferred = async { runCatching { occupancy.fetch() } }
        val reservationsDeferred = async { runCatching { reservations.fetch() } }

        val warnings = mutableListOf<SourceWarning>()
        val hours = hoursDeferred.await().onFailureWarn(warnings, Source.OPENING_HOURS)
        val occ = occupancyDeferred.await().onFailureWarn(warnings, Source.OCCUPANCY)
        val res = reservationsDeferred.await().onFailureWarn(warnings, Source.RESERVATIONS)

        PoolStatus(
            openingHours = hours,
            occupancy = occ,
            reservations = res,
            fetchedAt = clock(),
            warnings = warnings.toList(),
        )
    }

    private fun <T> Result<T>.onFailureWarn(into: MutableList<SourceWarning>, source: Source): T? {
        return fold(
            onSuccess = { it },
            onFailure = {
                into.add(SourceWarning(source, it.message ?: it.javaClass.simpleName))
                null
            }
        )
    }
}

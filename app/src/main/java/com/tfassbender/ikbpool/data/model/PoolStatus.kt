package com.tfassbender.ikbpool.data.model

import java.time.Instant

enum class Source { OPENING_HOURS, OCCUPANCY, RESERVATIONS }

data class SourceWarning(val source: Source, val message: String)

data class PoolStatus(
    val openingHours: OpeningHours?,
    val occupancy: Occupancy?,
    val reservations: LaneReservations?,
    val fetchedAt: Instant,
    val warnings: List<SourceWarning>,
)

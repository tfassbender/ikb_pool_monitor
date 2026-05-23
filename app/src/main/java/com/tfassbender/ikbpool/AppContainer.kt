package com.tfassbender.ikbpool

import com.tfassbender.ikbpool.data.PoolRepository
import com.tfassbender.ikbpool.data.source.OccupancyScraper
import com.tfassbender.ikbpool.data.source.OpeningHoursScraper
import com.tfassbender.ikbpool.data.source.ReservationsScraper
import com.tfassbender.ikbpool.domain.RecommendationEngine
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class AppContainer {
    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(20, TimeUnit.SECONDS)
        .build()

    private val openingHoursScraper = OpeningHoursScraper(http)
    private val occupancyScraper = OccupancyScraper(http)
    private val reservationsScraper = ReservationsScraper(http)

    val repository: PoolRepository = PoolRepository(
        openingHours = openingHoursScraper,
        occupancy = occupancyScraper,
        reservations = reservationsScraper,
    )

    val recommendationEngine: RecommendationEngine = RecommendationEngine()
}

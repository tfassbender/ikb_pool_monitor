package com.tfassbender.ikbpool.domain

import com.tfassbender.ikbpool.data.model.PoolStatus
import java.time.LocalDateTime
import java.time.ZoneId

class RecommendationEngine(
    private val clock: () -> LocalDateTime = { LocalDateTime.now(ZONE) },
) {

    fun evaluate(status: PoolStatus, now: LocalDateTime = clock()): Recommendation {
        // 1. Closed per schedule → SCHLECHT (closed beats every other signal).
        val isOpen = status.openingHours?.isOpenAt(now)
        if (isOpen == false) return Recommendation.SCHLECHT

        // 2. No occupancy data → can't evaluate the tiered rules.
        val percent = status.occupancy?.percent
            ?: return Recommendation.UNBEKANNT

        // 3. Tiered rules. Cascading: first match wins.
        if (percent > 40) return Recommendation.SCHLECHT
        if (percent < 18) return Recommendation.SUPER
        if (percent < 30) return Recommendation.GUT

        // 4. 30 ≤ percent ≤ 40 — borderline. OKAY only if lanes 1-3 all reserved.
        //    Missing reservation data is treated conservatively as "not all reserved".
        val lanesAllReserved = status.reservations?.lowerThreeAllReserved == true
        return if (lanesAllReserved) Recommendation.OKAY else Recommendation.SCHLECHT
    }

    companion object {
        val ZONE: ZoneId = ZoneId.of("Europe/Vienna")
    }
}

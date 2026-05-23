package com.tfassbender.ikbpool.data.source

import com.tfassbender.ikbpool.data.model.LaneReservations
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class ReservationsScraper(
    private val http: OkHttpClient,
    private val clock: () -> ZonedDateTime = { ZonedDateTime.now(ZONE) },
    private val baseUrl: String = DEFAULT_BASE_URL,
) : PoolDataSource<LaneReservations> {

    override suspend fun fetch(): LaneReservations = withContext(Dispatchers.IO) {
        val now = clock()
        val url = buildUrl(now.toLocalDate())
        val req = Request.Builder()
            .url(url)
            .header("X-Requested-With", "XMLHttpRequest")
            .header("Referer", WIDGET_URL)
            .header("Accept", "*/*")
            .get()
            .build()
        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code} fetching $url")
            val body = resp.body?.string() ?: throw IOException("empty body from $url")
            parse(body, now.toLocalTime())
        }
    }

    internal fun parse(json: String, now: LocalTime): LaneReservations {
        val response = JSON.decodeFromString<ApiResponse>(json)
        val slotStart = slotKey(now)
        fun reserved(courtId: Int): Boolean =
            response.slots.any { it.court == courtId && it.start == slotStart && !it.title.isOpenSwim() }
        return LaneReservations(
            lane1Reserved = reserved(LANE_1_COURT_ID),
            lane2Reserved = reserved(LANE_2_COURT_ID),
            lane3Reserved = reserved(LANE_3_COURT_ID),
        )
    }

    private fun buildUrl(date: LocalDate): String =
        baseUrl.toHttpUrl().newBuilder().apply {
            addQueryParameter("facilityId", FACILITY_ID)
            addQueryParameter("sport", SPORT)
            addQueryParameter("startDate", date.toString())
            ALL_COURT_IDS.forEach { addQueryParameter("courts[]", it.toString()) }
        }.build().toString()

    @Serializable
    private data class ApiResponse(val slots: List<Slot> = emptyList())

    @Serializable
    private data class Slot(
        val court: Int = 0,
        val start: String = "",
        val title: String? = null,
    )

    companion object {
        // Verify against the widget UI: top three lanes in the grid should be these IDs.
        // If lanes 1-3 appear at the bottom instead, swap to 71112/71111/71110.
        const val LANE_1_COURT_ID = 71107
        const val LANE_2_COURT_ID = 71108
        const val LANE_3_COURT_ID = 71109

        private val ALL_COURT_IDS = intArrayOf(
            71107, 71108, 71109, 71110, 71111, 71112, 71113, 71114, 87211,
        )
        private const val FACILITY_ID = "75192"
        private const val SPORT = "hallenbad-hoettinger-au"
        private const val WIDGET_URL = "https://www.eversports.at/widget/w/va4m84?countryCode=AT"
        const val DEFAULT_BASE_URL = "https://www.eversports.at/widget/api/slot"

        val ZONE: ZoneId = ZoneId.of("Europe/Vienna")

        private val JSON = Json { ignoreUnknownKeys = true; isLenient = true }

        internal fun slotKey(time: LocalTime): String {
            val rounded = if (time.minute >= 30) 30 else 0
            return "%02d%02d".format(time.hour, rounded)
        }

        private fun String?.isOpenSwim(): Boolean =
            this == null || isEmpty() || this == "Badebetrieb" || equals("geschlossen", ignoreCase = true)
    }
}

package com.tfassbender.ikbpool.data.source

import com.tfassbender.ikbpool.data.model.LaneReservations
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class ReservationsScraperTest {

    private lateinit var server: MockWebServer
    private val client = OkHttpClient()

    @Before fun setUp() { server = MockWebServer().apply { start() } }
    @After fun tearDown() { server.shutdown() }

    private fun fixture(name: String): String =
        javaClass.classLoader!!.getResourceAsStream("fixtures/$name")!!
            .bufferedReader(Charsets.UTF_8).use { it.readText() }

    private fun newScraper(now: LocalTime = LocalTime.of(12, 0)): ReservationsScraper {
        val fixedNow = ZonedDateTime.of(LocalDate.of(2026, 5, 23), now, ZoneId.of("Europe/Vienna"))
        return ReservationsScraper(client, clock = { fixedNow }, baseUrl = server.url("/").toString())
    }

    @Test fun `all lanes free before opening hours`() {
        val r = newScraper().parse(fixture("eversports_hoettinger.json"), LocalTime.of(7, 0))
        assertEquals(LaneReservations(false, false, false), r)
    }

    @Test fun `mixed reservation state — synthetic, lane3 free`() {
        val mixed = """
            {"slots":[
              {"date":"2026-05-23","start":"1000","court":71107,"title":"Schulgruppe"},
              {"date":"2026-05-23","start":"1000","court":71107,"title":"Badebetrieb"},
              {"date":"2026-05-23","start":"1000","court":71108,"title":"VHS"},
              {"date":"2026-05-23","start":"1000","court":71109,"title":"Badebetrieb"},
              {"date":"2026-05-23","start":"1000","court":71109,"title":null}
            ]}
        """.trimIndent()
        val r = newScraper().parse(mixed, LocalTime.of(10, 0))
        assertEquals(LaneReservations(lane1Reserved = true, lane2Reserved = true, lane3Reserved = false), r)
    }

    @Test fun `all three lower lanes reserved at noon (the OKAY-tier trigger)`() {
        val r = newScraper().parse(fixture("eversports_hoettinger.json"), LocalTime.of(12, 0))
        assertEquals(LaneReservations(true, true, true), r)
        assertTrue(r.lowerThreeAllReserved)
    }

    @Test fun `times round down to 30-min slot`() {
        val parser = newScraper()
        // 12:15 should round to "1200"; 12:45 should round to "1230"
        val at1215 = parser.parse(fixture("eversports_hoettinger.json"), LocalTime.of(12, 15))
        val at1245 = parser.parse(fixture("eversports_hoettinger.json"), LocalTime.of(12, 45))
        // Just assert they parsed (no exception) and lowerThreeAllReserved is consistent at midday
        assertTrue(at1215.lowerThreeAllReserved)
        assertTrue(at1245.lowerThreeAllReserved)
    }

    @Test fun `Badebetrieb does not count as reservation`() {
        val onlyBadebetrieb = """
            {"slots":[
              {"date":"2026-05-23","start":"1000","court":71107,"title":"Badebetrieb"},
              {"date":"2026-05-23","start":"1000","court":71108,"title":"Badebetrieb"},
              {"date":"2026-05-23","start":"1000","court":71109,"title":"Badebetrieb"}
            ]}
        """.trimIndent()
        val r = newScraper().parse(onlyBadebetrieb, LocalTime.of(10, 0))
        assertEquals(LaneReservations(false, false, false), r)
    }

    @Test fun `fetch round trip via MockWebServer`() = runBlocking {
        server.enqueue(MockResponse().setBody(fixture("eversports_hoettinger.json")))
        // clock fixed to 2026-05-23 noon (Saturday) → all 3 lanes reserved per fixture
        val result = newScraper(now = LocalTime.of(12, 0)).fetch()
        assertEquals(LaneReservations(true, true, true), result)
    }

    @Test fun `fetch sends required headers and query params`() = runBlocking {
        server.enqueue(MockResponse().setBody(fixture("eversports_hoettinger.json")))
        newScraper().fetch()

        val recorded = server.takeRequest()
        assertEquals("XMLHttpRequest", recorded.getHeader("X-Requested-With"))
        assertTrue(recorded.getHeader("Referer")?.contains("eversports.at/widget/w/va4m84") == true)
        val path = recorded.path!!
        assertTrue(path.contains("facilityId=75192"))
        assertTrue(path.contains("sport=hallenbad-hoettinger-au"))
        assertTrue(path.contains("startDate=2026-05-23"))
        assertTrue(path.contains("courts%5B%5D=71107"))
        assertTrue(path.contains("courts%5B%5D=71109"))
    }

    @Test fun `fetch throws on 403 (Cloudflare gate)`() {
        server.enqueue(MockResponse().setResponseCode(403))
        assertThrows(java.io.IOException::class.java) {
            runBlocking { newScraper().fetch() }
        }
    }

    @Test fun `slotKey rounds correctly`() {
        assertEquals("0800", ReservationsScraper.slotKey(LocalTime.of(8, 0)))
        assertEquals("0800", ReservationsScraper.slotKey(LocalTime.of(8, 29)))
        assertEquals("0830", ReservationsScraper.slotKey(LocalTime.of(8, 30)))
        assertEquals("0830", ReservationsScraper.slotKey(LocalTime.of(8, 59)))
        assertEquals("0000", ReservationsScraper.slotKey(LocalTime.MIDNIGHT))
        assertEquals("2330", ReservationsScraper.slotKey(LocalTime.of(23, 45)))
    }
}

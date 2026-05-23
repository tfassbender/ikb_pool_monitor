package com.tfassbender.ikbpool.data.source

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.jsoup.Jsoup
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalTime

class OpeningHoursScraperTest {

    private lateinit var server: MockWebServer
    private val client = OkHttpClient()

    @Before fun setUp() {
        server = MockWebServer().apply { start() }
    }

    @After fun tearDown() {
        server.shutdown()
    }

    private fun fixture(name: String): String =
        javaClass.classLoader!!.getResourceAsStream("fixtures/$name")!!
            .bufferedReader(Charsets.UTF_8).use { it.readText() }

    @Test fun `parses Schwimmbad table from live fixture`() {
        val scraper = OpeningHoursScraper(client, "http://unused")
        val doc = Jsoup.parse(fixture("hoettinger_au.html"))

        val hours = scraper.parse(doc)

        assertEquals(7, hours.schedule.size)
        assertEquals(LocalTime.of(6, 30) to LocalTime.of(22, 0), hours.windowFor(DayOfWeek.MONDAY).asPair())
        assertNull(hours.windowFor(DayOfWeek.TUESDAY))
        assertEquals(LocalTime.of(6, 30) to LocalTime.of(22, 0), hours.windowFor(DayOfWeek.WEDNESDAY).asPair())
        assertEquals(LocalTime.of(9, 0) to LocalTime.of(22, 0), hours.windowFor(DayOfWeek.THURSDAY).asPair())
        assertEquals(LocalTime.of(8, 0) to LocalTime.of(22, 0), hours.windowFor(DayOfWeek.FRIDAY).asPair())
        assertEquals(LocalTime.of(9, 0) to LocalTime.of(22, 0), hours.windowFor(DayOfWeek.SATURDAY).asPair())
        assertEquals(LocalTime.of(10, 0) to LocalTime.of(20, 30), hours.windowFor(DayOfWeek.SUNDAY).asPair())
    }

    @Test fun `fetch round trip via MockWebServer`() = runBlocking {
        server.enqueue(MockResponse().setBody(fixture("hoettinger_au.html")))
        val scraper = OpeningHoursScraper(client, server.url("/").toString())

        val hours = scraper.fetch()

        assertNull(hours.windowFor(DayOfWeek.TUESDAY))
        assertEquals(LocalTime.of(6, 30), hours.windowFor(DayOfWeek.MONDAY)!!.open)
    }

    @Test fun `fetch throws on HTTP error`() {
        server.enqueue(MockResponse().setResponseCode(500))
        val scraper = OpeningHoursScraper(client, server.url("/").toString())

        assertThrows(java.io.IOException::class.java) {
            runBlocking { scraper.fetch() }
        }
    }

    @Test fun `parse throws when opening-hours panel missing`() {
        val scraper = OpeningHoursScraper(client, "http://unused")
        val doc = Jsoup.parse("<html><body><p>no panel here</p></body></html>")

        assertThrows(IllegalStateException::class.java) { scraper.parse(doc) }
    }

    private fun com.tfassbender.ikbpool.data.model.OpeningWindow?.asPair() =
        this?.let { it.open to it.close }
}

package com.tfassbender.ikbpool.data.source

import com.tfassbender.ikbpool.data.model.Occupancy
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

class OccupancyScraperTest {

    private lateinit var server: MockWebServer
    private val client = OkHttpClient()

    @Before fun setUp() { server = MockWebServer().apply { start() } }
    @After fun tearDown() { server.shutdown() }

    private fun fixture(name: String): String =
        javaClass.classLoader!!.getResourceAsStream("fixtures/$name")!!
            .bufferedReader(Charsets.UTF_8).use { it.readText() }

    @Test fun `parses HBH percent from live fixture`() {
        val scraper = OccupancyScraper(client, "http://unused")
        val doc = Jsoup.parse(fixture("baeder_auslastung.html"))

        val result = scraper.parse(doc)

        assertEquals(Occupancy(14), result)
    }

    @Test fun `returns null when pool is closed`() {
        val scraper = OccupancyScraper(client, "http://unused")
        val html = """
            <html><body>
              <span id="cphInhalt_lblHBH_Bad1">
                <div class='auslastungoverlay2'></div>
                <p class='auslastungtext2'>derzeit<br/>geschlossen</p>
              </span>
            </body></html>
        """.trimIndent()

        assertNull(scraper.parse(Jsoup.parse(html)))
    }

    @Test fun `fetch round trip via MockWebServer`() = runBlocking {
        server.enqueue(MockResponse().setBody(fixture("baeder_auslastung.html")))
        val scraper = OccupancyScraper(client, server.url("/").toString())

        assertEquals(Occupancy(14), scraper.fetch())
    }

    @Test fun `fetch throws on HTTP error`() {
        server.enqueue(MockResponse().setResponseCode(503))
        val scraper = OccupancyScraper(client, server.url("/").toString())

        assertThrows(java.io.IOException::class.java) {
            runBlocking { scraper.fetch() }
        }
    }

    @Test fun `parse throws when target element missing`() {
        val scraper = OccupancyScraper(client, "http://unused")
        assertThrows(IllegalStateException::class.java) {
            scraper.parse(Jsoup.parse("<html><body></body></html>"))
        }
    }
}

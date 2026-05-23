package com.tfassbender.ikbpool.data.source

import com.tfassbender.ikbpool.data.model.OpeningHours
import com.tfassbender.ikbpool.data.model.OpeningWindow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.io.IOException
import java.time.DayOfWeek
import java.time.LocalTime

class OpeningHoursScraper(
    private val http: OkHttpClient,
    private val url: String = DEFAULT_URL,
) : PoolDataSource<OpeningHours> {

    override suspend fun fetch(): OpeningHours = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(url).get().build()
        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code} fetching $url")
            val body = resp.body?.string() ?: throw IOException("empty body from $url")
            parse(Jsoup.parse(body))
        }
    }

    internal fun parse(doc: Document): OpeningHours {
        val panel = doc.getElementById("oeffnungszeiten")
            ?: error("opening-hours panel #oeffnungszeiten not found")
        val table = panel.selectFirst("table.contenttable")
            ?: error("opening-hours table not found in #oeffnungszeiten")
        val schedule = mutableMapOf<DayOfWeek, OpeningWindow?>()
        for (row in table.select("tbody > tr")) {
            val cells = row.select("td")
            if (cells.size < 2) continue
            val day = GERMAN_DAYS[cells[0].text().trim()] ?: continue
            schedule[day] = parseWindow(cells[1].text().trim())
        }
        if (schedule.size != 7) {
            error("expected 7 weekday rows, got ${schedule.size}: ${schedule.keys}")
        }
        return OpeningHours(schedule)
    }

    private fun parseWindow(text: String): OpeningWindow? {
        if (text.equals("GESCHLOSSEN", ignoreCase = true)) return null
        val m = WINDOW_REGEX.find(text)
            ?: error("can't parse opening window: \"$text\"")
        return OpeningWindow(parseTime(m.groupValues[1]), parseTime(m.groupValues[2]))
    }

    private fun parseTime(s: String): LocalTime {
        val parts = s.replace('.', ':').split(':')
        return LocalTime.of(parts[0].toInt(), parts[1].toInt())
    }

    companion object {
        const val DEFAULT_URL = "https://www.ikb.at/baeder/hallenbad-sauna-hoettinger-au"

        private val WINDOW_REGEX =
            Regex("""(\d{1,2}[.:]\d{2})\s*bis\s*(\d{1,2}[.:]\d{2})""")

        private val GERMAN_DAYS = mapOf(
            "Montag" to DayOfWeek.MONDAY,
            "Dienstag" to DayOfWeek.TUESDAY,
            "Mittwoch" to DayOfWeek.WEDNESDAY,
            "Donnerstag" to DayOfWeek.THURSDAY,
            "Freitag" to DayOfWeek.FRIDAY,
            "Samstag" to DayOfWeek.SATURDAY,
            "Sonntag" to DayOfWeek.SUNDAY,
        )
    }
}

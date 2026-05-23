package com.tfassbender.ikbpool.data.source

import com.tfassbender.ikbpool.data.model.Occupancy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.io.IOException

class OccupancyScraper(
    private val http: OkHttpClient,
    private val url: String = DEFAULT_URL,
) : PoolDataSource<Occupancy?> {

    override suspend fun fetch(): Occupancy? = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(url).get().build()
        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code} fetching $url")
            val body = resp.body?.string() ?: throw IOException("empty body from $url")
            parse(Jsoup.parse(body))
        }
    }

    internal fun parse(doc: Document): Occupancy? {
        val el = doc.getElementById(HBH_BAD_ID)
            ?: error("element #$HBH_BAD_ID not found on occupancy page")

        el.selectFirst("p.text > span")?.let { span ->
            val pct = span.text().trim().toIntOrNull()
                ?: error("can't parse occupancy percent: \"${span.text()}\"")
            return Occupancy(pct)
        }

        if (el.text().contains("geschlossen", ignoreCase = true)) return null

        error("unexpected structure in #$HBH_BAD_ID: \"${el.text()}\"")
    }

    companion object {
        const val DEFAULT_URL = "https://sas.ikb.at/ks/baeder_auslastung.aspx?segment=privat"
        private const val HBH_BAD_ID = "cphInhalt_lblHBH_Bad1"
    }
}

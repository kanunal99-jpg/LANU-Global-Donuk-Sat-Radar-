package com.lanu.globaldonuksatisradari.data

import android.os.SystemClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.BufferedInputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Narrows real OSM discovery at request time when the user selected a neighborhood.
 * This is intentionally separate from the neighborhood catalog: the catalog discovers choices,
 * while this source makes the selected choice part of the upstream business query itself.
 */
class NeighborhoodScopedOsmSource(
    private val nowEpochMs: () -> Long = { System.currentTimeMillis() },
) {
    suspend fun fetchOverpass(
        query: String,
        city: String,
        district: String,
        neighborhood: String,
    ): List<VerifiedBusiness> = withContext(Dispatchers.IO) {
        require(city.isNotBlank()) { "Şehir boş olamaz" }
        require(district.isNotBlank()) { "İlçe boş olamaz" }
        require(neighborhood.isNotBlank()) { "Mahalle boş olamaz" }

        var lastFailure: Exception? = null
        val endpoints = (listOf(OverpassBusinessSource.BASE_URL) + OverpassBusinessSource.FALLBACK_URLS)
            .map(String::trim)
            .filter { it.startsWith("https://") }
            .distinct()

        for (endpoint in endpoints) {
            try {
                NeighborhoodOsmRateLimiter.await()
                val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 20_000
                    readTimeout = 120_000
                    doOutput = true
                    setRequestProperty("Accept", "application/json")
                    setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                    setRequestProperty(
                        "User-Agent",
                        "LANU-Global-Donuk-Satis-Radari/0.5 (+https://github.com/kanunal99-jpg/LANU-Global-Donuk-Sat-Radar-)",
                    )
                }
                try {
                    val encoded = URLEncoder.encode(
                        NeighborhoodScopedOsmQueryBuilder.overpass(query, city, district, neighborhood),
                        Charsets.UTF_8.name(),
                    )
                    connection.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write("data=$encoded") }
                    val responseCode = connection.responseCode
                    if (responseCode !in 200..299) throw IOException("Overpass HTTP $responseCode")
                    val payload = readBounded(connection)
                    val parsed = OverpassBusinessSourceAdapter(nowEpochMs = nowEpochMs)
                        .parse(payload, city, district, nowEpochMs())
                        .map { it.copy(neighborhood = neighborhood) }
                    return@withContext validate(parsed)
                } finally {
                    connection.disconnect()
                }
            } catch (error: kotlinx.coroutines.CancellationException) {
                throw error
            } catch (error: Exception) {
                lastFailure = error
            }
        }
        throw lastFailure ?: IllegalStateException("Mahalle kapsamlı Overpass endpoint bulunamadı")
    }

    suspend fun fetchNominatim(
        query: String,
        city: String,
        district: String,
        neighborhood: String,
    ): List<VerifiedBusiness> = withContext(Dispatchers.IO) {
        require(query.isNotBlank()) { "Nominatim mahalle fallback'i için arama metni gerekli" }
        require(city.isNotBlank() && district.isNotBlank() && neighborhood.isNotBlank()) {
            "Şehir, ilçe ve mahalle zorunlu"
        }
        NeighborhoodOsmRateLimiter.await()
        val connection = (URL(
            NeighborhoodScopedOsmQueryBuilder.nominatim(query, city, district, neighborhood),
        ).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 20_000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Accept-Language", "tr")
            setRequestProperty(
                "User-Agent",
                "LANU-Global-Donuk-Satis-Radari/0.5 (+https://github.com/kanunal99-jpg/LANU-Global-Donuk-Sat-Radar-)",
            )
        }
        try {
            val responseCode = connection.responseCode
            if (responseCode !in 200..299) throw IOException("Nominatim HTTP $responseCode")
            val payload = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            // Validate JSON before delegating to the existing canonical parser.
            JSONArray(payload)
            val parsed = NominatimBusinessSourceAdapter(nowEpochMs = nowEpochMs)
                .parse(payload, city, district, nowEpochMs())
            val scoped = parsed.filter { business ->
                business.neighborhood?.equals(neighborhood, ignoreCase = true) == true ||
                    business.address?.contains(neighborhood, ignoreCase = true) == true
            }.map { it.copy(neighborhood = neighborhood) }
            validate(scoped)
        } finally {
            connection.disconnect()
        }
    }

    private fun validate(records: List<VerifiedBusiness>): List<VerifiedBusiness> =
        records.mapNotNull { VerifiedBusinessValidator.validate(it).getOrNull() }

    private fun readBounded(connection: HttpURLConnection): String {
        val input = BufferedInputStream(connection.inputStream)
        return buildString {
            val buffer = ByteArray(16 * 1024)
            var total = 0
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                total += read
                if (total > 32 * 1024 * 1024) {
                    throw IllegalStateException("Overpass yanıtı güvenli boyut sınırını aştı")
                }
                append(String(buffer, 0, read, Charsets.UTF_8))
            }
        }
    }
}

internal object NeighborhoodScopedOsmQueryBuilder {
    fun overpass(query: String, city: String, district: String, neighborhood: String): String {
        val base = OverpassQueryBuilder.build(city, district, query)
        val neighborhoodRegex = escapeRegex(neighborhood.trim())
        val filter = "[~\"^(addr:(suburb|neighbourhood|quarter)|is_in:(neighbourhood|suburb))$\"~\"^$neighborhoodRegex$\",i]"
        return base.replace("(area.searchArea);", "$filter(area.searchArea);")
    }

    fun nominatim(query: String, city: String, district: String, neighborhood: String): String {
        val location = listOf(query.trim(), neighborhood.trim(), district.trim(), city.trim(), "Türkiye")
            .filter(String::isNotBlank)
            .joinToString(", ")
        return NominatimBusinessSource.BASE_URL +
            "?format=jsonv2&addressdetails=1&extratags=1&namedetails=1&layer=poi&limit=40&countrycodes=tr&q=" +
            URLEncoder.encode(location, Charsets.UTF_8.name())
    }

    private fun escapeRegex(value: String): String = buildString {
        value.forEach { char ->
            if (char in "\\.^$|?*+()[]{}-") append('\\')
            append(char)
        }
    }
}

private object NeighborhoodOsmRateLimiter {
    private const val MIN_INTERVAL_MS = 1_500L
    private var lastRequestAt = 0L

    @Synchronized
    fun await() {
        val now = SystemClock.elapsedRealtime()
        val wait = MIN_INTERVAL_MS - (now - lastRequestAt)
        if (wait > 0) Thread.sleep(wait)
        lastRequestAt = SystemClock.elapsedRealtime()
    }
}

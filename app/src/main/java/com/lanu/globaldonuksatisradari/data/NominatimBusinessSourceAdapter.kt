package com.lanu.globaldonuksatisradari.data

import android.os.SystemClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

/** Real external OSM place search; user-triggered and explicitly non-exhaustive. */
object NominatimBusinessSource {
    private const val SOURCE_REVIEWED_AT = 1789646400000L
    const val BASE_URL = "https://nominatim.openstreetmap.org/search"

    val descriptor = DataSourceDescriptor(
        id = "osm-nominatim",
        name = "OpenStreetMap Nominatim",
        publisher = "OpenStreetMap Foundation",
        licenseOrTerms = "https://operations.osmfoundation.org/policies/nominatim/ ; https://www.openstreetmap.org/copyright",
        sourceUrl = "https://nominatim.openstreetmap.org/",
        lastVerifiedAtEpochMs = SOURCE_REVIEWED_AT,
    )

    val contract = BusinessSourceContract(
        descriptor = descriptor,
        accessMethod = SourceAccessMethod.PUBLIC_SEARCH,
        scope = "User-triggered place search; non-exhaustive HORECA discovery; OSM/ODbL data",
        permittedUseVerified = true,
        supportsBulk = false,
        fieldNames = setOf("name", "city", "district", "neighborhood", "latitude", "longitude", "category", "address"),
    )
}

object NominatimQueryBuilder {
    fun build(query: String, city: String, district: String?, baseUrl: String = NominatimBusinessSource.BASE_URL): String {
        require(query.isNotBlank()) { "Arama metni boş olamaz" }
        require(city.isNotBlank()) { "Şehir boş olamaz" }
        require(baseUrl.startsWith("https://")) { "Kaynak endpoint HTTPS olmalı" }
        val location = listOfNotNull(
            query.trim(),
            district?.takeUnless { it.isBlank() || it.equals("Tümü", ignoreCase = true) },
            city.trim(),
            "Türkiye",
        ).joinToString(", ")
        return "$baseUrl?format=jsonv2&addressdetails=1&limit=20&countrycodes=tr&q=${URLEncoder.encode(location, Charsets.UTF_8.name())}"
    }
}

class NominatimBusinessSourceAdapter(
    private val nowEpochMs: () -> Long = { System.currentTimeMillis() },
    private val baseUrlProvider: () -> String = { NominatimBusinessSource.BASE_URL },
) : BusinessSourceAdapter {
    override val contract: BusinessSourceContract = NominatimBusinessSource.contract

    override suspend fun fetch(
        query: String,
        city: String,
        district: String?,
    ): List<VerifiedBusiness> = withContext(Dispatchers.IO) {
        if (contract.validate().isFailure || query.isBlank()) return@withContext emptyList()

        val cacheKey = listOf(query.trim().lowercase(), city.trim().lowercase(), district?.trim()?.lowercase().orEmpty(), baseUrlProvider()).joinToString("|")
        SearchCache.get(cacheKey)?.let { return@withContext it }
        RateLimiter.await()

        val connection = (URL(NominatimQueryBuilder.build(query, city, district, baseUrlProvider())).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 20_000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Accept-Language", "tr")
            setRequestProperty(
                "User-Agent",
                "LANU-Global-Donuk-Satis-Radari/0.1 (+https://github.com/kanunal99-jpg/LANU-Global-Donuk-Sat-Radar-)"
            )
        }

        try {
            if (connection.responseCode !in 200..299) return@withContext emptyList()
            val payload = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val parsed = parse(payload, city, district, nowEpochMs())
            SearchCache.put(cacheKey, parsed)
            parsed
        } finally {
            connection.disconnect()
        }
    }

    private fun parse(
        payload: String,
        selectedCity: String,
        selectedDistrict: String?,
        verifiedAtEpochMs: Long,
    ): List<VerifiedBusiness> {
        val json = JSONArray(payload)
        val result = mutableListOf<VerifiedBusiness>()
        for (index in 0 until json.length()) {
            val item = json.optJSONObject(index) ?: continue
            val name = item.optString("name").trim()
            if (name.isBlank()) continue
            val address = item.optJSONObject("address")
            val district = selectedDistrict?.takeUnless { it.isBlank() || it.equals("Tümü", ignoreCase = true) }
                ?: address?.let {
                    listOfNotNull(
                        it.optString("suburb").takeIf(String::isNotBlank),
                        it.optString("city_district").takeIf(String::isNotBlank),
                        it.optString("town").takeIf(String::isNotBlank),
                    ).firstOrNull()
                }
                ?: continue
            val id = "${item.optString("osm_type")}:${item.optString("osm_id")}".trim(':')
            if (id.isBlank()) continue

            result += VerifiedBusiness(
                id = id,
                name = name,
                city = selectedCity,
                district = district,
                neighborhood = address?.optString("neighbourhood")?.takeIf(String::isNotBlank),
                source = contract.descriptor,
                verifiedAtEpochMs = verifiedAtEpochMs,
                latitude = item.optString("lat").toDoubleOrNull(),
                longitude = item.optString("lon").toDoubleOrNull(),
                category = item.optString("type").takeIf(String::isNotBlank),
                address = item.optString("display_name").takeIf(String::isNotBlank),
            )
        }
        return BusinessDeduplication.deduplicate(result)
    }
}

private object SearchCache {
    private const val MAX_AGE_MS = 5 * 60 * 1000L
    private data class Entry(val createdAt: Long, val records: List<VerifiedBusiness>)
    private val entries = ConcurrentHashMap<String, Entry>()

    fun get(key: String): List<VerifiedBusiness>? {
        val entry = entries[key] ?: return null
        if (System.currentTimeMillis() - entry.createdAt > MAX_AGE_MS) {
            entries.remove(key)
            return null
        }
        return entry.records
    }

    fun put(key: String, records: List<VerifiedBusiness>) {
        entries[key] = Entry(System.currentTimeMillis(), records)
    }
}

private object RateLimiter {
    private const val MIN_INTERVAL_MS = 1_100L
    private var lastRequestAt = 0L

    @Synchronized
    fun await() {
        val now = SystemClock.elapsedRealtime()
        val wait = MIN_INTERVAL_MS - (now - lastRequestAt)
        if (wait > 0) Thread.sleep(wait)
        lastRequestAt = SystemClock.elapsedRealtime()
    }
}

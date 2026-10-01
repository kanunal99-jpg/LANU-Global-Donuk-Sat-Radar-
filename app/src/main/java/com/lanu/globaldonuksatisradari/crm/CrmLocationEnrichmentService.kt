package com.lanu.globaldonuksatisradari.crm

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.lanu.globaldonuksatisradari.data.BusinessDeduplication
import com.lanu.globaldonuksatisradari.data.DistrictCatalogRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap

internal data class OsmLookupAddress(
    val osmKey: String,
    val displayName: String?,
    val provinceCandidates: List<String>,
    val districtCandidates: List<String>,
    val neighborhoodCandidates: List<String>,
)

internal object CrmLocationSanitizer {
    fun sanitizeWithoutNetwork(customer: CrmCustomer): CrmCustomer = customer.copy(
        district = sanitizeDistrictLabel(customer.district).orEmpty(),
        neighborhood = sanitizeNeighborhood(customer.neighborhood),
        address = sanitizeAddress(
            value = customer.address,
            city = customer.city,
            district = customer.district,
        ),
    )

    fun sanitizeDistrictLabel(value: String?): String? =
        value?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.takeUnless {
                it.equals("Bilinmiyor", ignoreCase = true) ||
                    it.equals("Bilinmeyen", ignoreCase = true) ||
                    it.equals("Unknown", ignoreCase = true)
            }

    fun sanitizeNeighborhood(value: String?): String? =
        value?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.takeUnless {
                it.equals("Bilinmiyor", ignoreCase = true) ||
                    it.equals("Bilinmeyen", ignoreCase = true) ||
                    it.equals("Unknown", ignoreCase = true)
            }

    fun sanitizeAddress(value: String?, city: String, district: String?): String? {
        val cleaned = value
            ?.trim()
            ?.replace(Regex("\\s+"), " ")
            ?.replace(Regex(",\\s*,+"), ",")
            ?.trim(' ', ',')
            ?.takeIf(String::isNotBlank)
            ?: return null

        if (cleaned.all(Char::isDigit)) return null

        val pieces = cleaned
            .split(',', '/', ';')
            .map { it.trim() }
            .filter(String::isNotBlank)

        if (pieces.isEmpty()) return null

        val cityKey = normalizeAdmin(city)
        val districtKey = sanitizeDistrictLabel(district)?.let(::normalizeAdmin)
        val administrativeOnly = pieces.all { piece ->
            val key = normalizeAdmin(piece)
            key == cityKey ||
                key == districtKey ||
                key in COUNTRY_KEYS ||
                POSTCODE_REGEX.matches(piece)
        }
        if (administrativeOnly) return null

        return pieces
            .filterNot { normalizeAdmin(it) in COUNTRY_KEYS }
            .joinToString(", ")
            .takeIf { it.length >= 4 }
    }

    fun canonicalDistrict(
        current: String?,
        city: String,
        candidates: List<String>,
        validDistricts: List<String>,
    ): String? {
        val canonical = validDistricts.associateBy(::normalizeAdmin)
        val center = canonical[normalizeAdmin("Merkez")]

        fun match(value: String): String? {
            val cleaned = removeDistrictSuffix(value)
            val normalized = normalizeAdmin(cleaned)
            canonical[normalized]?.let { return it }

            val cityKey = normalizeAdmin(city)
            if (center != null && (
                    normalized == cityKey ||
                        normalized == "$cityKey merkez" ||
                        normalized == "merkez $cityKey"
                    )
            ) {
                return center
            }
            return null
        }

        sanitizeDistrictLabel(current)?.let { existing ->
            match(existing)?.let { return it }
            if (validDistricts.isEmpty()) return existing
        }

        candidates.forEach { candidate ->
            match(candidate)?.let { return it }
        }

        return if (validDistricts.isEmpty()) {
            candidates.firstNotNullOfOrNull(::sanitizeDistrictLabel)
        } else {
            null
        }
    }

    fun provinceMatches(city: String, candidates: List<String>): Boolean {
        if (candidates.isEmpty()) return true
        val cityKey = normalizeAdmin(city)
        return candidates.any { normalizeAdmin(it) == cityKey }
    }

    fun bestNeighborhood(
        current: String?,
        candidates: List<String>,
    ): String? =
        sanitizeNeighborhood(current)
            ?: candidates.firstNotNullOfOrNull(::sanitizeNeighborhood)

    private fun removeDistrictSuffix(value: String): String =
        value.trim()
            .replace(Regex("\\s+(ilçesi|ilcesi)$", RegexOption.IGNORE_CASE), "")
            .trim()

    private fun normalizeAdmin(value: String): String =
        BusinessDeduplication.normalizeForComparison(value)
            .replace(Regex("\\s+"), " ")
            .trim()

    private val POSTCODE_REGEX = Regex("^\\d{5}$")

    private val COUNTRY_KEYS = setOf(
        normalizeAdmin("Türkiye"),
        normalizeAdmin("Turkey"),
        normalizeAdmin("TR"),
    )
}

internal class CrmLocationEnrichmentService(
    context: Context,
    private val lookupBaseUrlProvider: () -> String = { DEFAULT_LOOKUP_URL },
) {
    private val preferences =
        context.applicationContext.getSharedPreferences("crm_location_enrichment_cache", Context.MODE_PRIVATE)
    private val districtCatalog = DistrictCatalogRepository(context)
    private val districtCache = ConcurrentHashMap<String, List<String>>()

    suspend fun enrich(customers: List<CrmCustomer>): List<CrmCustomer> = withContext(Dispatchers.IO) {
        if (customers.isEmpty()) return@withContext emptyList()

        val validDistrictsByCity = customers
            .map { it.city.trim() }
            .filter(String::isNotBlank)
            .distinct()
            .associateWith { city -> loadDistricts(city) }

        val sanitized = customers.map { customer ->
            val validDistricts = validDistrictsByCity[customer.city.trim()].orEmpty()
            sanitizeWithDistrictValidation(customer, validDistricts)
        }

        val lookups = loadLookups(
            sanitized.filter { customer ->
                needsLookup(customer, validDistrictsByCity[customer.city.trim()].orEmpty())
            },
        )

        sanitized.map { customer ->
            val lookup = parseOsmKey(customer.businessSourceId)?.let(lookups::get)
                ?: return@map customer

            if (!CrmLocationSanitizer.provinceMatches(customer.city, lookup.provinceCandidates)) {
                return@map customer
            }

            val validDistricts = validDistrictsByCity[customer.city.trim()].orEmpty()
            val district = CrmLocationSanitizer.canonicalDistrict(
                current = customer.district,
                city = customer.city,
                candidates = lookup.districtCandidates,
                validDistricts = validDistricts,
            )
            val neighborhood = CrmLocationSanitizer.bestNeighborhood(
                current = customer.neighborhood,
                candidates = lookup.neighborhoodCandidates,
            )
            val address = CrmLocationSanitizer.sanitizeAddress(
                value = customer.address,
                city = customer.city,
                district = district,
            ) ?: CrmLocationSanitizer.sanitizeAddress(
                value = lookup.displayName,
                city = customer.city,
                district = district,
            )

            customer.copy(
                district = district.orEmpty(),
                neighborhood = neighborhood,
                address = address,
            )
        }
    }

    private suspend fun loadDistricts(city: String): List<String> {
        val key = BusinessDeduplication.normalizeForComparison(city)
        districtCache[key]?.let { return it }

        val loaded = try {
            districtCatalog.getDistricts(city, emptyList())
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.w(TAG, "İlçe doğrulama kataloğu alınamadı: $city", error)
            emptyList()
        }
        districtCache[key] = loaded
        return loaded
    }

    private fun sanitizeWithDistrictValidation(
        customer: CrmCustomer,
        validDistricts: List<String>,
    ): CrmCustomer {
        val base = CrmLocationSanitizer.sanitizeWithoutNetwork(customer)
        val district = CrmLocationSanitizer.canonicalDistrict(
            current = base.district,
            city = base.city,
            candidates = emptyList(),
            validDistricts = validDistricts,
        )
        return base.copy(
            district = district.orEmpty(),
            address = CrmLocationSanitizer.sanitizeAddress(
                value = base.address,
                city = base.city,
                district = district,
            ),
        )
    }

    private fun needsLookup(
        customer: CrmCustomer,
        validDistricts: List<String>,
    ): Boolean {
        if (parseOsmKey(customer.businessSourceId) == null) return false
        val district = CrmLocationSanitizer.canonicalDistrict(
            current = customer.district,
            city = customer.city,
            candidates = emptyList(),
            validDistricts = validDistricts,
        )
        return district == null ||
            customer.neighborhood.isNullOrBlank() ||
            customer.address.isNullOrBlank()
    }

    private suspend fun loadLookups(customers: List<CrmCustomer>): Map<String, OsmLookupAddress> {
        val osmKeys = customers
            .mapNotNull { parseOsmKey(it.businessSourceId) }
            .distinct()

        if (osmKeys.isEmpty()) return emptyMap()

        val result = LinkedHashMap<String, OsmLookupAddress>()
        val missing = mutableListOf<String>()

        osmKeys.forEach { key ->
            readCache(key)?.let { result[key] = it } ?: missing.add(key)
        }

        missing.chunked(MAX_LOOKUP_BATCH).forEach { batch ->
            LookupRateLimiter.await()
            val fetched = try {
                fetchBatch(batch)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.w(TAG, "OSM adres lookup başarısız; yerel veriyle devam ediliyor.", error)
                emptyMap()
            }
            fetched.forEach { (key, value) ->
                result[key] = value
                writeCache(value)
            }
        }

        return result
    }

    private fun fetchBatch(osmKeys: List<String>): Map<String, OsmLookupAddress> {
        val baseUrl = lookupBaseUrlProvider()
        require(baseUrl.startsWith("https://")) { "Nominatim lookup endpoint HTTPS olmalı" }

        val osmIds = osmKeys.mapNotNull(::toNominatimId)
        if (osmIds.isEmpty()) return emptyMap()

        val url = URL(
            "$baseUrl?format=jsonv2&addressdetails=1&namedetails=0&extratags=0" +
                "&accept-language=tr&osm_ids=" +
                URLEncoder.encode(osmIds.joinToString(","), Charsets.UTF_8.name()),
        )
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 25_000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Accept-Language", "tr")
            setRequestProperty(
                "User-Agent",
                "LANU-Global-Donuk-Satis-Radari/0.3 (+https://github.com/kanunal99-jpg/LANU-Global-Donuk-Sat-Radar-)",
            )
        }

        try {
            val code = connection.responseCode
            if (code !in 200..299) throw IOException("Nominatim lookup HTTP $code")
            val payload = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            return parseLookupPayload(payload).associateBy(OsmLookupAddress::osmKey)
        } finally {
            connection.disconnect()
        }
    }

    private fun readCache(osmKey: String): OsmLookupAddress? {
        val raw = preferences.getString("lookup:$osmKey", null) ?: return null
        val root = runCatching { JSONObject(raw) }.getOrNull() ?: return null
        val savedAt = root.optLong("savedAtEpochMs", 0L)
        if (savedAt <= 0L || System.currentTimeMillis() - savedAt > CACHE_TTL_MS) return null
        return root.optJSONObject("value")?.let(::parseCachedLookup)
    }

    private fun writeCache(value: OsmLookupAddress) {
        val encoded = JSONObject()
            .put("osmKey", value.osmKey)
            .put("displayName", value.displayName)
            .put("provinceCandidates", JSONArray(value.provinceCandidates))
            .put("districtCandidates", JSONArray(value.districtCandidates))
            .put("neighborhoodCandidates", JSONArray(value.neighborhoodCandidates))
        preferences.edit()
            .putString(
                "lookup:${value.osmKey}",
                JSONObject()
                    .put("savedAtEpochMs", System.currentTimeMillis())
                    .put("value", encoded)
                    .toString(),
            )
            .apply()
    }

    private fun parseCachedLookup(item: JSONObject): OsmLookupAddress =
        OsmLookupAddress(
            osmKey = item.optString("osmKey"),
            displayName = item.optString("displayName").trim().takeIf(String::isNotBlank),
            provinceCandidates = item.optJSONArray("provinceCandidates").toStringList(),
            districtCandidates = item.optJSONArray("districtCandidates").toStringList(),
            neighborhoodCandidates = item.optJSONArray("neighborhoodCandidates").toStringList(),
        )

    companion object {
        const val DEFAULT_LOOKUP_URL = "https://nominatim.openstreetmap.org/lookup"
        private const val MAX_LOOKUP_BATCH = 50
        private const val CACHE_TTL_MS = 30L * 24 * 60 * 60 * 1000
        private const val TAG = "LanuCrmLocation"

        internal fun parseLookupPayload(payload: String): List<OsmLookupAddress> {
            val array = JSONArray(payload)
            return buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val type = item.optString("osm_type").trim().lowercase()
                    val id = item.optLong("osm_id")
                    val osmKey = if (type in setOf("node", "way", "relation") && id > 0L) {
                        "$type:$id"
                    } else {
                        continue
                    }
                    val address = item.optJSONObject("address")
                    add(
                        OsmLookupAddress(
                            osmKey = osmKey,
                            displayName = item.optString("display_name").trim().takeIf(String::isNotBlank),
                            provinceCandidates = address.valuesFor(
                                "state",
                                "province",
                                "region",
                            ),
                            districtCandidates = address.valuesFor(
                                "district",
                                "city_district",
                                "town",
                                "municipality",
                                "county",
                                "state_district",
                                "city",
                            ),
                            neighborhoodCandidates = address.valuesFor(
                                "neighbourhood",
                                "quarter",
                                "suburb",
                                "village",
                                "hamlet",
                            ),
                        ),
                    )
                }
            }
        }

        internal fun parseOsmKey(value: String): String? {
            val match = Regex("^(node|way|relation):(\\d+)$", RegexOption.IGNORE_CASE)
                .matchEntire(value.trim())
                ?: return null
            return match.groupValues[1].lowercase() + ":" + match.groupValues[2]
        }

        private fun toNominatimId(osmKey: String): String? {
            val parsed = parseOsmKey(osmKey) ?: return null
            val (type, id) = parsed.split(':', limit = 2)
            val prefix = when (type) {
                "node" -> "N"
                "way" -> "W"
                "relation" -> "R"
                else -> return null
            }
            return prefix + id
        }

        private fun JSONObject?.valuesFor(vararg keys: String): List<String> =
            keys.mapNotNull { key ->
                this?.optString(key)?.trim()?.takeIf(String::isNotBlank)
            }.distinctBy(BusinessDeduplication::normalizeForComparison)

        private fun JSONArray?.toStringList(): List<String> {
            if (this == null) return emptyList()
            return buildList {
                for (index in 0 until length()) {
                    optString(index).trim().takeIf(String::isNotBlank)?.let(::add)
                }
            }
        }
    }
}

private object LookupRateLimiter {
    private const val MIN_INTERVAL_MS = 1_100L
    private val mutex = Mutex()
    private var lastRequestAt = 0L

    suspend fun await() {
        mutex.withLock {
            val now = SystemClock.elapsedRealtime()
            val wait = MIN_INTERVAL_MS - (now - lastRequestAt)
            if (wait > 0L) delay(wait)
            lastRequestAt = SystemClock.elapsedRealtime()
        }
    }
}

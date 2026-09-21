package com.lanu.globaldonuksatisradari.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class MultiSourceBusinessRepository(
    context: Context,
) : BusinessRepository {
    private val ISTANBUL_DISTRICTS = listOf(
        "Adalar", "Arnavutköy", "Ataşehir", "Avcılar", "Bağcılar", "Bahçelievler",
        "Bakırköy", "Başakşehir", "Bayrampaşa", "Beşiktaş", "Beykoz", "Beylikdüzü",
        "Beyoğlu", "Büyükçekmece", "Çatalca", "Çekmeköy", "Esenler", "Esenyurt",
        "Eyüpsultan", "Fatih", "Gaziosmanpaşa", "Güngören", "Kadıköy", "Kağıthane",
        "Kartal", "Küçükçekmece", "Maltepe", "Pendik", "Sancaktepe", "Sarıyer",
        "Silivri", "Sultanbeyli", "Sultangazi", "Şile", "Şişli", "Tuzla",
        "Ümraniye", "Üsküdar", "Zeytinburnu",
    )

    private val primary = OverpassBusinessSourceAdapter()
    private val alternative = NominatimBusinessSourceAdapter()
    private val cache = BusinessInventoryCache(context.applicationContext)

    override suspend fun search(
        query: String,
        city: String,
        district: String?,
    ): List<VerifiedBusiness> = withContext(Dispatchers.IO) {
        if (city.equals("İstanbul", ignoreCase = true) && district == null) {
            return@withContext searchWholeIstanbul(query)
        }

        val key = BusinessInventoryCache.key(city, district, query)
        val cached = cache.get(key)

        if (query.isBlank() && cached.isNotEmpty() && cache.ageMs(key) < 24 * 60 * 60 * 1000L) {
            return@withContext BusinessDeduplication.deduplicate(cached)
        }

        val primaryRecords = runCatching {
            primary.fetchValidated(query, city, district)
        }.getOrDefault(emptyList())

        if (primaryRecords.isNotEmpty()) {
            val merged = BusinessDeduplication.deduplicate(primaryRecords + cached)
            cache.put(key, merged)
            return@withContext merged
        }

        if (query.isNotBlank() || district != null) {
            val alternativeRecords = runCatching {
                alternative.fetchValidated(query.ifBlank { "restaurant" }, city, district)
            }.getOrDefault(emptyList())
            if (alternativeRecords.isNotEmpty()) {
                val merged = BusinessDeduplication.deduplicate(alternativeRecords + cached)
                cache.put(key, merged)
                return@withContext merged
            }
        }

        return@withContext BusinessDeduplication.deduplicate(cached)
    }

    private suspend fun searchWholeIstanbul(query: String): List<VerifiedBusiness> {
        val merged = linkedMapOf<String, VerifiedBusiness>()

        ISTANBUL_DISTRICTS.forEach { districtName ->
            val key = BusinessInventoryCache.key("İstanbul", districtName, query)
            val cached = cache.get(key)
            val freshCached = cached.isNotEmpty() && cache.ageMs(key) < 24 * 60 * 60 * 1000L

            val records = if (freshCached) {
                cached
            } else {
                val fetched = runCatching {
                    primary.fetchValidated(query, "İstanbul", districtName)
                }.getOrDefault(emptyList())

                if (fetched.isNotEmpty()) {
                    cache.put(key, fetched)
                    fetched
                } else if (query.isNotBlank()) {
                    // A district-level primary outage must not hide that district from a targeted search.
                    // Nominatim remains a targeted, non-exhaustive fallback and is never used for blank
                    // city-wide harvesting.
                    val alternativeRecords = runCatching {
                        alternative.fetchValidated(query, "İstanbul", districtName)
                    }.getOrDefault(emptyList())
                    if (alternativeRecords.isNotEmpty()) {
                        cache.put(key, alternativeRecords)
                        alternativeRecords
                    } else {
                        cached
                    }
                } else {
                    cached
                }
            }

            records.forEach { business ->
                merged[business.source.id + ":" + business.id] = business
            }
        }

        if (merged.isNotEmpty()) return merged.values.toList()

        val cityFallback = cache.get(BusinessInventoryCache.key("İstanbul", null, query))
        if (cityFallback.isNotEmpty()) return BusinessDeduplication.deduplicate(cityFallback)

        if (query.isNotBlank()) {
            return runCatching {
                alternative.fetchValidated(query, "İstanbul", null)
            }.getOrDefault(emptyList())
        }

        return emptyList()
    }
}

private class BusinessInventoryCache(
    context: Context,
) {
    private val preferences = context.getSharedPreferences("business_inventory_cache", Context.MODE_PRIVATE)

    fun get(key: String): List<VerifiedBusiness> {
        val raw = preferences.getString(key, null) ?: return emptyList()
        return runCatching {
            val root = JSONObject(raw)
            val records = root.optJSONArray("records") ?: JSONArray()
            buildList {
                for (i in 0 until records.length()) {
                    val item = records.optJSONObject(i) ?: continue
                    parse(item)?.let(::add)
                }
            }
        }.getOrDefault(emptyList())
    }

    fun put(key: String, records: List<VerifiedBusiness>) {
        val limited = records.distinctBy { it.source.id + ":" + it.id }.take(5_000)
        val array = JSONArray()
        limited.forEach { array.put(serialize(it)) }
        val root = JSONObject()
            .put("savedAt", System.currentTimeMillis())
            .put("records", array)
        preferences.edit().putString(key, root.toString()).apply()
    }

    fun ageMs(key: String): Long {
        val raw = preferences.getString(key, null) ?: return Long.MAX_VALUE
        return runCatching {
            System.currentTimeMillis() - JSONObject(raw).optLong("savedAt", 0L)
        }.getOrDefault(Long.MAX_VALUE)
    }

    private fun serialize(business: VerifiedBusiness): JSONObject =
        JSONObject()
            .put("id", business.id)
            .put("name", business.name)
            .put("city", business.city)
            .put("district", business.district)
            .put("neighborhood", business.neighborhood)
            .put("sourceId", business.source.id)
            .put("sourceName", business.source.name)
            .put("sourcePublisher", business.source.publisher)
            .put("sourceTerms", business.source.licenseOrTerms)
            .put("sourceUrl", business.source.sourceUrl)
            .put("sourceVerifiedAt", business.source.lastVerifiedAtEpochMs)
            .put("verifiedAt", business.verifiedAtEpochMs)
            .put("latitude", business.latitude)
            .put("longitude", business.longitude)
            .put("category", business.category)
            .put("address", business.address)
            .put("phone", business.phone)
            .put("website", business.website)
            .put("openingHours", business.openingHours)
            .put("menuUrl", business.menuUrl)
            .put("menuText", business.menuText)

    private fun parse(item: JSONObject): VerifiedBusiness? = runCatching {
        VerifiedBusiness(
            id = item.getString("id"),
            name = item.getString("name"),
            city = item.getString("city"),
            district = item.getString("district"),
            neighborhood = item.optString("neighborhood").takeIf { it.isNotBlank() && it != "null" },
            source = DataSourceDescriptor(
                id = item.getString("sourceId"),
                name = item.getString("sourceName"),
                publisher = item.getString("sourcePublisher"),
                licenseOrTerms = item.getString("sourceTerms"),
                sourceUrl = item.getString("sourceUrl"),
                lastVerifiedAtEpochMs = item.getLong("sourceVerifiedAt"),
            ),
            verifiedAtEpochMs = item.getLong("verifiedAt"),
            latitude = item.optDouble("latitude").takeUnless { it.isNaN() },
            longitude = item.optDouble("longitude").takeUnless { it.isNaN() },
            category = item.optString("category").takeIf { it.isNotBlank() && it != "null" },
            address = item.optString("address").takeIf { it.isNotBlank() && it != "null" },
            phone = item.optString("phone").takeIf { it.isNotBlank() && it != "null" },
            website = item.optString("website").takeIf { it.isNotBlank() && it != "null" },
            openingHours = item.optString("openingHours").takeIf { it.isNotBlank() && it != "null" },
            menuUrl = item.optString("menuUrl").takeIf { it.isNotBlank() && it != "null" },
            menuText = item.optString("menuText").takeIf { it.isNotBlank() && it != "null" },
        )
    }.getOrNull()

    companion object {
        private const val CACHE_SCHEMA = "v2"

        fun key(city: String, district: String?, query: String): String {
            val raw = listOf(CACHE_SCHEMA, city, district.orEmpty(), query.ifBlank { "*" })
                .joinToString("|")
                .lowercase()
                .map { if (it.isLetterOrDigit()) it else '_' }
                .joinToString("")
                .take(100)
            return "inventory_" + raw
        }
    }
}

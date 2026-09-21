package com.lanu.globaldonuksatisradari.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

interface CoverageLocalCache {
    fun get(scope: CoverageScope, nowEpochMs: Long = System.currentTimeMillis()): List<VerifiedBusiness>
    fun put(
        scope: CoverageScope,
        businesses: List<VerifiedBusiness>,
        savedAtEpochMs: Long = System.currentTimeMillis(),
    )
}

class SharedPreferencesCoverageLocalCache(
    context: Context,
    private val maxAgeMs: Long = DEFAULT_MAX_AGE_MS,
) : CoverageLocalCache {

    init {
        require(maxAgeMs > 0) { "Önbellek süresi pozitif olmalı" }
    }

    private val preferences: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun get(scope: CoverageScope, nowEpochMs: Long): List<VerifiedBusiness> {
        val raw = preferences.getString(key(scope), null) ?: return emptyList()
        val envelope = runCatching { CoverageLocalCacheCodec.decode(raw) }.getOrNull() ?: return emptyList()
        if (nowEpochMs - envelope.savedAtEpochMs > maxAgeMs) {
            preferences.edit().remove(key(scope)).apply()
            return emptyList()
        }
        return envelope.businesses
    }

    override fun put(scope: CoverageScope, businesses: List<VerifiedBusiness>, savedAtEpochMs: Long) {
        if (businesses.isEmpty()) return
        preferences.edit()
            .putString(
                key(scope),
                CoverageLocalCacheCodec.encode(businesses.take(MAX_RECORDS_PER_SCOPE), savedAtEpochMs),
            )
            .apply()
    }

    private fun key(scope: CoverageScope): String =
        listOf(scope.country, scope.city, scope.district, scope.category)
            .joinToString("|")
            .lowercase()
            .map { if (it.isLetterOrDigit()) it else '_' }
            .joinToString("")
            .take(120)
            .let { "coverage_$it" }

    private companion object {
        const val PREFS_NAME = "coverage_local_cache"
        const val DEFAULT_MAX_AGE_MS = 24 * 60 * 60 * 1000L
        const val MAX_RECORDS_PER_SCOPE = 5_000
    }
}

data class CoverageCacheEnvelope(
    val savedAtEpochMs: Long,
    val businesses: List<VerifiedBusiness>,
)

internal object CoverageLocalCacheCodec {

    fun encode(businesses: List<VerifiedBusiness>, savedAtEpochMs: Long): String {
        require(savedAtEpochMs > 0) { "savedAtEpochMs doğrulanmalı" }
        val records = JSONArray()
        businesses.forEach { business ->
            records.put(
                JSONObject().apply {
                    put("id", business.id)
                    put("name", business.name)
                    put("city", business.city)
                    put("district", business.district)
                    put("neighborhood", business.neighborhood ?: JSONObject.NULL)
                    put("verifiedAt", business.verifiedAtEpochMs)
                    put("latitude", business.latitude ?: JSONObject.NULL)
                    put("longitude", business.longitude ?: JSONObject.NULL)
                    put("category", business.category ?: JSONObject.NULL)
                    put("address", business.address ?: JSONObject.NULL)
                    put("phone", business.phone ?: JSONObject.NULL)
                    put("website", business.website ?: JSONObject.NULL)
                    put("openingHours", business.openingHours ?: JSONObject.NULL)
                    put("menuUrl", business.menuUrl ?: JSONObject.NULL)
                    put("menuText", business.menuText ?: JSONObject.NULL)
                    put("source", JSONObject().apply {
                        put("id", business.source.id)
                        put("name", business.source.name)
                        put("publisher", business.source.publisher)
                        put("licenseOrTerms", business.source.licenseOrTerms)
                        put("sourceUrl", business.source.sourceUrl)
                        put("lastVerifiedAtEpochMs", business.source.lastVerifiedAtEpochMs)
                    })
                },
            )
        }
        return JSONObject()
            .put("savedAtEpochMs", savedAtEpochMs)
            .put("records", records)
            .toString()
    }

    fun decode(raw: String): CoverageCacheEnvelope {
        val root = JSONObject(raw)
        val savedAtEpochMs = root.getLong("savedAtEpochMs")
        val records = root.optJSONArray("records") ?: JSONArray()
        val businesses = buildList {
            for (index in 0 until records.length()) {
                val item = records.optJSONObject(index) ?: continue
                val source = item.optJSONObject("source") ?: continue
                add(
                    VerifiedBusiness(
                        id = item.getString("id"),
                        name = item.getString("name"),
                        city = item.getString("city"),
                        district = item.getString("district"),
                        neighborhood = optionalString(item, "neighborhood"),
                        verifiedAtEpochMs = item.getLong("verifiedAt"),
                        latitude = optionalDouble(item, "latitude"),
                        longitude = optionalDouble(item, "longitude"),
                        category = optionalString(item, "category"),
                        address = optionalString(item, "address"),
                        phone = optionalString(item, "phone"),
                        website = optionalString(item, "website"),
                        openingHours = optionalString(item, "openingHours"),
                        menuUrl = optionalString(item, "menuUrl"),
                        menuText = optionalString(item, "menuText"),
                        source = DataSourceDescriptor(
                            id = source.getString("id"),
                            name = source.getString("name"),
                            publisher = source.getString("publisher"),
                            licenseOrTerms = source.getString("licenseOrTerms"),
                            sourceUrl = source.getString("sourceUrl"),
                            lastVerifiedAtEpochMs = source.getLong("lastVerifiedAtEpochMs"),
                        ),
                    ),
                )
            }
        }
        return CoverageCacheEnvelope(savedAtEpochMs, businesses)
    }

    private fun optionalString(item: JSONObject, key: String): String? =
        item.optString(key).takeIf { it.isNotBlank() && it != "null" }

    private fun optionalDouble(item: JSONObject, key: String): Double? =
        if (item.isNull(key)) null else item.optDouble(key).takeUnless { it.isNaN() }
}

internal object CoverageResultMerger {
    fun merge(
        scans: List<CoverageScanResult>,
        localCache: CoverageLocalCache,
        nowEpochMs: Long,
    ): List<VerifiedBusiness> {
        val merged = mutableListOf<VerifiedBusiness>()

        scans.forEach { scan ->
            when {
                scan.businesses.isNotEmpty() -> {
                    localCache.put(scan.scope, scan.businesses, nowEpochMs)
                    merged += scan.businesses
                }

                scan.attempts.isNotEmpty() && scan.attempts.all { !it.success } -> {
                    merged += localCache.get(scan.scope, nowEpochMs)
                }
            }
        }

        return BusinessDeduplication.deduplicateCrossSource(merged)
    }
}

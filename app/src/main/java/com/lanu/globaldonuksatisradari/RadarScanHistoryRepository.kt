package com.lanu.globaldonuksatisradari

import android.content.Context
import com.lanu.globaldonuksatisradari.data.BusinessDeduplication
import com.lanu.globaldonuksatisradari.data.VerifiedBusiness
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

data class RadarScanDelta(
    val isFirstScan: Boolean,
    val previousCount: Int,
    val currentCount: Int,
    val newBusinessKeys: Set<String>,
    val scannedAtEpochMs: Long,
) {
    val newCount: Int get() = newBusinessKeys.size
}

class RadarScanHistoryRepository(context: Context) {
    private val preferences =
        context.applicationContext.getSharedPreferences("radar_scan_history", Context.MODE_PRIVATE)

    fun compareAndRecord(
        city: String,
        district: String?,
        neighborhood: String? = null,
        query: String,
        records: List<VerifiedBusiness>,
        nowEpochMs: Long = System.currentTimeMillis(),
    ): RadarScanDelta {
        val key = scopeKey(city, district, neighborhood, query)
        val currentKeys = records
            .map(::businessKey)
            .toSet()

        val previous = preferences.getString(key, null)
            ?.let { runCatching { JSONObject(it) }.getOrNull() }
        val previousScanKeys = previous
            ?.optJSONArray("lastKeys")
            ?.toStringSet()
            ?: previous
                ?.optJSONArray("businessKeys")
                ?.toStringSet()
                .orEmpty()
        val seenKeys = previous
            ?.optJSONArray("seenKeys")
            ?.toStringSet()
            ?: previousScanKeys

        val delta = RadarScanDelta(
            isFirstScan = previous == null,
            previousCount = previousScanKeys.size,
            currentCount = currentKeys.size,
            newBusinessKeys = if (previous == null) emptySet() else currentKeys - seenKeys,
            scannedAtEpochMs = nowEpochMs,
        )

        val payload = JSONObject()
            .put("savedAtEpochMs", nowEpochMs)
            .put("lastKeys", JSONArray().apply { currentKeys.sorted().forEach(::put) })
            .put("seenKeys", JSONArray().apply { (seenKeys + currentKeys).sorted().forEach(::put) })
        preferences.edit().putString(key, payload.toString()).apply()

        return delta
    }

    fun clear() {
        preferences.edit().clear().apply()
    }

    companion object {
        fun businessKey(business: VerifiedBusiness): String =
            business.source.id + ":" + business.id

        private fun scopeKey(
            city: String,
            district: String?,
            neighborhood: String?,
            query: String,
        ): String {
            val normalizedCity = BusinessDeduplication.normalizeForComparison(city)
            val normalizedDistrict = district
                ?.takeIf { it.isNotBlank() && it != "Tümü" }
                ?.let(BusinessDeduplication::normalizeForComparison)
                .orEmpty()
            val normalizedNeighborhood = neighborhood
                ?.takeIf { it.isNotBlank() && it != "Tümü" }
                ?.let(BusinessDeduplication::normalizeForComparison)
                .orEmpty()
            val normalizedQuery = BusinessDeduplication.normalizeForComparison(query)
            return "scope:" + listOf(
                normalizedCity,
                normalizedDistrict,
                normalizedNeighborhood,
                normalizedQuery,
            )
                .joinToString("|")
                .lowercase(Locale.ROOT)
        }

        private fun JSONArray.toStringSet(): Set<String> = buildSet {
            for (index in 0 until length()) {
                optString(index).takeIf(String::isNotBlank)?.let(::add)
            }
        }
    }
}

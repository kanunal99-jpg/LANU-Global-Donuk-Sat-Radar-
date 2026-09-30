package com.lanu.globaldonuksatisradari.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Independent neighborhood catalog. Neighborhood choices must not depend on business-search results.
 * Primary: OSM/Overpass administrative boundaries. Fallback: last verified local cache. Safe default: empty list.
 */
class NeighborhoodCatalogRepository(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("neighborhood_catalog_cache", Context.MODE_PRIVATE)
    private val endpoints = listOf(
        OverpassBusinessSource.BASE_URL,
        *OverpassBusinessSource.FALLBACK_URLS.toTypedArray(),
    )

    suspend fun getNeighborhoods(city: String, district: String): List<String> = withContext(Dispatchers.IO) {
        if (city.isBlank() || district.isBlank() || district.equals("Tümü", true)) return@withContext emptyList()
        val key = "neighborhoods:" + BusinessDeduplication.normalizeForComparison(city) + ":" +
            BusinessDeduplication.normalizeForComparison(district)
        readCache(key)?.takeIf { it.isNotEmpty() }?.let { return@withContext it }
        for (endpoint in endpoints.distinct()) {
            val result = runCatching { fetch(endpoint, city, district) }.getOrNull().orEmpty()
            if (result.isNotEmpty()) {
                writeCache(key, result)
                return@withContext result
            }
        }
        return@withContext readStaleCache(key).orEmpty()
    }

    private fun fetch(endpoint: String, city: String, district: String): List<String> {
        fun q(value: String) = value.replace("\\", "\\\\").replace("\"", "\\\"")
        val query = """[out:json][timeout:45];area["name"="${q(city)}"]["boundary"="administrative"]["admin_level"="4"]->.cityArea;relation(area.cityArea)["name"="${q(district)}"]["boundary"="administrative"]->.district;map_to_area .district->.districtArea;(relation(area.districtArea)["boundary"="administrative"]["name"];way(area.districtArea)["place"~"neighbourhood|quarter|suburb"]["name"];node(area.districtArea)["place"~"neighbourhood|quarter|suburb"]["name"];);out tags;"""
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 60_000
            doOutput = true
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
            setRequestProperty("User-Agent", "LANU-Global-Donuk-Satis-Radari/0.4")
        }
        try {
            val encoded = URLEncoder.encode(query, Charsets.UTF_8.name())
            connection.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write("data=$encoded") }
            if (connection.responseCode !in 200..299) return emptyList()
            val payload = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val elements = JSONObject(payload).optJSONArray("elements") ?: JSONArray()
            return buildList {
                for (i in 0 until elements.length()) {
                    val tags = elements.optJSONObject(i)?.optJSONObject("tags") ?: continue
                    val name = tags.optString("name").trim()
                    if (name.isNotBlank() && !name.equals(district, true) && !name.equals(city, true)) add(name)
                }
            }.distinct().sortedWith(String.CASE_INSENSITIVE_ORDER)
        } finally {
            connection.disconnect()
        }
    }

    private fun readCache(key: String): List<String>? {
        val raw = preferences.getString(key, null) ?: return null
        val root = runCatching { JSONObject(raw) }.getOrNull() ?: return null
        if (System.currentTimeMillis() - root.optLong("savedAt", 0L) > 30L * 24 * 60 * 60 * 1000) return null
        return readArray(root)
    }

    private fun readStaleCache(key: String): List<String>? {
        val raw = preferences.getString(key, null) ?: return null
        val root = runCatching { JSONObject(raw) }.getOrNull() ?: return null
        return readArray(root)
    }

    private fun readArray(root: JSONObject): List<String> {
        val array = root.optJSONArray("neighborhoods") ?: return emptyList()
        return buildList {
            for (i in 0 until array.length()) array.optString(i).trim().takeIf(String::isNotBlank)?.let(::add)
        }.distinct().sortedWith(String.CASE_INSENSITIVE_ORDER)
    }

    private fun writeCache(key: String, neighborhoods: List<String>) {
        val array = JSONArray().apply { neighborhoods.forEach(::put) }
        preferences.edit().putString(
            key,
            JSONObject().put("savedAt", System.currentTimeMillis()).put("neighborhoods", array).toString(),
        ).apply()
    }
}

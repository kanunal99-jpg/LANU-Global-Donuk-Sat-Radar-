package com.lanu.globaldonuksatisradari.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

/** Discovers Turkish district (admin_level=6) names from OSM with real endpoint fallback. */
class DistrictCatalogRepository(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("district_catalog_cache", Context.MODE_PRIVATE)
    private val endpoints = listOf(
        OverpassBusinessSource.BASE_URL,
        *OverpassBusinessSource.FALLBACK_URLS.toTypedArray(),
    )

    suspend fun getDistricts(city: String, fallback: List<String> = emptyList()): List<String> = withContext(Dispatchers.IO) {
        if (city.isBlank()) return@withContext fallback
        val key = "districts:" + BusinessDeduplication.normalizeForComparison(city)
        readCache(key)?.takeIf { it.isNotEmpty() }?.let { return@withContext it }
        for (endpoint in endpoints.distinct()) {
            val result = runCatching { fetch(endpoint, city) }.getOrNull().orEmpty()
            if (result.isNotEmpty()) {
                writeCache(key, result)
                return@withContext result
            }
        }
        return@withContext fallback.distinct().sortedWith(String.CASE_INSENSITIVE_ORDER)
    }

    private fun fetch(endpoint: String, city: String): List<String> {
        val areaName = city.replace("\\", "\\\\").replace("\"", "\\\"")
        val query = """[out:json][timeout:45];area[\"name\"=\"$areaName\"][\"boundary\"=\"administrative\"][\"admin_level\"=\"4\"]->.cityArea;relation(area.cityArea)[\"boundary\"=\"administrative\"][\"admin_level\"=\"6\"][\"name\"]->.districts;out tags;"""
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 60_000
            doOutput = true
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
            setRequestProperty("User-Agent", "LANU-Global-Donuk-Satis-Radari/0.3")
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
                    tags.optString("name").trim().takeIf { it.isNotBlank() }?.let(::add)
                }
            }.distinct().sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it })
        } finally {
            connection.disconnect()
        }
    }

    private fun readCache(key: String): List<String>? {
        val raw = preferences.getString(key, null) ?: return null
        val root = runCatching { JSONObject(raw) }.getOrNull() ?: return null
        if (System.currentTimeMillis() - root.optLong("savedAt", 0L) > 7L * 24 * 60 * 60 * 1000) return null
        val array = root.optJSONArray("districts") ?: return null
        return buildList { for (i in 0 until array.length()) array.optString(i).takeIf(String::isNotBlank)?.let(::add) }
    }

    private fun writeCache(key: String, districts: List<String>) {
        val array = JSONArray().apply { districts.forEach(::put) }
        preferences.edit().putString(key, JSONObject().put("savedAt", System.currentTimeMillis()).put("districts", array).toString()).apply()
    }
}
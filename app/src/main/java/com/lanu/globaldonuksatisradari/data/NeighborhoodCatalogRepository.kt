package com.lanu.globaldonuksatisradari.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class NeighborhoodCatalogRepository(context: Context) {
    private val preferences =
        context.applicationContext.getSharedPreferences("neighborhood_catalog_cache", Context.MODE_PRIVATE)
    private val primary = TurkiyeAdministrativeApi()

    private val endpoints = listOf(
        OverpassBusinessSource.BASE_URL,
        *OverpassBusinessSource.FALLBACK_URLS.toTypedArray(),
    )

    suspend fun getNeighborhoods(city: String, district: String): List<String> = withContext(Dispatchers.IO) {
        if (city.isBlank() || district.isBlank() || district == "Tümü") return@withContext emptyList()

        val key = "neighborhoods:v2:" +
            BusinessDeduplication.normalizeForComparison(city) + ":" +
            BusinessDeduplication.normalizeForComparison(district)

        readCache(key)?.takeIf { it.isNotEmpty() }?.let { return@withContext it }

        try {
            val primaryResult = primary.neighborhoods(city, district)
            if (primaryResult.isNotEmpty()) {
                writeCache(key, primaryResult)
                return@withContext primaryResult
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.w("LanuLocation", "TurkiyeAPI mahalle listesi alınamadı: $city/$district", error)
        }

        for (endpoint in endpoints.distinct()) {
            currentCoroutineContext().ensureActive()
            val result = try {
                fetch(endpoint, city, district)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.w("LanuLocation", "OSM mahalle fallback başarısız: $city/$district @ $endpoint", error)
                emptyList()
            }
            if (result.isNotEmpty()) {
                writeCache(key, result)
                return@withContext result
            }
        }

        emptyList()
    }

    private fun fetch(endpoint: String, city: String, district: String): List<String> {
        val cityName = escapeOverpass(city)
        val districtName = escapeOverpass(district)
        val query = """
            [out:json][timeout:50];
            area["name"="$cityName"]["boundary"="administrative"]["admin_level"="4"]->.cityArea;
            relation(area.cityArea)["name"="$districtName"]["boundary"="administrative"]["admin_level"~"6|7|8"]->.districtRel;
            .districtRel map_to_area ->.districtArea;
            (
              relation(area.districtArea)["boundary"="administrative"]["admin_level"~"8|9|10"]["name"];
              nwr(area.districtArea)["place"~"neighbourhood|quarter|suburb|village"]["name"];
            );
            out tags;
        """.trimIndent()

        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 65_000
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
                for (index in 0 until elements.length()) {
                    val name = elements.optJSONObject(index)
                        ?.optJSONObject("tags")
                        ?.optString("name")
                        ?.trim()
                        .orEmpty()
                    if (name.isNotBlank()) add(name)
                }
            }
                .distinctBy { BusinessDeduplication.normalizeForComparison(it) }
                .sortedWith(String.CASE_INSENSITIVE_ORDER)
        } finally {
            connection.disconnect()
        }
    }

    private fun escapeOverpass(value: String): String =
        value.replace("\\", "\\\\").replace("\"", "\\\"")

    private fun readCache(key: String): List<String>? {
        val raw = preferences.getString(key, null) ?: return null
        val root = runCatching { JSONObject(raw) }.getOrNull() ?: return null
        if (System.currentTimeMillis() - root.optLong("savedAt", 0L) > CACHE_TTL_MS) return null
        val array = root.optJSONArray("items") ?: return null
        return buildList {
            for (index in 0 until array.length()) {
                array.optString(index).trim().takeIf(String::isNotBlank)?.let(::add)
            }
        }
    }

    private fun writeCache(key: String, values: List<String>) {
        val array = JSONArray().apply { values.forEach(::put) }
        preferences.edit()
            .putString(
                key,
                JSONObject()
                    .put("savedAt", System.currentTimeMillis())
                    .put("items", array)
                    .toString(),
            )
            .apply()
    }

    companion object {
        private const val CACHE_TTL_MS = 30L * 24 * 60 * 60 * 1000
    }
}

package com.lanu.globaldonuksatisradari.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

internal data class TurkeyDistrict(
    val id: Int,
    val provinceId: Int,
    val name: String,
)

internal class TurkiyeAdministrativeApi(
    private val baseUrl: String = BASE_URL,
) {
    suspend fun districts(city: String): List<TurkeyDistrict> = withContext(Dispatchers.IO) {
        val provinceId = provinceId(city) ?: return@withContext emptyList()
        val payload = getJson(
            "/districts?provinceId=$provinceId&limit=1000&fields=id,name,provinceId",
        )
        parseDistricts(payload, provinceId)
    }

    suspend fun neighborhoods(city: String, district: String): List<String> = withContext(Dispatchers.IO) {
        val provinceId = provinceId(city) ?: return@withContext emptyList()
        val districtRecord = districts(city)
            .firstOrNull {
                BusinessDeduplication.normalizeForComparison(it.name) ==
                    BusinessDeduplication.normalizeForComparison(district)
            }
            ?: return@withContext emptyList()

        val payload = getJson(
            "/districts/${districtRecord.id}/neighborhoods?limit=1000&fields=id,name,districtId,provinceId",
        )
        parseNeighborhoods(payload)
    }

    private fun provinceId(city: String): Int? {
        val encoded = URLEncoder.encode(city.trim(), Charsets.UTF_8.name())
        val payload = getJson("/provinces?search=$encoded&limit=20&fields=id,name")
        return parseProvinceId(payload, city)
    }

    private fun getJson(path: String): String {
        require(baseUrl.startsWith("https://")) { "TurkiyeAPI endpoint HTTPS olmalı" }
        val connection = (URL(baseUrl + path).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 20_000
            setRequestProperty("Accept", "application/json")
            setRequestProperty(
                "User-Agent",
                "LANU-Global-Donuk-Satis-Radari/0.3 (+https://github.com/kanunal99-jpg/LANU-Global-Donuk-Sat-Radar-)",
            )
        }
        try {
            val code = connection.responseCode
            if (code !in 200..299) {
                throw IOException("TurkiyeAPI HTTP $code")
            }
            return connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        const val BASE_URL = "https://api.turkiyeapi.dev/v2"

        internal fun parseProvinceId(payload: String, city: String): Int? {
            val root = JSONObject(payload)
            val data = root.optJSONArray("data") ?: return null
            val normalizedCity = BusinessDeduplication.normalizeForComparison(city)
            for (index in 0 until data.length()) {
                val item = data.optJSONObject(index) ?: continue
                val name = item.optString("name").trim()
                if (BusinessDeduplication.normalizeForComparison(name) == normalizedCity) {
                    return item.optInt("id").takeIf { it > 0 }
                }
            }
            return null
        }

        internal fun parseDistricts(payload: String, provinceId: Int): List<TurkeyDistrict> {
            val data = JSONObject(payload).optJSONArray("data") ?: return emptyList()
            return buildList {
                for (index in 0 until data.length()) {
                    val item = data.optJSONObject(index) ?: continue
                    val id = item.optInt("id")
                    val itemProvinceId = item.optInt("provinceId")
                    val name = item.optString("name").trim()
                    if (id > 0 && itemProvinceId == provinceId && name.isNotBlank()) {
                        add(TurkeyDistrict(id = id, provinceId = itemProvinceId, name = name))
                    }
                }
            }
                .distinctBy { BusinessDeduplication.normalizeForComparison(it.name) }
                .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
        }

        internal fun parseNeighborhoods(payload: String): List<String> {
            val data = JSONObject(payload).optJSONArray("data") ?: return emptyList()
            return buildList {
                for (index in 0 until data.length()) {
                    data.optJSONObject(index)
                        ?.optString("name")
                        ?.trim()
                        ?.takeIf(String::isNotBlank)
                        ?.let(::add)
                }
            }
                .distinctBy(BusinessDeduplication::normalizeForComparison)
                .sortedWith(String.CASE_INSENSITIVE_ORDER)
        }
    }
}

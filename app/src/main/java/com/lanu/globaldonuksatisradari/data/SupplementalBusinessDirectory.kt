package com.lanu.globaldonuksatisradari.data

import android.content.Context
import org.json.JSONObject

/**
 * Small, vetted correction layer for real businesses that are missing from the bulk/open
 * providers. This is intentionally data-driven so a coverage gap can be corrected without
 * hard-coding one business in repository logic.
 */
class SupplementalBusinessDirectory(
    context: Context,
) {
    private val appContext = context.applicationContext

    private val records: List<VerifiedBusiness> by lazy {
        runCatching {
            appContext.assets.open(ASSET_NAME).bufferedReader().use { reader ->
                SupplementalBusinessParser.parse(reader.readText())
            }
        }.getOrDefault(emptyList())
    }

    fun search(
        query: String,
        city: String,
        district: String?,
        neighborhood: String?,
    ): List<VerifiedBusiness> =
        records.filter { business ->
            SupplementalBusinessParser.matches(
                business = business,
                query = query,
                city = city,
                district = district,
                neighborhood = neighborhood,
            )
        }

    private companion object {
        const val ASSET_NAME = "supplemental_businesses.json"
    }
}

internal object SupplementalBusinessParser {
    fun parse(payload: String): List<VerifiedBusiness> {
        val root = JSONObject(payload)
        require(root.getInt("schemaVersion") == 1) {
            "Desteklenmeyen supplemental işletme şema sürümü."
        }
        val array = root.getJSONArray("records")
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                val sourceJson = item.getJSONObject("source")
                val business = VerifiedBusiness(
                    id = item.getString("id").trim(),
                    name = item.getString("name").trim(),
                    city = item.getString("city").trim(),
                    district = item.getString("district").trim(),
                    neighborhood = optionalString(item, "neighborhood"),
                    source = DataSourceDescriptor(
                        id = sourceJson.getString("id").trim(),
                        name = sourceJson.getString("name").trim(),
                        publisher = sourceJson.getString("publisher").trim(),
                        licenseOrTerms = sourceJson.getString("licenseOrTerms").trim(),
                        sourceUrl = sourceJson.getString("sourceUrl").trim(),
                        lastVerifiedAtEpochMs = sourceJson.getLong("lastVerifiedAtEpochMs"),
                    ),
                    verifiedAtEpochMs = item.getLong("verifiedAtEpochMs"),
                    latitude = optionalDouble(item, "latitude"),
                    longitude = optionalDouble(item, "longitude"),
                    category = optionalString(item, "category"),
                    address = optionalString(item, "address"),
                    phone = optionalString(item, "phone"),
                    website = optionalString(item, "website"),
                    openingHours = optionalString(item, "openingHours"),
                )
                VerifiedBusinessValidator.validate(business).getOrNull()?.let(::add)
            }
        }
    }

    fun matches(
        business: VerifiedBusiness,
        query: String,
        city: String,
        district: String?,
        neighborhood: String?,
    ): Boolean {
        if (normalize(business.city) != normalize(city)) return false

        val wantedDistrict = district
            ?.takeUnless { it.isBlank() || it.equals("Tümü", ignoreCase = true) }
            ?.let(::normalizeDistrict)
        if (wantedDistrict != null && normalizeDistrict(business.district) != wantedDistrict) {
            return false
        }

        val wantedNeighborhood = neighborhood
            ?.takeUnless { it.isBlank() || it.equals("Tümü", ignoreCase = true) }
            ?.let(::normalizeNeighborhood)
        if (wantedNeighborhood != null &&
            normalizeNeighborhood(business.neighborhood.orEmpty()) != wantedNeighborhood
        ) {
            return false
        }

        val wantedQuery = normalize(query)
        if (wantedQuery.isBlank()) return true
        val haystack = listOfNotNull(
            business.name,
            business.category,
            business.address,
            business.phone,
        ).joinToString(" ")
        return normalize(haystack).contains(wantedQuery)
    }

    private fun optionalString(item: JSONObject, key: String): String? =
        item.optString(key).trim().takeIf { it.isNotBlank() && it != "null" }

    private fun optionalDouble(item: JSONObject, key: String): Double? =
        if (item.has(key) && !item.isNull(key)) item.optDouble(key).takeUnless(Double::isNaN) else null

    private fun normalize(value: String): String =
        BusinessDeduplication.normalizeForComparison(value.replace('_', ' ').replace('-', ' '))

    private fun normalizeDistrict(value: String): String =
        normalize(value)
            .removeSuffix(" district")
            .removeSuffix(" ilcesi")
            .removeSuffix(" ilce")
            .trim()

    private fun normalizeNeighborhood(value: String): String =
        normalize(value)
            .removeSuffix(" mahallesi")
            .removeSuffix(" mah")
            .removeSuffix(" neighborhood")
            .trim()
}

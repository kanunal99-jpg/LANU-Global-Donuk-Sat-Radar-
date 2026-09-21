package com.lanu.globaldonuksatisradari.data

import java.util.Locale

/** Exact + conservative cross-source deduplication. */
object BusinessDeduplication {
    fun exactIdentityKey(business: VerifiedBusiness): String =
        business.source.id + ":" + business.id

    fun deduplicate(records: List<VerifiedBusiness>): List<VerifiedBusiness> =
        records.distinctBy(::exactIdentityKey)

    fun deduplicateCrossSource(records: List<VerifiedBusiness>): List<VerifiedBusiness> {
        val selected = linkedMapOf<String, VerifiedBusiness>()
        deduplicate(records).forEach { business ->
            val key = crossSourceIdentityKey(business)
            val existing = selected[key]
            if (existing == null || richness(business) > richness(existing)) {
                selected[key] = business
            }
        }
        return selected.values.toList()
    }

    private fun crossSourceIdentityKey(business: VerifiedBusiness): String {
        val phone = business.phone?.filter(Char::isDigit).orEmpty()
        if (phone.length >= 7) return "phone:" + phone.takeLast(10)
        val name = normalizeForComparison(business.name)
        val district = normalizeForComparison(business.district)
        val address = normalizeForComparison(business.address.orEmpty())
        val coordinates = if (business.latitude != null && business.longitude != null) {
            "geo:" + String.format(Locale.ROOT, "%.3f", business.latitude) + ":" + String.format(Locale.ROOT, "%.3f", business.longitude)
        } else {
            "addr:" + address
        }
        return "name:" + name + "|district:" + district + "|" + coordinates
    }

    private fun richness(business: VerifiedBusiness): Int =
        BusinessQualityEvaluator.evaluate(business).score

    fun normalizeForComparison(value: String): String = value
        .trim()
        .replace('İ', 'I')
        .lowercase(Locale.ROOT)
        .replace("\u0307", "")
        .replace('ı', 'i')
        .replace('ğ', 'g')
        .replace('ü', 'u')
        .replace('ş', 's')
        .replace('ö', 'o')
        .replace('ç', 'c')
        .replace(Regex("\\s+"), " ")
}
package com.lanu.globaldonuksatisradari.data

import java.util.Locale

/** Conservative deduplication: merge only identical source + stable record ID. */
object BusinessDeduplication {
    fun exactIdentityKey(business: VerifiedBusiness): String =
        "${business.source.id}:${business.id}"

    fun deduplicate(records: List<VerifiedBusiness>): List<VerifiedBusiness> =
        records.distinctBy(::exactIdentityKey)

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

package com.lanu.globaldonuksatisradari.data

/** Conservative deduplication: merge only identical source + stable record ID. */
object BusinessDeduplication {
    fun exactIdentityKey(business: VerifiedBusiness): String =
        "${business.source.id}:${business.id}"

    fun deduplicate(records: List<VerifiedBusiness>): List<VerifiedBusiness> =
        records.distinctBy(::exactIdentityKey)

    fun normalizeForComparison(value: String): String = value
        .trim()
        .lowercase()
        .replace('ı', 'i')
        .replace('ğ', 'g')
        .replace('ü', 'u')
        .replace('ş', 's')
        .replace('ö', 'o')
        .replace('ç', 'c')
        .replace(Regex("\\s+"), " ")
}

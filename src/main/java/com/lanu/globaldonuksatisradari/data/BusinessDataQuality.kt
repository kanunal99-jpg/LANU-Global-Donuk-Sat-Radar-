package com.lanu.globaldonuksatisradari.data

data class BusinessQuality(
    val score: Int,
    val populatedFields: Int,
    val totalFields: Int,
    val missingFields: List<String>,
    val evidenceBackedFields: Int,
    val evidenceCoveragePercent: Int,
) {
    val completenessPercent: Int
        get() = if (totalFields == 0) 0 else populatedFields * 100 / totalFields

    val label: String
        get() = when {
            score >= 85 -> "Yüksek"
            score >= 65 -> "İyi"
            score >= 45 -> "Orta"
            else -> "Eksik"
        }
}

/**
 * Ölçülebilir veri kalitesi: uygulama kaynakta olmayan bilgiyi uydurmaz.
 *
 * Evidence coverage is deliberately separate from completeness:
 * a field may be populated but still be unproven if a future source adapter
 * bypasses provenance creation.
 */
object BusinessQualityEvaluator {
    fun evaluate(business: VerifiedBusiness): BusinessQuality {
        val fields = listOf(
            "İşletme adı" to business.name,
            "İl" to business.city,
            "İlçe" to business.district,
            "Adres" to business.address,
            "Telefon" to business.phone,
            "Web sitesi" to business.website,
            "Koordinat" to if (business.latitude != null && business.longitude != null) "ok" else null,
            "Kategori" to business.category,
            "Çalışma saatleri" to business.openingHours,
            "Menü" to if (!business.menuUrl.isNullOrBlank() || !business.menuText.isNullOrBlank()) "ok" else null,
            "NACE" to business.naceCode,
            "Hukuki unvan" to business.legalName,
            "Ticaret sicil no" to business.tradeRegistryNumber,
        )
        val missing = fields.filter { it.second.isNullOrBlank() }.map { it.first }
        var score = 0
        score += if (business.name.isNotBlank()) 15 else 0
        score += if (business.city.isNotBlank()) 5 else 0
        score += if (business.district.isNotBlank()) 10 else 0
        score += if (!business.address.isNullOrBlank()) 12 else 0
        score += if (business.latitude != null && business.longitude != null) 18 else 0
        score += if (!business.phone.isNullOrBlank()) 12 else 0
        score += if (!business.website.isNullOrBlank()) 5 else 0
        score += if (!business.category.isNullOrBlank()) 5 else 0
        score += if (!business.openingHours.isNullOrBlank()) 3 else 0
        score += if (!business.menuUrl.isNullOrBlank() || !business.menuText.isNullOrBlank()) 5 else 0
        score += if (!business.naceCode.isNullOrBlank()) 4 else 0
        score += if (!business.legalName.isNullOrBlank()) 3 else 0
        score += if (!business.tradeRegistryNumber.isNullOrBlank()) 3 else 0

        val effectiveEvidence = business.effectiveEvidence()
        val evidenceFields = effectiveEvidence.map { it.field }.toSet()
        val evidenceBackedFields = fields.count { (label, value) ->
            value != null && value.toString().isNotBlank() && evidenceFields.contains(fieldKey(label))
        }
        val evidenceCoveragePercent =
            if (populatedFieldCount(fields) == 0) 0
            else evidenceBackedFields * 100 / populatedFieldCount(fields)

        return BusinessQuality(
            score = score.coerceIn(0, 100),
            populatedFields = populatedFieldCount(fields),
            totalFields = fields.size,
            missingFields = missing,
            evidenceBackedFields = evidenceBackedFields,
            evidenceCoveragePercent = evidenceCoveragePercent,
        )
    }

    private fun populatedFieldCount(fields: List<Pair<String, Any?>>): Int =
        fields.count { !it.second.isNullOrBlank() }

    private fun fieldKey(label: String): String = when (label) {
        "İşletme adı" -> "name"
        "İl" -> "city"
        "İlçe" -> "district"
        "Adres" -> "address"
        "Telefon" -> "phone"
        "Web sitesi" -> "website"
        "Koordinat" -> "latitude"
        "Kategori" -> "category"
        "Çalışma saatleri" -> "openingHours"
        "Menü" -> "menuUrl"
        "NACE" -> "naceCode"
        "Hukuki unvan" -> "legalName"
        "Ticaret sicil no" -> "tradeRegistryNumber"
        else -> label
    }
}

package com.lanu.globaldonuksatisradari.data

data class BusinessQuality(
    val score: Int,
    val populatedFields: Int,
    val totalFields: Int,
    val missingFields: List<String>,
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
        )
        val missing = fields.filter { it.second.isNullOrBlank() }.map { it.first }
        var score = 0
        score += if (business.name.isNotBlank()) 15 else 0
        score += if (business.city.isNotBlank()) 5 else 0
        score += if (business.district.isNotBlank()) 10 else 0
        score += if (!business.address.isNullOrBlank()) 15 else 0
        score += if (business.latitude != null && business.longitude != null) 20 else 0
        score += if (!business.phone.isNullOrBlank()) 15 else 0
        score += if (!business.website.isNullOrBlank()) 5 else 0
        score += if (!business.category.isNullOrBlank()) 5 else 0
        score += if (!business.openingHours.isNullOrBlank()) 3 else 0
        score += if (!business.menuUrl.isNullOrBlank() || !business.menuText.isNullOrBlank()) 7 else 0
        return BusinessQuality(
            score = score.coerceIn(0, 100),
            populatedFields = fields.size - missing.size,
            totalFields = fields.size,
            missingFields = missing,
        )
    }
}

package com.lanu.globaldonuksatisradari.data

object BusinessCategoryLabels {
    private val labels = mapOf(
        "restaurant" to "Restoran",
        "cafe" to "Kafe",
        "fast_food" to "Fast Food",
        "food_court" to "Yemek Alanı",
        "bar" to "Bar",
        "pub" to "Pub",
        "biergarten" to "Bira Bahçesi",
        "ice_cream" to "Dondurma",
        "catering" to "Catering",
        "caterer" to "Catering",
        "supermarket" to "Süpermarket",
        "convenience" to "Market",
        "food" to "Gıda Mağazası",
        "bakery" to "Fırın",
        "butcher" to "Kasap",
        "deli" to "Şarküteri",
        "greengrocer" to "Manav",
        "seafood" to "Balık / Deniz Ürünleri",
        "wholesale" to "Toptan Satış",
        "hotel" to "Otel",
        "hostel" to "Hostel",
        "motel" to "Motel",
        "guest_house" to "Konukevi / Pansiyon",
        "apartment" to "Apart / Konaklama",
        "marketplace" to "Pazar / Çarşı",
    )

    fun displayName(raw: String?): String? {
        val value = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return labels[value.lowercase()] ?: value
    }
}

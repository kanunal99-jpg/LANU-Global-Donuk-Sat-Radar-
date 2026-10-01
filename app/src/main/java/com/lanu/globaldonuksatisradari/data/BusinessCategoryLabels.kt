package com.lanu.globaldonuksatisradari.data

data class BusinessSearchCategory(
    val label: String,
    val query: String,
)

object BusinessCategoryLabels {
    private val labels = linkedMapOf(
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
        "internet_cafe" to "İnternet Kafe",
        "adult_gaming_centre" to "Oyun Salonu",
    )

    val searchCategories: List<BusinessSearchCategory> = listOf(
        BusinessSearchCategory("Restoran", "restaurant"),
        BusinessSearchCategory("Kafe", "cafe"),
        BusinessSearchCategory("Fast Food", "fast food"),
        BusinessSearchCategory("Yemek Alanı", "food_court"),
        BusinessSearchCategory("Bar", "bar"),
        BusinessSearchCategory("Pub", "pub"),
        BusinessSearchCategory("Bira Bahçesi", "biergarten"),
        BusinessSearchCategory("Dondurma", "ice_cream"),
        BusinessSearchCategory("Catering", "catering"),
        BusinessSearchCategory("Süpermarket", "supermarket"),
        BusinessSearchCategory("Market", "market"),
        BusinessSearchCategory("Gıda Mağazası", "food"),
        BusinessSearchCategory("Fırın", "bakery"),
        BusinessSearchCategory("Kasap", "butcher"),
        BusinessSearchCategory("Şarküteri", "deli"),
        BusinessSearchCategory("Manav", "greengrocer"),
        BusinessSearchCategory("Balık / Deniz Ürünleri", "seafood"),
        BusinessSearchCategory("Toptan Satış", "wholesale"),
        BusinessSearchCategory("Otel", "hotel"),
        BusinessSearchCategory("Hostel", "hostel"),
        BusinessSearchCategory("Motel", "motel"),
        BusinessSearchCategory("Konukevi / Pansiyon", "guest_house"),
        BusinessSearchCategory("Apart / Konaklama", "apartment"),
        BusinessSearchCategory("Pazar / Çarşı", "marketplace"),
        BusinessSearchCategory("İnternet Kafe", "internet cafe"),
        BusinessSearchCategory("Oyun Salonu", "adult_gaming_centre"),
    )

    val searchLabels: List<String>
        get() = searchCategories.map { it.label }

    fun searchQueryForLabel(label: String?): String? =
        searchCategories.firstOrNull { it.label.equals(label, ignoreCase = true) }?.query

    fun displayName(raw: String?): String? {
        val value = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return labels[value.lowercase()] ?: value
    }
}

package com.lanu.globaldonuksatisradari

object IstanbulRegionCatalog {
    const val ANATOLIA = "İstanbul Anadolu"
    const val EUROPE = "İstanbul Avrupa"

    val anatoliaDistricts = listOf(
        "Adalar", "Ataşehir", "Beykoz", "Çekmeköy", "Kadıköy", "Kartal", "Maltepe",
        "Pendik", "Sancaktepe", "Sultanbeyli", "Şile", "Tuzla", "Ümraniye", "Üsküdar",
    )

    val europeDistricts = listOf(
        "Arnavutköy", "Avcılar", "Bağcılar", "Bahçelievler", "Bakırköy", "Başakşehir",
        "Bayrampaşa", "Beşiktaş", "Beylikdüzü", "Beyoğlu", "Büyükçekmece", "Çatalca",
        "Esenler", "Esenyurt", "Eyüpsultan", "Fatih", "Gaziosmanpaşa", "Güngören",
        "Kağıthane", "Küçükçekmece", "Sarıyer", "Silivri", "Sultangazi", "Şişli", "Zeytinburnu",
    )

    fun salesRegions(): List<City> = listOf(
        City(ANATOLIA, anatoliaDistricts),
        City(EUROPE, europeDistricts),
    )

    fun withTurkeyCities(all: List<City>): List<City> =
        salesRegions() + all.filterNot { it.name.equals("İstanbul", ignoreCase = true) }

    fun queryCity(displayName: String): String = when (displayName) {
        ANATOLIA, EUROPE -> "İstanbul"
        else -> displayName
    }
}

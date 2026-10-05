package com.lanu.globaldonuksatisradari

data class CityCatalogEntry(
    val name: String,
    val fallbackDistricts: List<String> = emptyList(),
    val queryCityName: String = name,
    val restrictToFallbackDistricts: Boolean = false,
)

object TurkeyCityCatalog {
    val ALL: List<CityCatalogEntry> = listOf(
        "Adana", "Adıyaman", "Afyonkarahisar", "Ağrı", "Aksaray", "Amasya", "Ankara",
        "Antalya", "Ardahan", "Artvin", "Aydın", "Balıkesir", "Bartın", "Batman",
        "Bayburt", "Bilecik", "Bingöl", "Bitlis", "Bolu", "Burdur", "Bursa",
        "Çanakkale", "Çankırı", "Çorum", "Denizli", "Diyarbakır", "Düzce", "Edirne",
        "Elazığ", "Erzincan", "Erzurum", "Eskişehir", "Gaziantep", "Giresun",
        "Gümüşhane", "Hakkâri", "Hatay", "Iğdır", "Isparta", "İzmir",
        "Kahramanmaraş", "Karabük", "Karaman", "Kars", "Kastamonu", "Kayseri",
        "Kilis", "Kırıkkale", "Kırklareli", "Kırşehir", "Kocaeli", "Konya",
        "Kütahya", "Malatya", "Manisa", "Mardin", "Mersin", "Muğla", "Muş",
        "Nevşehir", "Niğde", "Ordu", "Osmaniye", "Rize", "Sakarya", "Samsun",
        "Siirt", "Sinop", "Sivas", "Şanlıurfa", "Şırnak", "Tekirdağ", "Tokat",
        "Trabzon", "Tunceli", "Uşak", "Van", "Yalova", "Yozgat", "Zonguldak",
    ).map {
        when (it) {
            "Ankara" -> CityCatalogEntry(it, listOf("Çankaya", "Keçiören", "Yenimahalle"))
            "İzmir" -> CityCatalogEntry(it, listOf("Konak", "Karşıyaka", "Bornova"))
            "Bursa" -> CityCatalogEntry(it, listOf("Nilüfer", "Osmangazi"))
            "Antalya" -> CityCatalogEntry(it, listOf("Muratpaşa", "Konyaaltı"))
            else -> CityCatalogEntry(it)
        }
    } + listOf(
        CityCatalogEntry(
            name = "İstanbul Anadolu",
            fallbackDistricts = IstanbulDistricts.ANATOLIAN,
            queryCityName = "İstanbul",
            restrictToFallbackDistricts = true,
        ),
        CityCatalogEntry(
            name = "İstanbul Avrupa",
            fallbackDistricts = IstanbulDistricts.EUROPEAN,
            queryCityName = "İstanbul",
            restrictToFallbackDistricts = true,
        ),
    )
}
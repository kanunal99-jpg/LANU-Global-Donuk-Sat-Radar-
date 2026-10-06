package com.lanu.globaldonuksatisradari

object IstanbulDistricts {
    val ANATOLIAN: List<String> = listOf(
        "Adalar",
        "Ataşehir",
        "Beykoz",
        "Çekmeköy",
        "Kadıköy",
        "Kartal",
        "Maltepe",
        "Pendik",
        "Sancaktepe",
        "Sultanbeyli",
        "Şile",
        "Tuzla",
        "Ümraniye",
        "Üsküdar",
    )

    val EUROPEAN: List<String> = listOf(
        "Arnavutköy",
        "Avcılar",
        "Bağcılar",
        "Bahçelievler",
        "Bakırköy",
        "Başakşehir",
        "Bayrampaşa",
        "Beşiktaş",
        "Beylikdüzü",
        "Beyoğlu",
        "Büyükçekmece",
        "Çatalca",
        "Esenler",
        "Esenyurt",
        "Eyüpsultan",
        "Fatih",
        "Gaziosmanpaşa",
        "Güngören",
        "Kağıthane",
        "Küçükçekmece",
        "Sarıyer",
        "Silivri",
        "Sultangazi",
        "Şişli",
        "Zeytinburnu",
    )

    val ALL: List<String> = (ANATOLIAN + EUROPEAN).sorted()
}

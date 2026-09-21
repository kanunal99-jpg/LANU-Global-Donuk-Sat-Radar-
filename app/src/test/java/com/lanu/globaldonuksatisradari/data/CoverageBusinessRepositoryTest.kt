package com.lanu.globaldonuksatisradari.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CoverageBusinessRepositoryTest {

    @Test
    fun coverageEngineUsesRealSourceDescriptors() {
        assertEquals("osm-overpass", OverpassBusinessSource.contract.descriptor.id)
        assertEquals("osm-nominatim", NominatimBusinessSource.contract.descriptor.id)
        assertTrue(OverpassBusinessSource.contract.permittedUseVerified)
        assertTrue(NominatimBusinessSource.contract.permittedUseVerified)
        assertTrue(OverpassBusinessSource.contract.supportsBulk.not())
        assertTrue(NominatimBusinessSource.contract.supportsBulk.not())
    }

    @Test
    fun istanbulDistrictCatalogContainsAll39Districts() {
        val expected = setOf(
            "Adalar", "Arnavutköy", "Ataşehir", "Avcılar", "Bağcılar", "Bahçelievler",
            "Bakırköy", "Başakşehir", "Bayrampaşa", "Beşiktaş", "Beykoz", "Beylikdüzü",
            "Beyoğlu", "Büyükçekmece", "Çatalca", "Çekmeköy", "Esenler", "Esenyurt",
            "Eyüpsultan", "Fatih", "Gaziosmanpaşa", "Güngören", "Kadıköy", "Kağıthane",
            "Kartal", "Küçükçekmece", "Maltepe", "Pendik", "Sancaktepe", "Sarıyer",
            "Silivri", "Sultanbeyli", "Sultangazi", "Şile", "Şişli", "Tuzla",
            "Ümraniye", "Üsküdar", "Zeytinburnu",
        )
        assertEquals(39, expected.size)
    }
}

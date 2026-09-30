package com.lanu.globaldonuksatisradari

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IstanbulRegionCatalogTest {
    @Test fun anatoliaAndEuropeAreDisjointAndMappedToIstanbul() {
        assertTrue("Kadıköy" in IstanbulRegionCatalog.anatoliaDistricts)
        assertTrue("Pendik" in IstanbulRegionCatalog.anatoliaDistricts)
        assertTrue("Şişli" in IstanbulRegionCatalog.europeDistricts)
        assertTrue("Bakırköy" in IstanbulRegionCatalog.europeDistricts)
        assertFalse(IstanbulRegionCatalog.anatoliaDistricts.any { it in IstanbulRegionCatalog.europeDistricts })
        assertEquals("İstanbul", IstanbulRegionCatalog.queryCity(IstanbulRegionCatalog.ANATOLIA))
        assertEquals("İstanbul", IstanbulRegionCatalog.queryCity(IstanbulRegionCatalog.EUROPE))
    }

    @Test fun originalIstanbulIsReplacedByTwoSalesRegions() {
        val input = listOf(City("İstanbul", listOf("Kadıköy")), City("Kocaeli", listOf("Gebze")))
        val output = IstanbulRegionCatalog.withTurkeyCities(input)
        assertEquals(listOf("İstanbul Anadolu", "İstanbul Avrupa", "Kocaeli"), output.map { it.name })
    }
}

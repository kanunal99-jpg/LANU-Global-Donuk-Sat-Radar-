package com.lanu.globaldonuksatisradari

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IstanbulScopeTest {
    @Test
    fun sideCatalogUsesCanonicalIstanbulQueries() {
        assertTrue("Kadıköy" in IstanbulDistricts.ANATOLIAN)
        assertTrue("Pendik" in IstanbulDistricts.ANATOLIAN)
        assertTrue("Şişli" in IstanbulDistricts.EUROPEAN)
        assertTrue("Bakırköy" in IstanbulDistricts.EUROPEAN)

        val anatolian = TurkeyCityCatalog.ALL.first { it.name == "İstanbul Anadolu" }
        val european = TurkeyCityCatalog.ALL.first { it.name == "İstanbul Avrupa" }

        assertEquals("İstanbul", anatolian.queryCityName)
        assertEquals("İstanbul", european.queryCityName)
        assertTrue(anatolian.restrictToFallbackDistricts)
        assertTrue(european.restrictToFallbackDistricts)
        assertFalse("Şişli" in anatolian.fallbackDistricts)
        assertFalse("Kadıköy" in european.fallbackDistricts)
    }
}

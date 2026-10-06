package com.lanu.globaldonuksatisradari

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TurkeyCityCatalogTest {
    @Test
    fun catalogContainsAll81UniqueProvinces() {
        assertEquals(81, TurkeyCityCatalog.ALL.size)
        assertEquals(81, TurkeyCityCatalog.ALL.map { it.name }.toSet().size)
        assertTrue(TurkeyCityCatalog.ALL.any { it.name == "İstanbul" })
        assertTrue(TurkeyCityCatalog.ALL.any { it.name == "Hakkâri" })
    }

    @Test
    fun istanbulFallbackStillContainsAllDistricts() {
        val istanbul = TurkeyCityCatalog.ALL.single { it.name == "İstanbul" }
        assertEquals(39, istanbul.fallbackDistricts.toSet().size)
    }

    @Test
    fun fallbackCatalogContainsAll973DistrictsAcross81Provinces() {
        assertEquals(81, TurkeyDistrictFallback.provinceCount)
        assertEquals(973, TurkeyDistrictFallback.districtCount)
        assertEquals(51, TurkeyDistrictFallback.centralDistrictCount)
        assertEquals(922, TurkeyDistrictFallback.districtGovernorateCount)
        TurkeyCityCatalog.ALL.forEach { city ->
            assertTrue("İlçe fallback boş: ${city.name}", city.fallbackDistricts.isNotEmpty())
        }
        assertEquals(25, TurkeyDistrictFallback.forCity("Ankara").size)
        assertEquals(12, TurkeyDistrictFallback.forCity("Kocaeli").size)
        assertEquals(39, TurkeyDistrictFallback.forCity("İstanbul").size)
        assertEquals(5, TurkeyDistrictFallback.forCity("Hakkâri").size)
    }
}

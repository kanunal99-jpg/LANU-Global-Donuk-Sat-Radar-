package com.lanu.globaldonuksatisradari

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TurkeyAdministrativeCatalogTest {
    @Test
    fun canonicalProvinceAndDistrictCountsAreComplete() {
        assertEquals(81, TurkeyCityCatalog.ALL.size)
        assertEquals(81, TurkeyCityCatalog.ALL.map { it.name }.distinct().size)
        assertEquals(81, TurkeyDistrictFallback.provinceCount)

        // 973 includes 51 central "Merkez" districts; 922 remaining districts map to
        // the Ministry of Interior's district governorate count.
        assertEquals(973, TurkeyDistrictFallback.districtCount)
        assertEquals(51, TurkeyDistrictFallback.centralDistrictCount)
        assertEquals(922, TurkeyDistrictFallback.districtGovernorateCount)

        TurkeyCityCatalog.ALL.forEach { city ->
            assertTrue("İlçe listesi boş: ${city.name}", city.fallbackDistricts.isNotEmpty())
            assertEquals(
                "Katalog/fallback uyuşmuyor: ${city.name}",
                TurkeyDistrictFallback.forCity(city.name).toSet(),
                city.fallbackDistricts.toSet(),
            )
        }
    }

    @Test
    fun istanbulSidesAreDisjointAndComplete() {
        assertEquals(14, IstanbulDistricts.ANATOLIAN.size)
        assertEquals(25, IstanbulDistricts.EUROPEAN.size)
        assertEquals(39, IstanbulDistricts.ALL.size)
        assertEquals(39, IstanbulDistricts.ALL.distinct().size)
        assertTrue(
            IstanbulDistricts.ANATOLIAN.toSet()
                .intersect(IstanbulDistricts.EUROPEAN.toSet())
                .isEmpty(),
        )
        assertEquals(
            TurkeyDistrictFallback.forCity("İstanbul").toSet(),
            IstanbulDistricts.ALL.toSet(),
        )
    }
}

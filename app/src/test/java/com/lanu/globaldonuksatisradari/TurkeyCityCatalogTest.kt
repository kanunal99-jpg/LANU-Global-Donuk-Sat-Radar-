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
    fun everyProvinceHasCompleteOfflineDistrictFallback() {
        assertEquals(81, TurkeyDistrictFallbackCatalog.PROVINCE_COUNT)
        assertEquals(973, TurkeyDistrictFallbackCatalog.DISTRICT_COUNT)
        assertEquals(81, TurkeyDistrictFallbackCatalog.provinceNames().size)
        assertEquals(973, TurkeyDistrictFallbackCatalog.totalDistrictCount())

        TurkeyCityCatalog.ALL.forEach { province ->
            assertTrue(
                "Offline ilçe fallback boş: ${province.name}",
                province.fallbackDistricts.isNotEmpty(),
            )
            assertEquals(
                "İlçe listesinde tekrar var: ${province.name}",
                province.fallbackDistricts.size,
                province.fallbackDistricts.distinct().size,
            )
        }
        assertEquals(
            973,
            TurkeyCityCatalog.ALL.sumOf { it.fallbackDistricts.size },
        )
    }

    @Test
    fun istanbulFallbackMatchesSidePartitionExactly() {
        val istanbul = TurkeyCityCatalog.ALL.single { it.name == "İstanbul" }
        assertEquals(39, istanbul.fallbackDistricts.toSet().size)
        assertEquals(
            istanbul.fallbackDistricts.toSet(),
            (IstanbulDistricts.ANATOLIAN + IstanbulDistricts.EUROPEAN).toSet(),
        )
        assertTrue(
            IstanbulDistricts.ANATOLIAN.toSet()
                .intersect(IstanbulDistricts.EUROPEAN.toSet())
                .isEmpty(),
        )
    }
}

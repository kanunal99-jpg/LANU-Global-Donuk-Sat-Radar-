package com.lanu.globaldonuksatisradari

import com.lanu.globaldonuksatisradari.data.BusinessDeduplication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TurkeyDistrictFallbackTest {
    @Test
    fun catalogContainsExactly81ProvincesAnd973Districts() {
        assertEquals(81, TurkeyCityCatalog.ALL.size)
        assertEquals(81, TurkeyDistrictFallback.provinceCount)
        assertEquals(973, TurkeyDistrictFallback.districtCount)

        TurkeyCityCatalog.ALL.forEach { entry ->
            val districts = TurkeyDistrictFallback.forCity(entry.name)
            assertFalse("${entry.name} ilçe listesi boş olmamalı", districts.isEmpty())
            val normalized = districts.map(BusinessDeduplication::normalizeForComparison)
            assertEquals("${entry.name} içinde mükerrer ilçe olmamalı", normalized.size, normalized.distinct().size)
        }
    }

    @Test
    fun istanbulCanonicalListMatchesSidePartitions() {
        val canonical = TurkeyDistrictFallback.forCity("İstanbul").toSet()
        assertEquals(39, canonical.size)
        assertEquals(canonical, IstanbulDistricts.ALL.toSet())
        assertTrue(IstanbulDistricts.ANATOLIAN.toSet().intersect(IstanbulDistricts.EUROPEAN.toSet()).isEmpty())
    }

    @Test
    fun representativeProvinceDistrictsArePresent() {
        assertTrue("Çankaya" in TurkeyDistrictFallback.forCity("Ankara"))
        assertTrue("Konak" in TurkeyDistrictFallback.forCity("İzmir"))
        assertTrue("Gebze" in TurkeyDistrictFallback.forCity("Kocaeli"))
        assertTrue("Nilüfer" in TurkeyDistrictFallback.forCity("Bursa"))
        assertTrue("Seyhan" in TurkeyDistrictFallback.forCity("Adana"))
        assertTrue("Şahinbey" in TurkeyDistrictFallback.forCity("Gaziantep"))
    }
}

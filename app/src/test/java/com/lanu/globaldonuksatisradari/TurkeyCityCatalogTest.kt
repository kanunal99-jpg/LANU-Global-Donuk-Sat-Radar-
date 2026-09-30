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
}

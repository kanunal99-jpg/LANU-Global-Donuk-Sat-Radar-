package com.lanu.globaldonuksatisradari

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TurkeyCityCatalogTest {
    @Test
    fun catalogContainsAll81Provinces() {
        assertEquals(81, TurkeyCityCatalog.ALL.size)
        assertTrue(TurkeyAdministrativeRegions.PROVINCES.containsAll(TurkeyCityCatalog.ALL.map { it.name }))
        assertTrue(TurkeyAdministrativeRegions.containsProvince("İstanbul"))
        assertTrue(TurkeyAdministrativeRegions.containsProvince("Şanlıurfa"))
    }
}

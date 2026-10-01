package com.lanu.globaldonuksatisradari.data

import com.lanu.globaldonuksatisradari.IstanbulDistricts
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
    fun cityWideScanUsesEveryDiscoveredDistrict() {
        val scopes = planCoverageScopes(
            city = "Ağrı",
            selectedDistrict = null,
            query = "",
            discoveredDistricts = listOf("Merkez", "Patnos", "Doğubayazıt"),
        )

        assertEquals(3, scopes.size)
        assertEquals(setOf("Merkez", "Patnos", "Doğubayazıt"), scopes.map { it.district }.toSet())
        assertTrue(scopes.none { it.district == "Tümü" })
        assertTrue(scopes.all { it.category == "*" })
    }

    @Test
    fun explicitDistrictNeverLeaksIntoOtherDistricts() {
        val scopes = planCoverageScopes(
            city = "Ağrı",
            selectedDistrict = "Patnos",
            query = "market",
            discoveredDistricts = listOf("Merkez", "Patnos", "Doğubayazıt"),
        )

        assertEquals(1, scopes.size)
        assertEquals("Patnos", scopes.single().district)
        assertEquals("market", scopes.single().category)
    }

    @Test
    fun istanbulDistrictCatalogContainsAll39Districts() {
        assertEquals(39, IstanbulDistricts.ALL.size)
        assertEquals(39, IstanbulDistricts.ALL.toSet().size)
    }
}

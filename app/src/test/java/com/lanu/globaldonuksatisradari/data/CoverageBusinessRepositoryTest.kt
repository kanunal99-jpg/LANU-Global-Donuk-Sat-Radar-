package com.lanu.globaldonuksatisradari.data

import com.lanu.globaldonuksatisradari.IstanbulDistricts
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CoverageBusinessRepositoryTest {

    @Test
    fun coverageEngineUsesRealSourceDescriptors() {
        assertEquals("overture-places", OvertureBusinessSource.contract.descriptor.id)
        assertEquals("osm-overpass", OverpassBusinessSource.contract.descriptor.id)
        assertEquals("osm-nominatim", NominatimBusinessSource.contract.descriptor.id)
        assertTrue(OvertureBusinessSource.contract.permittedUseVerified)
        assertTrue(OverpassBusinessSource.contract.permittedUseVerified)
        assertTrue(NominatimBusinessSource.contract.permittedUseVerified)
        assertTrue(OvertureBusinessSource.contract.supportsBulk)
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
    fun explicitNeighborhoodStaysInsideSelectedDistrictScope() {
        val scopes = planCoverageScopes(
            city = "İstanbul",
            selectedDistrict = "Kadıköy",
            selectedNeighborhood = "Caferağa",
            query = "",
            discoveredDistricts = emptyList(),
        )

        assertEquals(1, scopes.size)
        assertEquals("Kadıköy", scopes.single().district)
        assertEquals("Caferağa", scopes.single().neighborhood)
        assertEquals("*", scopes.single().category)
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
    @Test
    fun newBusinessFamiliesUseUsefulNominatimFallbackTerms() {
        assertEquals("school", nominatimFallbackQuery("education"))
        assertEquals("car repair", nominatimFallbackQuery("automotive"))
        assertEquals("beauty salon", nominatimFallbackQuery("beauty"))
        assertEquals("bank", nominatimFallbackQuery("finance"))
        assertEquals("construction company", nominatimFallbackQuery("construction"))
        assertEquals("farm", nominatimFallbackQuery("agriculture"))
        assertEquals("logistics", nominatimFallbackQuery("logistics"))
        assertEquals("factory", nominatimFallbackQuery("manufacturer"))
        assertEquals("wholesale", nominatimFallbackQuery("wholesale"))
    }

}

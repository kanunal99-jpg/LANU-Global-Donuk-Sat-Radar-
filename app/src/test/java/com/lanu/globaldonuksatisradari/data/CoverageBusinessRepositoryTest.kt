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
    fun istanbulDistrictCatalogContainsAll39Districts() {
        assertEquals(39, IstanbulDistricts.ALL.size)
        assertEquals(39, IstanbulDistricts.ALL.toSet().size)
    }
}

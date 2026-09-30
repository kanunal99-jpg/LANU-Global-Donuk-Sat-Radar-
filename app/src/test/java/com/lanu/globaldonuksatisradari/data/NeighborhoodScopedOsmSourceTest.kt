package com.lanu.globaldonuksatisradari.data

import java.net.URLDecoder
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NeighborhoodScopedOsmSourceTest {
    @Test
    fun overpassQuery_placesNeighborhoodInUpstreamTagFilter() {
        val query = NeighborhoodScopedOsmQueryBuilder.overpass(
            query = "cafe",
            city = "İstanbul",
            district = "Kadıköy",
            neighborhood = "Caferağa",
        )

        assertTrue(query.contains("Caferağa"))
        assertTrue(query.contains("addr:(suburb|neighbourhood|quarter)"))
        assertTrue(query.contains("area.searchArea"))
        assertFalse(query.contains("nwr[\"amenity\"=\"cafe\"](area.searchArea);"))
    }

    @Test
    fun nominatimQuery_placesNeighborhoodBetweenSearchAndDistrict() {
        val url = NeighborhoodScopedOsmQueryBuilder.nominatim(
            query = "cafe",
            city = "İstanbul",
            district = "Kadıköy",
            neighborhood = "Caferağa",
        )
        val decoded = URLDecoder.decode(url, Charsets.UTF_8.name())

        assertTrue(decoded.contains("q=cafe, Caferağa, Kadıköy, İstanbul, Türkiye"))
        assertTrue(decoded.startsWith(NominatimBusinessSource.BASE_URL))
    }
}

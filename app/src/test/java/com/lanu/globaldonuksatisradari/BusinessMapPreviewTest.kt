package com.lanu.globaldonuksatisradari

import com.lanu.globaldonuksatisradari.data.DataSourceDescriptor
import com.lanu.globaldonuksatisradari.data.VerifiedBusiness
import org.junit.Assert.assertTrue
import org.junit.Test

class BusinessMapPreviewTest {

    @Test
    fun mapHtml_containsOsmAttributionTilesAndRealMarkers() {
        val business = VerifiedBusiness(
            id = "osm-node-1",
            name = "Gerçek İşletme",
            city = "İstanbul",
            district = "Kadıköy",
            neighborhood = "Moda",
            source = DataSourceDescriptor(
                id = "osm-nominatim",
                name = "OpenStreetMap Nominatim",
                publisher = "OpenStreetMap",
                licenseOrTerms = "ODbL",
                sourceUrl = "https://nominatim.openstreetmap.org/",
                lastVerifiedAtEpochMs = 1L,
            ),
            verifiedAtEpochMs = 1L,
            latitude = 40.987,
            longitude = 29.028,
            category = "restaurant",
        )

        val html = buildMapHtml(listOf(business))

        assertTrue(html.contains("tile.openstreetmap.org/{z}/{x}/{y}.png"))
        assertTrue(html.contains("OpenStreetMap contributors"))
        assertTrue(html.contains("ODbL"))
        assertTrue(html.contains("Gerçek İşletme"))
        assertTrue(html.contains("40.987"))
        assertTrue(html.contains("29.028"))
    }
}

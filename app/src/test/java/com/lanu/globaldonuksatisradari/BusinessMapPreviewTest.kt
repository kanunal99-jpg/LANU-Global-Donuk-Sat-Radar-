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
        assertTrue(html.contains("unpkg.com/leaflet@1.9.4"))
        assertTrue(html.contains("cdn.jsdelivr.net/npm/leaflet@1.9.4"))
        assertTrue(html.contains("L.circleMarker"))
        assertTrue(html.contains("loadLeafletFallback"))
        assertTrue(html.contains("AndroidMapBridge.onReady"))
        assertTrue(html.contains("AndroidMapBridge.onError"))
    }

    @Test
    fun mapHtml_rendersUntrustedNamesThroughTextContentInsteadOfPopupHtml() {
        val business = VerifiedBusiness(
            id = "osm-node-xss",
            name = "<img src=x onerror=alert(1)>",
            city = "İstanbul",
            district = "<script>alert(2)</script>",
            neighborhood = null,
            source = DataSourceDescriptor(
                id = "osm-overpass",
                name = "OSM",
                publisher = "OpenStreetMap",
                licenseOrTerms = "ODbL",
                sourceUrl = "https://www.openstreetmap.org/",
                lastVerifiedAtEpochMs = 1L,
            ),
            verifiedAtEpochMs = 1L,
            latitude = 41.0,
            longitude = 29.0,
            category = "<b>restaurant</b>",
        )

        val html = buildMapHtml(listOf(business))

        assertTrue(html.contains("title.textContent = business.name"))
        assertTrue(html.contains("document.createTextNode(business.district)"))
        assertTrue(html.contains("document.createTextNode('Kategori: ' + business.category)"))
        assertTrue(!html.contains("bindPopup('<strong>'"))
    }
}

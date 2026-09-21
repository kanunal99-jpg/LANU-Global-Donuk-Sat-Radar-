package com.lanu.globaldonuksatisradari.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CoverageLocalCacheTest {

    private val source = DataSourceDescriptor(
        id = "osm-overpass",
        name = "OpenStreetMap Overpass",
        publisher = "OpenStreetMap / FOSSGISS",
        licenseOrTerms = "OSM/ODbL",
        sourceUrl = "https://overpass-api.de/api/interpreter",
        lastVerifiedAtEpochMs = 123L,
    )

    private val business = VerifiedBusiness(
        id = "node:123",
        name = "Test Cafe",
        city = "İstanbul",
        district = "Kadıköy",
        neighborhood = "Caferağa",
        source = source,
        verifiedAtEpochMs = 456L,
        latitude = 40.98,
        longitude = 29.03,
        category = "cafe",
        address = "Test Sokak 1",
        phone = "+90 212 000 00 00",
        website = "https://example.com",
        openingHours = "Mo-Sa 09:00-22:00",
        menuUrl = "https://example.com/menu",
        menuText = "Kahve",
    )

    @Test
    fun cacheCodecRoundTripsVerifiedBusinessProvenance() {
        val raw = CoverageLocalCacheCodec.encode(listOf(business), 1_000L)
        val decoded = CoverageLocalCacheCodec.decode(raw)

        assertEquals(1_000L, decoded.savedAtEpochMs)
        assertEquals(1, decoded.businesses.size)
        assertEquals(business, decoded.businesses.single())
    }

    @Test
    fun mergerUsesCacheOnlyAfterAllExternalAttemptsFail() {
        val scope = CoverageScope(
            city = "İstanbul",
            district = "Kadıköy",
            category = "restaurant",
        )
        val cache = object : CoverageLocalCache {
            private var saved: List<VerifiedBusiness> = emptyList()

            override fun get(scope: CoverageScope, nowEpochMs: Long): List<VerifiedBusiness> = saved

            override fun put(scope: CoverageScope, businesses: List<VerifiedBusiness>, savedAtEpochMs: Long) {
                saved = businesses
            }
        }

        val cached = business.copy(id = "cached:1")
        cache.put(scope, listOf(cached), 10L)

        val failedScan = CoverageScanResult(
            scope = scope,
            businesses = emptyList(),
            attempts = listOf(
                CoverageAttempt("osm-overpass", success = false, resultCount = 0, errorCode = "HTTP"),
                CoverageAttempt("osm-nominatim", success = false, resultCount = 0, errorCode = "HTTP"),
            ),
            selectedSourceId = null,
            completedAtEpochMs = 20L,
        )

        val result = CoverageResultMerger.merge(
            scans = listOf(failedScan),
            localCache = cache,
            nowEpochMs = 20L,
        )

        assertEquals(listOf(cached), result)
    }

    @Test
    fun successfulEmptyScanDoesNotUseCache() {
        val scope = CoverageScope(
            city = "İstanbul",
            district = "Kadıköy",
            category = "restaurant",
        )
        val cache = object : CoverageLocalCache {
            override fun get(scope: CoverageScope, nowEpochMs: Long): List<VerifiedBusiness> =
                listOf(business.copy(id = "cached:2"))

            override fun put(scope: CoverageScope, businesses: List<VerifiedBusiness>, savedAtEpochMs: Long) = Unit
        }

        val successfulEmptyScan = CoverageScanResult(
            scope = scope,
            businesses = emptyList(),
            attempts = listOf(
                CoverageAttempt("osm-overpass", success = true, resultCount = 0),
            ),
            selectedSourceId = "osm-overpass",
            completedAtEpochMs = 20L,
        )

        val result = CoverageResultMerger.merge(
            scans = listOf(successfulEmptyScan),
            localCache = cache,
            nowEpochMs = 20L,
        )

        assertTrue(result.isEmpty())
    }
}

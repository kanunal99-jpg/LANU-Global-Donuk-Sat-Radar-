package com.lanu.globaldonuksatisradari.data

import kotlin.test.Test
import kotlin.test.assertEquals

class BusinessDeduplicationTest {
    private fun record(
        id: String,
        phone: String?,
        address: String?,
        latitude: Double?,
        longitude: Double?,
        website: String?,
    ) = VerifiedBusiness(
        id = id,
        name = "Demo Cafe",
        city = "İstanbul",
        district = "Kadıköy",
        neighborhood = null,
        source = DataSourceDescriptor(
            id = if (id == "osm-1") "osm" else "alternative",
            name = "Test",
            publisher = "Test",
            licenseOrTerms = "test",
            sourceUrl = "https://example.com",
            lastVerifiedAtEpochMs = 1L,
        ),
        verifiedAtEpochMs = 1L,
        latitude = latitude,
        longitude = longitude,
        category = "cafe",
        address = address,
        phone = phone,
        website = website,
        openingHours = null,
        menuUrl = null,
        menuText = null,
    )

    @Test
    fun crossSourceRecordsWithSamePhoneCollapseToRicherRecord() {
        val sparse = record("osm-1", "+90 555 555 5555", null, 40.99, 29.03, null)
        val rich = record("other-1", "+90 555 555 5555", "Kadıköy, İstanbul", 40.99, 29.03, "https://example.com")
        val result = BusinessDeduplication.deduplicateCrossSource(listOf(sparse, rich))
        assertEquals(1, result.size)
        assertEquals("other-1", result.single().id)
    }
}

package com.lanu.globaldonuksatisradari.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class OvertureBusinessSourceAdapterTest {

    @Test
    fun manifestRequiresSafeProvinceAssetsAndStrongHashes() {
        val payload = """
            {
              "schemaVersion":1,
              "overtureRelease":"2026-09-23.1",
              "generatedAtEpochMs":1790985600000,
              "cities":[{
                "city":"İstanbul",
                "regionCode":"TR-34",
                "asset":"TR-34.jsonl.gz",
                "sha256":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                "recordCount":1234
              }]
            }
        """.trimIndent()

        val manifest = OvertureDirectoryParser.parseManifest(payload)

        assertEquals("2026-09-23.1", manifest.overtureRelease)
        assertEquals(1, manifest.cities.size)
        assertEquals("İstanbul", manifest.cities.single().city)
        assertEquals("TR-34.jsonl.gz", manifest.cities.single().asset)
        assertEquals(1234, manifest.cities.single().recordCount)
    }

    @Test
    fun recordParserPreservesBusinessIdentityLocationAndContactFields() {
        val record = OvertureDirectoryParser.parseRecord(
            """
            {
              "id":"gers-1",
              "name":"Sandora Fast Food & Cafe",
              "city":"İstanbul",
              "district":"Sultanbeyli",
              "neighborhood":"Mimar Sinan",
              "address":"Özgürlük Cd. No:76/A",
              "latitude":40.968,
              "longitude":29.266,
              "basicCategory":"fast_food_restaurant",
              "category":"fast_food_restaurant",
              "topLevelCategory":"food_and_drink",
              "phone":"05375197453",
              "website":"https://example.com",
              "operatingStatus":"open",
              "confidence":0.91
            }
            """.trimIndent(),
        )

        assertNotNull(record)
        assertEquals("Sandora Fast Food & Cafe", record.name)
        assertEquals("Sultanbeyli", record.district)
        assertEquals("Mimar Sinan", record.neighborhood)
        assertEquals("05375197453", record.phone)
        assertEquals(0.91, record.confidence)

        val verified = OvertureDirectoryParser.toVerifiedBusiness(record, 1790985600000)
        assertNotNull(verified)
        assertEquals("overture-places", verified.source.id)
        assertEquals("fast_food_restaurant", verified.category)
        assertEquals(40.968, verified.latitude)
        assertEquals(29.266, verified.longitude)
    }

    @Test
    fun queryAndDistrictFilteringAreSectorAgnostic() {
        val factory = OvertureDirectoryRecord(
            id = "1",
            name = "Örnek Makine Sanayi",
            city = "İstanbul",
            district = "Tuzla",
            neighborhood = "Aydınlı",
            address = "Sanayi Cad. No:1",
            latitude = 40.9,
            longitude = 29.3,
            basicCategory = "manufacturer",
            category = "industrial_equipment_manufacturer",
            topLevelCategory = "services_and_business",
            phone = null,
            website = null,
            operatingStatus = "open",
            confidence = 0.8,
        )

        assertTrue(OvertureDirectoryParser.matches(factory, "", "Tuzla", null))
        assertTrue(OvertureDirectoryParser.matches(factory, "manufacturer", "Tuzla", null))
        assertTrue(OvertureDirectoryParser.matches(factory, "makine", "Tuzla", "Aydınlı Mahallesi"))
        assertFalse(OvertureDirectoryParser.matches(factory, "restaurant", "Tuzla", null))
        assertFalse(OvertureDirectoryParser.matches(factory, "", "Pendik", null))
    }

    @Test
    fun sourceContractIsBulkAndPermissionReviewed() {
        assertEquals("overture-places", OvertureBusinessSource.descriptor.id)
        assertEquals(SourceAccessMethod.API, OvertureBusinessSource.contract.accessMethod)
        assertTrue(OvertureBusinessSource.contract.supportsBulk)
        assertTrue(OvertureBusinessSource.contract.permittedUseVerified)
    }
}

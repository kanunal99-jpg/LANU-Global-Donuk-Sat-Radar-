package com.lanu.globaldonuksatisradari.crm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CrmLocationEnrichmentTest {
    @Test
    fun weakAddressesAreRemovedInsteadOfExportedAsOpenAddress() {
        assertNull(CrmLocationSanitizer.sanitizeAddress("Ağrı", "Ağrı", "Merkez"))
        assertNull(CrmLocationSanitizer.sanitizeAddress("Patnos/Ağrı", "Ağrı", "Patnos"))
        assertNull(CrmLocationSanitizer.sanitizeAddress("12", "Ağrı", "Merkez"))
        assertNull(CrmLocationSanitizer.sanitizeAddress("192", "Ağrı", "Merkez"))
        assertNull(CrmLocationSanitizer.sanitizeAddress("04100", "Ağrı", "Merkez"))
    }

    @Test
    fun detailedStreetAddressIsPreserved() {
        assertEquals(
            "Cumhuriyet Cad., 282 D: 4, 04100, Ağrı",
            CrmLocationSanitizer.sanitizeAddress(
                "Cumhuriyet Cad., 282 D: 4, 04100, Ağrı",
                "Ağrı",
                "Merkez",
            ),
        )
    }

    @Test
    fun districtMustBelongToSelectedProvinceCatalog() {
        val districts = listOf(
            "Diyadin",
            "Doğubayazıt",
            "Eleşkirt",
            "Hamur",
            "Merkez",
            "Patnos",
            "Taşlıçay",
            "Tutak",
        )

        assertNull(
            CrmLocationSanitizer.canonicalDistrict(
                current = "Beyşehir",
                city = "Ağrı",
                candidates = emptyList(),
                validDistricts = districts,
            ),
        )
        assertEquals(
            "Doğubayazıt",
            CrmLocationSanitizer.canonicalDistrict(
                current = "Bilinmiyor",
                city = "Ağrı",
                candidates = listOf("Doğubayazıt İlçesi"),
                validDistricts = districts,
            ),
        )
        assertEquals(
            "Merkez",
            CrmLocationSanitizer.canonicalDistrict(
                current = "Ağrı",
                city = "Ağrı",
                candidates = emptyList(),
                validDistricts = districts,
            ),
        )
    }

    @Test
    fun nominatimLookupParserSeparatesDistrictNeighborhoodAndProvince() {
        val payload = """
            [{
              "osm_type":"node",
              "osm_id":123,
              "display_name":"Test Kafe, Cumhuriyet Caddesi, Hürriyet Mahallesi, Doğubayazıt, Ağrı, Türkiye",
              "address":{
                "quarter":"Hürriyet Mahallesi",
                "town":"Doğubayazıt",
                "state":"Ağrı",
                "country":"Türkiye"
              }
            }]
        """.trimIndent()

        val parsed = CrmLocationEnrichmentService.parseLookupPayload(payload).single()

        assertEquals("node:123", parsed.osmKey)
        assertTrue(parsed.districtCandidates.contains("Doğubayazıt"))
        assertTrue(parsed.neighborhoodCandidates.contains("Hürriyet Mahallesi"))
        assertTrue(parsed.provinceCandidates.contains("Ağrı"))
    }

    @Test
    fun osmKeyParserRejectsNonOsmManualIds() {
        assertEquals("node:123", CrmLocationEnrichmentService.parseOsmKey("Node:123"))
        assertNull(CrmLocationEnrichmentService.parseOsmKey("manual:123"))
        assertNull(CrmLocationEnrichmentService.parseOsmKey("instrumentation-ui"))
    }
}

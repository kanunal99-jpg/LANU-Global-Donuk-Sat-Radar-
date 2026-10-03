package com.lanu.globaldonuksatisradari.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SupplementalBusinessDirectoryTest {
    private val payload = """
        {
          "schemaVersion":1,
          "records":[{
            "id":"supplemental:tr-34:sandora-fast-food-cafe-sultanbeyli",
            "name":"Sandora Fast Food & Cafe",
            "city":"İstanbul",
            "district":"Sultanbeyli",
            "neighborhood":"Mimar Sinan",
            "category":"fast_food_restaurant",
            "address":"Mimar Sinan, Özgürlük Cd. No:76/A, 34920 Sultanbeyli/İstanbul",
            "phone":"+90 537 519 74 53",
            "verifiedAtEpochMs":1790985600000,
            "source":{
              "id":"public-evidence-restaurantguru",
              "name":"Restaurant Guru",
              "publisher":"Restaurant Guru",
              "licenseOrTerms":"Public factual listing",
              "sourceUrl":"https://tr.restaurantguru.com/Sandora-Fast-Food-and-Cafe-Sultanbeyli",
              "lastVerifiedAtEpochMs":1790985600000
            }
          }]
        }
    """.trimIndent()

    @Test
    fun sandoraAcceptanceRecordParsesWithExpectedIdentityAndContact() {
        val business = SupplementalBusinessParser.parse(payload).single()

        assertEquals("Sandora Fast Food & Cafe", business.name)
        assertEquals("Sultanbeyli", business.district)
        assertEquals("Mimar Sinan", business.neighborhood)
        assertEquals("+90 537 519 74 53", business.phone)
        assertEquals("public-evidence-restaurantguru", business.source.id)
    }

    @Test
    fun supplementalSearchMatchingRespectsDistrictNeighborhoodAndName() {
        val business = SupplementalBusinessParser.parse(payload).single()

        assertTrue(
            SupplementalBusinessParser.matches(
                business, "sandora cafe", "İstanbul", "Sultanbeyli", "Mimar Sinan Mahallesi",
            ),
        )
        assertFalse(
            SupplementalBusinessParser.matches(
                business, "sandora cafe", "İstanbul", "Kadıköy", null,
            ),
        )
    }
}

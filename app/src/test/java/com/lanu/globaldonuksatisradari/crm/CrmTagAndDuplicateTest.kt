package com.lanu.globaldonuksatisradari.crm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CrmTagAndDuplicateTest {
    @Test
    fun tagsAreNormalizedDeduplicatedAndBounded() {
        val tags = CrmTagCodec.normalize(
            listOf("  Sıcak   Lead ", "sıcak lead", "Otel", "Zincir|Özel", ""),
        )

        assertEquals(3, tags.size)
        assertTrue(tags.any { it.equals("Sıcak Lead", ignoreCase = true) })
        assertTrue(tags.contains("Otel"))
        assertTrue(tags.contains("Zincir Özel"))
        assertEquals(tags, CrmTagCodec.decode(CrmTagCodec.encode(tags)))
    }

    @Test
    fun exactPhoneAndNameInSameDistrictCreatesStrongDuplicateCandidate() {
        val a = customer(
            id = "a",
            name = "Global Lezzet Market",
            phone = "0532 111 22 33",
            latitude = 40.9900,
            longitude = 29.0300,
        )
        val b = customer(
            id = "b",
            name = "GLOBAL LEZZET MARKET",
            phone = "+90 532 111 22 33",
            latitude = 40.9902,
            longitude = 29.0302,
        )

        val candidate = CrmDuplicateDetector.find(listOf(a, b)).single()

        assertTrue(candidate.score >= 100)
        assertTrue(candidate.reasons.contains("Aynı telefon"))
        assertTrue(candidate.reasons.contains("Aynı işletme/tabela adı"))
    }

    @Test
    fun similarNamesInDifferentCitiesAreNotMergedBySuggestion() {
        val a = customer(id = "a", name = "Merkez Market", city = "İstanbul", district = "Kadıköy")
        val b = customer(id = "b", name = "Merkez Market", city = "Ankara", district = "Çankaya")

        assertTrue(CrmDuplicateDetector.find(listOf(a, b)).isEmpty())
    }

    private fun customer(
        id: String,
        name: String,
        phone: String? = null,
        city: String = "İstanbul",
        district: String = "Kadıköy",
        latitude: Double? = null,
        longitude: Double? = null,
    ) = CrmCustomer(
        id = id,
        businessSourceId = "manual:$id",
        businessName = name,
        city = city,
        district = district,
        neighborhood = null,
        phone = phone,
        latitude = latitude,
        longitude = longitude,
        createdAtEpochMs = 1L,
        updatedAtEpochMs = 1L,
    )
}

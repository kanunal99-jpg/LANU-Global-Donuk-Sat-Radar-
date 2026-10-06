package com.lanu.globaldonuksatisradari

import com.lanu.globaldonuksatisradari.crm.CrmCustomer
import com.lanu.globaldonuksatisradari.crm.CrmDuplicateDetector
import com.lanu.globaldonuksatisradari.crm.CrmTagCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CrmSegmentDuplicateRegressionTest {
    @Test
    fun tagCodec_normalizesDeduplicatesAndRoundTrips() {
        val encoded = CrmTagCodec.encode(listOf(" VIP ", "vip", " HoReCa  A ", "Saha|Öncelik", ""))
        val decoded = CrmTagCodec.decode(encoded)
        assertEquals(setOf("HoReCa A", "Saha Öncelik", "VIP"), decoded)
        assertTrue(encoded.length <= 20 * 41)
    }

    @Test
    fun duplicateDetector_requiresStrongEvidenceAndRejectsCrossCityNoise() {
        val now = 1L
        fun customer(id: String, name: String, city: String, district: String, phone: String?) =
            CrmCustomer(
                id = id,
                businessSourceId = id,
                businessName = name,
                city = city,
                district = district,
                neighborhood = "Merkez",
                phone = phone,
                createdAtEpochMs = now,
                updatedAtEpochMs = now,
            )

        val a = customer("a", "Global Donuk Gıda", "İstanbul", "Kadıköy", "05551112233")
        val b = customer("b", "Global Donuk Gida", "İstanbul", "Kadıköy", "+90 555 111 22 33")
        val c = customer("c", "Global Donuk Gıda", "Ankara", "Çankaya", null)

        val candidates = CrmDuplicateDetector.find(listOf(a, b, c))
        assertEquals(1, candidates.size)
        assertEquals(setOf("a", "b"), setOf(candidates.single().first.id, candidates.single().second.id))
        assertTrue(candidates.single().score >= 70)
    }
}

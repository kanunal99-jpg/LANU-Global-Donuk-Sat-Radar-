package com.lanu.globaldonuksatisradari.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BusinessFreshnessDeduplicationTest {
    @Test
    fun `freshness does not change provenance`() {
        val policy = FreshnessPolicy(maxAgeMs = 1_000)
        assertEquals(Freshness.FRESH, policy.evaluate(1_000, 2_000))
        assertEquals(Freshness.STALE, policy.evaluate(1_000, 2_001))
        assertEquals(DataQuality.VERIFIED_OFFICIAL, DataQuality.VERIFIED_OFFICIAL)
    }

    @Test
    fun `deduplication only merges identical stable source identity`() {
        val source = DataSourceDescriptor("ito", "ITO", "ITO", "terms", "https://example.com", 1)
        val first = VerifiedBusiness("100", "Kafe A", "İstanbul", "Kadıköy", null, source, 1)
        val duplicate = first.copy(name = "Kafe A Ltd")
        val differentId = first.copy(id = "101", name = "Kafe A Ltd")

        val result = BusinessDeduplication.deduplicate(listOf(first, duplicate, differentId))
        assertEquals(2, result.size)
        assertTrue(result.any { it.id == "100" })
        assertTrue(result.any { it.id == "101" })
    }

    @Test
    fun `normalization is comparison helper only`() {
        assertEquals("istanbul cafe", BusinessDeduplication.normalizeForComparison("  İstanbul   CAFE "))
    }
}

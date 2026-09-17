package com.lanu.globaldonuksatisradari.data

import kotlin.test.Test
import kotlin.test.assertEquals

class BusinessIngestionRulesTest {
    private val source = DataSourceDescriptor(
        id = "source",
        name = "Verified test source",
        publisher = "LANU test",
        licenseOrTerms = "TEST_VERIFIED",
        sourceUrl = "https://example.com/source",
        lastVerifiedAtEpochMs = 1L,
    )

    private fun business(id: String, verifiedAt: Long) = VerifiedBusiness(
        id = id,
        name = "Business $id",
        city = "İstanbul",
        district = "Kadıköy",
        neighborhood = "Caferağa",
        source = source,
        verifiedAtEpochMs = verifiedAt,
    )

    @Test
    fun `duplicate source records are reduced deterministically`() {
        val first = business("1", 100L)
        val duplicate = business("1", 200L)
        val second = business("2", 200L)

        val result = BusinessDeduplicator.deduplicate(listOf(first, duplicate, second))

        assertEquals(listOf(first, second), result)
    }

    @Test
    fun `future verification timestamp is not accepted`() {
        val result = BusinessIngestionGate.accept(
            records = listOf(business("1", 2_000L)),
            nowEpochMs = 1_000L,
            maxAgeMs = 10_000L,
        )

        assertEquals(emptyList(), result)
    }

    @Test
    fun `stale state is separate from source quality`() {
        val record = business("1", 1_000L)

        assertEquals(
            BusinessFreshnessState.STALE,
            BusinessFreshness.classify(record, nowEpochMs = 3_000L, maxAgeMs = 1_000L),
        )
        assertEquals(
            listOf(record),
            BusinessIngestionGate.accept(
                records = listOf(record),
                nowEpochMs = 3_000L,
                maxAgeMs = 2_000L,
            ),
        )
    }
}

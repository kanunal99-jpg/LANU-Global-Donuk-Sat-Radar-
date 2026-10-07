package com.lanu.globaldonuksatisradari.crm

import org.junit.Assert.assertTrue
import org.junit.Test

class CrmDuplicateDetectorScaleTest {
    private fun customer(index: Int, phone: String? = null, name: String = "İşletme"): CrmCustomer =
        CrmCustomer(
            id = "dup-$index",
            businessSourceId = "test:dup-$index",
            businessName = "$name $index",
            city = "İstanbul",
            district = "Sultanbeyli",
            neighborhood = "Test",
            latitude = 40.90 + (index % 100) * 0.0005,
            longitude = 29.20 + (index / 100) * 0.0005,
            phone = phone,
            createdAtEpochMs = 1L,
            updatedAtEpochMs = 1L,
        )

    @Test(timeout = 10_000)
    fun findsStrongDuplicateInsideLargeCrmSetWithoutPairExplosion() {
        val customers = (0 until 6576).map(::customer).toMutableList()
        customers += customer(9001, phone = "05321112233", name = "Özel Mükerrer")
        customers += customer(9002, phone = "+90 532 111 22 33", name = "Özel Mükerrer")

        val candidates = CrmDuplicateDetector.find(customers)

        assertTrue(
            candidates.any { candidate ->
                setOf(candidate.first.id, candidate.second.id) == setOf("dup-9001", "dup-9002")
            },
        )
    }
    @Test(timeout = 2_000)
    fun interruptedAnalysisReturnsPromptly() {
        val customers = (0 until 10_000).map(::customer)
        Thread.currentThread().interrupt()
        try {
            assertTrue(CrmDuplicateDetector.find(customers).isEmpty())
        } finally {
            // JUnit worker thread must not leak the interrupt flag into later tests.
            Thread.interrupted()
        }
    }

}

package com.lanu.globaldonuksatisradari.crm

import kotlin.test.Test
import kotlin.test.assertEquals

class CrmConflictResolverTest {
    private fun customer(
        version: Long,
        updatedAt: Long,
        stage: CrmStage = CrmStage.PROSPECT,
        notes: String? = null,
    ) = CrmCustomer(
        id = "customer-1",
        businessSourceId = "n123",
        businessName = "Örnek İşletme",
        city = "İstanbul",
        district = "Kadıköy",
        neighborhood = null,
        stage = stage,
        createdAtEpochMs = 100L,
        updatedAtEpochMs = updatedAt,
        version = version,
        notes = notes,
    )

    @Test
    fun `higher remote version wins`() {
        val local = customer(version = 2, updatedAt = 200)
        val remote = RemoteCustomerSnapshot(customer(version = 3, updatedAt = 150))
        assertEquals(ConflictResolution.REMOTE_WINS, CrmConflictResolver.resolve(local, remote))
    }

    @Test
    fun `higher local version wins`() {
        val local = customer(version = 4, updatedAt = 200)
        val remote = RemoteCustomerSnapshot(customer(version = 3, updatedAt = 300))
        assertEquals(ConflictResolution.LOCAL_WINS, CrmConflictResolver.resolve(local, remote))
    }

    @Test
    fun `equal versions use update time`() {
        val local = customer(version = 4, updatedAt = 200)
        val remote = RemoteCustomerSnapshot(customer(version = 4, updatedAt = 300, stage = CrmStage.VISIT))
        assertEquals(ConflictResolution.REMOTE_WINS, CrmConflictResolver.resolve(local, remote))
    }

    @Test
    fun `equal version and timestamp with different content stays unresolved`() {
        val local = customer(version = 4, updatedAt = 200, notes = "yerel")
        val remote = RemoteCustomerSnapshot(customer(version = 4, updatedAt = 200, notes = "uzak"))
        assertEquals(ConflictResolution.UNRESOLVED, CrmConflictResolver.resolve(local, remote))
    }
}

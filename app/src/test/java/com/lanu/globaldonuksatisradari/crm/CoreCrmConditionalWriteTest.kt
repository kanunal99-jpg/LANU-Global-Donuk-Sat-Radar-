package com.lanu.globaldonuksatisradari.crm

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class CoreCrmConditionalWriteTest {
    private val customerId = "00000000-0000-0000-0000-000000000091"
    private fun operation(
        operation: String = LocalCrmRepository.OP_CREATE,
        version: Long = 1L,
        id: String = customerId,
    ) = SyncOperationEntity(
        id = "00000000-0000-0000-0000-000000000092",
        entityType = LocalCrmRepository.ENTITY_CUSTOMER,
        entityId = id,
        operation = operation,
        payloadVersion = version,
        payloadJson = "{}",
        createdAtEpochMs = 1L,
        attemptCount = 0,
        lastError = null,
    )

    @Test
    fun newCustomer_isCreateOnly_neverMergeDuplicates() {
        val plan = planSafeCustomerWrite(
            operation(), JSONObject().put("id", customerId).put("sync_version", 1),
        )
        assertEquals("POST", plan.method)
        assertEquals("/rest/v1/lanu_crm_customers?on_conflict=id", plan.path)
        assertTrue(plan.createOnly)
    }

    @Test
    fun updateUsesIdAndPreviousVersionAsAtomicCondition() {
        val plan = planSafeCustomerWrite(
            operation(operation = LocalCrmRepository.OP_UPDATE, version = 4L),
            JSONObject().put("id", customerId).put("sync_version", 4),
        )
        assertEquals("PATCH", plan.method)
        assertEquals(
            "/rest/v1/lanu_crm_customers?id=eq.$customerId&sync_version=eq.3",
            plan.path,
        )
        assertFalse(plan.createOnly)
    }

    @Test
    fun updateAtInitialVersion_doesNotSwitchToCreate() {
        val plan = planSafeCustomerWrite(
            operation(operation = LocalCrmRepository.OP_UPDATE, version = 1L),
            JSONObject().put("id", customerId).put("sync_version", 1),
        )
        assertEquals("PATCH", plan.method)
        assertTrue(plan.path.endsWith("sync_version=eq.0"))
    }

    @Test
    fun wrongRecordVersionAndBadId_areRejectedLocally() {
        assertThrows(IllegalArgumentException::class.java) {
            planSafeCustomerWrite(operation(version = 2L), JSONObject().put("id", customerId).put("sync_version", 1))
        }
        assertThrows(IllegalArgumentException::class.java) {
            planSafeCustomerWrite(operation(), JSONObject().put("id", "spoofed").put("sync_version", 1))
        }
        assertThrows(IllegalArgumentException::class.java) {
            planSafeCustomerWrite(operation(id = "not-a-uuid"), JSONObject().put("id", "not-a-uuid").put("sync_version", 1))
        }
    }
}

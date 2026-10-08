package com.lanu.globaldonuksatisradari.crm

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class AtomicCrmMutationTest {
    private val operationId = "00000000-0000-0000-0000-000000000501"
    private val entityId = "00000000-0000-0000-0000-000000000502"

    private fun op(version: Long = 1, type: String = CommercialCrmSync.ENTITY_QUOTE) =
        SyncOperationEntity(
            id = operationId,
            entityType = type,
            entityId = entityId,
            operation = "update",
            payloadVersion = version,
            payloadJson = "{}",
            createdAtEpochMs = 1L,
            attemptCount = 0,
            lastError = null,
        )

    @Test
    fun create_expectsVersionZero_andKeepsStableIdempotencyKey() {
        val request = JSONObject(buildAtomicCrmMutationRequest(
            op(), JSONObject().put("id", entityId).put("version", 1L),
        ))
        assertEquals(operationId, request.getString("p_operation_id"))
        assertEquals("quote", request.getString("p_entity_type"))
        assertEquals(0L, request.getLong("p_expected_version"))
        assertEquals(entityId, request.getJSONObject("p_payload").getString("id"))
    }

    @Test
    fun update_expectsExactlyPreviousVersion() {
        val request = JSONObject(buildAtomicCrmMutationRequest(
            op(7), JSONObject().put("id", entityId).put("version", 7L),
        ))
        assertEquals(6L, request.getLong("p_expected_version"))
    }

    @Test
    fun allFiveCommercialEntities_areSupported() {
        for (type in listOf("contact", "quote", "quote_line", "order", "order_line")) {
            val request = JSONObject(buildAtomicCrmMutationRequest(
                op(type = type), JSONObject().put("id", entityId).put("version", 1L),
            ))
            assertEquals(type, request.getString("p_entity_type"))
        }
    }

    @Test
    fun incorrectVersionOrIdentity_isRejectedBeforeNetwork() {
        assertThrows(IllegalArgumentException::class.java) {
            buildAtomicCrmMutationRequest(op(version = 2), JSONObject().put("id", entityId).put("version", 1))
        }
        assertThrows(IllegalArgumentException::class.java) {
            buildAtomicCrmMutationRequest(op(), JSONObject().put("id", "wrong-id").put("version", 1))
        }
        assertThrows(IllegalArgumentException::class.java) {
            buildAtomicCrmMutationRequest(op(type = "activity"), JSONObject().put("id", entityId).put("version", 1))
        }
    }

    @Test
    fun onlyExplicitApplied_isSuccessful() {
        assertEquals(AtomicCrmMutationStatus.APPLIED, parseAtomicCrmMutationStatus("\"APPLIED\""))
        assertEquals(AtomicCrmMutationStatus.CONFLICT, parseAtomicCrmMutationStatus("\"CONFLICT\""))
        assertEquals(AtomicCrmMutationStatus.INVALID_VERSION, parseAtomicCrmMutationStatus("\"INVALID_VERSION\""))
        for (invalid in listOf("", "{}", "null", "\"UNKNOWN\"", "[]", "\"\"")) {
            assertThrows(IllegalStateException::class.java) {
                parseAtomicCrmMutationStatus(invalid)
            }
        }
    }
}

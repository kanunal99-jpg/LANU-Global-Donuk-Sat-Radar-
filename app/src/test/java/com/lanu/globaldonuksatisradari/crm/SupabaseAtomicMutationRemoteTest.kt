package com.lanu.globaldonuksatisradari.crm

import org.json.JSONObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SupabaseAtomicMutationRemoteTest {
    @Test
    fun `rpc result parser accepts PostgREST JSON string results`() {
        assertEquals(AtomicMutationStatus.APPLIED, AtomicMutationResultParser.parse("\"APPLIED\""))
        assertEquals(AtomicMutationStatus.CONFLICT, AtomicMutationResultParser.parse("\"CONFLICT\""))
        assertEquals(AtomicMutationStatus.INVALID_VERSION, AtomicMutationResultParser.parse("\"INVALID_VERSION\""))
    }

    @Test
    fun `customer payload maps local fields to production columns and version`() {
        val local = JSONObject().apply {
            put("id", "20000000-0000-4000-8000-000000000001")
            put("businessSourceId", "manual:source-1")
            put("businessName", "Müşteri")
            put("city", "İstanbul")
            put("district", "Kadıköy")
            put("neighborhood", JSONObject.NULL)
            put("address", "Adres")
            put("latitude", 40.99)
            put("longitude", 29.03)
            put("dataQuality", "USER_ENTERED")
            put("stage", "PROSPECT")
            put("notes", JSONObject.NULL)
            put("createdAtEpochMs", 1_700_000_000_000L)
            put("updatedAtEpochMs", 1_700_000_001_000L)
            put("version", 3L)
        }

        val row = AtomicMutationPayloadMapper.toDatabaseRow(
            LocalCrmRepository.ENTITY_CUSTOMER,
            local,
            "10000000-0000-4000-8000-000000000001",
        )

        assertEquals("10000000-0000-4000-8000-000000000001", row.getString("owner_user_id"))
        assertEquals("manual", row.getString("source"))
        assertEquals("manual:source-1", row.getString("source_id"))
        assertEquals("Müşteri", row.getString("name"))
        assertEquals(3L, row.getLong("sync_version"))
        assertTrue(row.isNull("neighborhood"))
        assertTrue(row.isNull("notes"))
    }

    @Test
    fun `opportunity mapper keeps fixed point money exact`() {
        val local = JSONObject().apply {
            put("id", "22000000-0000-4000-8000-000000000001")
            put("customerId", "20000000-0000-4000-8000-000000000001")
            put("title", "Fırsat")
            put("status", "OPEN")
            put("notes", JSONObject.NULL)
            put("estimatedValueMinor", 12_345L)
            put("currency", "TRY")
            put("valueOrigin", "USER_ENTERED")
            put("createdAtEpochMs", 1_700_000_000_000L)
            put("updatedAtEpochMs", 1_700_000_001_000L)
            put("version", 2L)
        }

        val row = AtomicMutationPayloadMapper.toDatabaseRow(
            LocalCrmRepository.ENTITY_OPPORTUNITY,
            local,
            "10000000-0000-4000-8000-000000000001",
        )

        assertEquals("123.45", row.getString("amount"))
        assertEquals("USER_ENTERED", row.getString("amount_origin"))
        assertEquals(2L, row.getLong("version"))
    }

    @Test
    fun `commercial nullable fields stay null in database row`() {
        val local = JSONObject().apply {
            put("id", "23000000-0000-4000-8000-000000000001")
            put("customerId", "20000000-0000-4000-8000-000000000001")
            put("quoteNumber", "Q-1")
            put("opportunityId", JSONObject.NULL)
            put("status", "DRAFT")
            put("currency", "TRY")
            put("totalMinor", 0L)
            put("validUntilEpochMs", JSONObject.NULL)
            put("notes", JSONObject.NULL)
            put("createdAtEpochMs", 1L)
            put("updatedAtEpochMs", 1L)
            put("version", 1L)
        }

        val row = AtomicMutationPayloadMapper.toDatabaseRow(
            CommercialCrmSync.ENTITY_QUOTE,
            local,
            "10000000-0000-4000-8000-000000000001",
        )

        assertTrue(row.isNull("opportunity_id"))
        assertTrue(row.isNull("valid_until_epoch_ms"))
        assertTrue(row.isNull("notes"))
        assertEquals(1L, row.getLong("version"))
    }
}

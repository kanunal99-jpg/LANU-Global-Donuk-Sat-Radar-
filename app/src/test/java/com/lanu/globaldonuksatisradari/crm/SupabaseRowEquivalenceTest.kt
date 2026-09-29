package com.lanu.globaldonuksatisradari.crm

import org.json.JSONObject
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SupabaseRowEquivalenceTest {
    @Test
    fun `numeric and timestamp representation differences remain idempotent`() {
        val remote = JSONObject().apply {
            put("amount", 100)
            put("updated_at", "2026-09-29T18:00:00+00:00")
            put("note", JSONObject.NULL)
        }
        val outgoing = JSONObject().apply {
            put("amount", "100.00")
            put("updated_at", "2026-09-29T18:00:00Z")
            put("note", JSONObject.NULL)
        }

        assertTrue(SupabaseRowEquivalence.matchesOutgoing(remote, outgoing))
    }

    @Test
    fun `same version retry with different business content is not equivalent`() {
        val remote = JSONObject().apply {
            put("id", "row-1")
            put("version", 3L)
            put("status", "ACCEPTED")
        }
        val outgoing = JSONObject().apply {
            put("id", "row-1")
            put("version", 3L)
            put("status", "REJECTED")
        }

        assertFalse(SupabaseRowEquivalence.matchesOutgoing(remote, outgoing))
    }

    @Test
    fun `missing outgoing field in remote is not treated as identical`() {
        val remote = JSONObject().apply { put("id", "row-1") }
        val outgoing = JSONObject().apply {
            put("id", "row-1")
            put("currency", "TRY")
        }

        assertFalse(SupabaseRowEquivalence.matchesOutgoing(remote, outgoing))
    }
}

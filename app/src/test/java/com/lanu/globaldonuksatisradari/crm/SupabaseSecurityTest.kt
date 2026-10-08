package com.lanu.globaldonuksatisradari.crm

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class SupabaseSecurityTest {
    @Test
    fun passwordGrant_topLevelTokens_withUser_areParsed() {
        val response = JSONObject(
            """{"access_token":"access","refresh_token":"refresh","user":{"id":"owner-a"}}""",
        )
        assertEquals(SupabaseSession("access", "refresh", "owner-a"), readSupabaseSession(response))
    }

    @Test
    fun nestedSignupSession_isParsed() {
        val response = JSONObject(
            """{"user":{"id":"owner-a"},"session":{"access_token":"access","refresh_token":"refresh"}}""",
        )
        assertEquals(SupabaseSession("access", "refresh", "owner-a"), readSupabaseSession(response))
    }

    @Test
    fun emailConfirmationWithoutTokens_mustNotBecomeAuthenticated() {
        assertNull(readSupabaseSession(JSONObject("""{"user":{"id":"owner-a"}}""")))
    }

    @Test
    fun tokenWithoutIdentifiedUser_mustNotBecomeAuthenticated() {
        assertNull(readSupabaseSession(JSONObject("""{"access_token":"a","refresh_token":"r"}""")))
    }

    @Test
    fun validRemoteAcknowledgement_requiresMatchingSingleId() {
        requireRemoteWriteAcknowledgement("""[{"id":"crm-1","version":3}]""", "crm-1")
        for (body in listOf("[]", "{}", "", """[{"id":"crm-2"}]""",
            """[{"id":"crm-1"},{"id":"crm-1"}]""")) {
            assertThrows(IllegalStateException::class.java) {
                requireRemoteWriteAcknowledgement(body, "crm-1")
            }
        }
    }

    @Test
    fun blankIdentity_rejected() {
        assertThrows(IllegalArgumentException::class.java) {
            requireRemoteWriteAcknowledgement("""[{"id":""}]""", "")
        }
    }
}

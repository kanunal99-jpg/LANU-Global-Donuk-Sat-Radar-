package com.lanu.globaldonuksatisradari.crm

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SupabaseAuthResponseParserTest {
    @Test
    fun parsesRawGoTrueTopLevelTokenResponse() {
        val response = JSONObject()
            .put("access_token", "access-top")
            .put("refresh_token", "refresh-top")
            .put("user", JSONObject().put("id", "user-top"))

        assertEquals(
            SupabaseSession("access-top", "refresh-top", "user-top"),
            parseSupabaseSession(response),
        )
    }

    @Test
    fun keepsCompatibilityWithNestedSessionShape() {
        val response = JSONObject()
            .put(
                "session",
                JSONObject()
                    .put("access_token", "access-nested")
                    .put("refresh_token", "refresh-nested")
                    .put("user", JSONObject().put("id", "user-nested")),
            )

        assertEquals(
            SupabaseSession("access-nested", "refresh-nested", "user-nested"),
            parseSupabaseSession(response),
        )
    }

    @Test
    fun rejectsPartialTokenResponse() {
        val response = JSONObject()
            .put("access_token", "access-only")
            .put("user", JSONObject().put("id", "user"))

        assertNull(parseSupabaseSession(response))
    }
}

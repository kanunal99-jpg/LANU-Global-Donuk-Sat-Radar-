package com.lanu.globaldonuksatisradari.crm

import org.json.JSONArray

/**
 * PostgREST may return HTTP 2xx with ZERO changed rows when RLS filters a write.
 * Never discard an offline operation without proof that the expected row was written.
 *
 * Requires "Prefer: resolution=merge-duplicates,return=representation" for uploads.
 */
internal fun requireRemoteWriteAcknowledgement(response: String, expectedId: String) {
    require(expectedId.isNotBlank()) { "Invalid CRM write identity." }
    val rows = try {
        JSONArray(response)
    } catch (error: Exception) {
        throw IllegalStateException("CRM sunucu yazma onayı ayrıştırılamadı.", error)
    }
    if (rows.length() != 1 || rows.optJSONObject(0)?.optString("id") != expectedId) {
        throw IllegalStateException("CRM sunucusu kayıt yazımını doğrulamadı; işlem kuyrukta kalacak.")
    }
}

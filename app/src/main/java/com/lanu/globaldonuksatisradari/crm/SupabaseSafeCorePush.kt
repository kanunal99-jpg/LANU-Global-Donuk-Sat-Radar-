package com.lanu.globaldonuksatisradari.crm

import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal

/**
 * Optimistic-concurrency preflight for legacy/core CRM pushes.
 *
 * The existing core adapter remains the actual writer. This guard closes the equal-version race:
 * a retry of an already-applied identical mutation is idempotent, while a different remote row at
 * the same version is surfaced as CONFLICT instead of being silently overwritten.
 */
class SupabaseSafeCorePush(
    private val auth: SupabaseAuthClient,
) {
    private val delegate = SupabaseCrmRemoteDataSource(auth)

    suspend fun apply(operation: SyncOperationEntity): RemoteSyncResult {
        val session = auth.ensureSession() ?: return RemoteSyncResult.NotConfigured
        return runCatching {
            val payload = JSONObject(operation.payloadJson)
            val table = tableFor(operation.entityType)
            val outgoing = rowFor(operation.entityType, payload, session.userId)
            val remote = fetchRemoteRow(table, payload.optString("id"), session)

            if (remote != null) {
                val remoteVersion = versionFor(operation.entityType, remote)
                when {
                    remoteVersion > operation.payloadVersion -> {
                        return RemoteSyncResult.Conflict(
                            "Uzak kayıt sürümü daha yeni: remote=$remoteVersion local=${operation.payloadVersion}",
                        )
                    }
                    remoteVersion == operation.payloadVersion &&
                        SupabaseRowEquivalence.matchesOutgoing(remote, outgoing) -> {
                        return RemoteSyncResult.Success
                    }
                    remoteVersion == operation.payloadVersion -> {
                        return RemoteSyncResult.Conflict(
                            "Aynı sürüm numarasında farklı uzak içerik algılandı: version=$remoteVersion",
                        )
                    }
                }
            }

            delegate.apply(operation)
        }.getOrElse(::mapFailure)
    }

    private fun tableFor(entityType: String): String = when (entityType) {
        LocalCrmRepository.ENTITY_CUSTOMER -> "lanu_crm_customers"
        LocalCrmRepository.ENTITY_ACTIVITY -> "lanu_crm_activities"
        LocalCrmRepository.ENTITY_NEXT_ACTION -> "lanu_crm_next_actions"
        LocalCrmRepository.ENTITY_OPPORTUNITY -> "lanu_crm_opportunities"
        else -> error("Bilinmeyen core CRM entity: $entityType")
    }

    private fun versionFor(entityType: String, remote: JSONObject): Long = when (entityType) {
        LocalCrmRepository.ENTITY_CUSTOMER -> remote.optLong("sync_version", 1L)
        LocalCrmRepository.ENTITY_NEXT_ACTION,
        LocalCrmRepository.ENTITY_OPPORTUNITY,
        -> remote.optLong("version", 1L)
        LocalCrmRepository.ENTITY_ACTIVITY -> 1L
        else -> 1L
    }

    private fun rowFor(entityType: String, p: JSONObject, userId: String): JSONObject = when (entityType) {
        LocalCrmRepository.ENTITY_CUSTOMER -> customerRow(p, userId)
        LocalCrmRepository.ENTITY_ACTIVITY -> activityRow(p, userId)
        LocalCrmRepository.ENTITY_NEXT_ACTION -> nextActionRow(p, userId)
        LocalCrmRepository.ENTITY_OPPORTUNITY -> opportunityRow(p, userId)
        else -> error("Bilinmeyen core CRM entity: $entityType")
    }

    private fun fetchRemoteRow(
        table: String,
        id: String,
        session: SupabaseSession,
    ): JSONObject? {
        if (id.isBlank()) return null
        val text = auth.rawRequest(
            "GET",
            "/rest/v1/$table?select=*&id=eq.$id&limit=1",
            null,
            session.accessToken,
        )
        val array = JSONArray(text)
        return if (array.length() == 0) null else array.getJSONObject(0)
    }

    private fun customerRow(p: JSONObject, userId: String) = JSONObject().apply {
        put("id", p.getString("id"))
        put("owner_user_id", userId)
        put("stage", p.getString("stage"))
        put("source", if (p.optString("businessSourceId").startsWith("manual:")) "manual" else "osm")
        put("source_id", p.getString("businessSourceId"))
        put("name", p.getString("businessName"))
        put("city", p.getString("city"))
        put("district", p.getString("district"))
        put("neighborhood", p.optString("neighborhood").takeIf(String::isNotBlank) ?: JSONObject.NULL)
        put("address", p.optString("address").takeIf(String::isNotBlank) ?: JSONObject.NULL)
        put("latitude", if (p.isNull("latitude")) JSONObject.NULL else p.getDouble("latitude"))
        put("longitude", if (p.isNull("longitude")) JSONObject.NULL else p.getDouble("longitude"))
        put("data_quality", p.optString("dataQuality").ifBlank { "UNKNOWN" })
        put("notes", p.optString("notes").takeIf(String::isNotBlank) ?: JSONObject.NULL)
        put("sync_version", p.optLong("version", 1L))
    }

    private fun activityRow(p: JSONObject, userId: String) = JSONObject().apply {
        put("id", p.getString("id"))
        put("owner_user_id", userId)
        put("customer_id", p.getString("customerId"))
        put("type", p.getString("type"))
        put("note", p.optString("note").takeIf(String::isNotBlank) ?: JSONObject.NULL)
        put("occurred_at", epochToIso(p.getLong("occurredAtEpochMs")))
        put("created_at", epochToIso(p.getLong("createdAtEpochMs")))
    }

    private fun nextActionRow(p: JSONObject, userId: String) = JSONObject().apply {
        put("id", p.getString("id"))
        put("owner_user_id", userId)
        put("customer_id", p.getString("customerId"))
        put("type", p.getString("type"))
        put("due_at", epochToIso(p.getLong("dueAtEpochMs")))
        put("note", p.optString("note").takeIf(String::isNotBlank) ?: JSONObject.NULL)
        put("created_by_user_id", p.optString("createdByUserId").takeIf(String::isNotBlank) ?: userId)
        put(
            "completed_at",
            p.optLong("completedAtEpochMs", 0L).takeIf { it > 0 }?.let(::epochToIso) ?: JSONObject.NULL,
        )
        put("completed_by_user_id", p.optString("completedByUserId").takeIf(String::isNotBlank) ?: JSONObject.NULL)
        put("version", p.optLong("version", 1L))
    }

    private fun opportunityRow(p: JSONObject, userId: String) = JSONObject().apply {
        put("id", p.getString("id"))
        put("owner_user_id", userId)
        put("customer_id", p.getString("customerId"))
        put("title", p.getString("title"))
        put("status", p.getString("status"))
        val minor = if (p.isNull("estimatedValueMinor")) null else p.optLong("estimatedValueMinor")
        put("amount", minor?.let { BigDecimal(it).movePointLeft(2).toPlainString() } ?: JSONObject.NULL)
        put("currency", p.optString("currency").takeIf(String::isNotBlank) ?: JSONObject.NULL)
        put("amount_origin", p.optString("valueOrigin").ifBlank { "UNKNOWN" })
        put("note", p.optString("notes").takeIf(String::isNotBlank) ?: JSONObject.NULL)
        put("created_at", epochToIso(p.getLong("createdAtEpochMs")))
        put("updated_at", epochToIso(p.getLong("updatedAtEpochMs")))
        put("version", p.optLong("version", 1L))
    }

    private fun epochToIso(epochMs: Long): String = java.time.Instant.ofEpochMilli(epochMs).toString()

    private fun mapFailure(error: Throwable): RemoteSyncResult = when (error) {
        is SupabaseHttpException -> when {
            error.code == 401 -> RemoteSyncResult.RetryableFailure("Oturum süresi doldu.")
            error.code == 409 || error.code == 412 -> RemoteSyncResult.Conflict(error.message)
            error.code in 408..599 -> RemoteSyncResult.RetryableFailure(error.message)
            else -> RemoteSyncResult.PermanentFailure(error.message)
        }
        else -> RemoteSyncResult.RetryableFailure(error.message ?: "Bilinmeyen ağ hatası")
    }
}

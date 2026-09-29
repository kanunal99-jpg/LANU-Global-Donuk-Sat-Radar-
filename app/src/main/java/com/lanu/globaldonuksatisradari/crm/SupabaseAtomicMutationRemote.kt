package com.lanu.globaldonuksatisradari.crm

import org.json.JSONObject
import java.math.BigDecimal

/**
 * Adds an atomic optimistic-concurrency boundary to the canonical full CRM remote.
 *
 * Pulls continue through [SupabaseFullCrmRemoteDataSource], which already preserves dirty Room
 * rows. Append-only activities retain the proven direct adapter. Every versioned entity is pushed
 * through the production `lanu_apply_versioned_crm_mutation` SECURITY INVOKER RPC so version check,
 * row mutation and operation-id idempotency occur in one PostgreSQL transaction under RLS.
 */
class SupabaseAtomicMutationRemoteDataSource(
    private val auth: SupabaseAuthClient,
) : RemoteCrmDataSource {
    private val delegate = SupabaseFullCrmRemoteDataSource(auth)

    override suspend fun pullInto(database: LanuCrmDatabase): RemotePullResult =
        delegate.pullInto(database)

    override suspend fun apply(operation: SyncOperationEntity): RemoteSyncResult {
        if (operation.entityType !in VERSIONED_ENTITY_TYPES) return delegate.apply(operation)
        val session = auth.ensureSession() ?: return RemoteSyncResult.NotConfigured
        return runCatching {
            require(operation.payloadVersion >= 1L) { "Senkronizasyon sürümü en az 1 olmalıdır." }
            val payload = JSONObject(operation.payloadJson)
            val row = AtomicMutationPayloadMapper.toDatabaseRow(operation.entityType, payload, session.userId)
            val request = JSONObject().apply {
                put("p_operation_id", operation.id)
                put("p_entity_type", operation.entityType)
                put("p_payload", row)
                put("p_expected_version", operation.payloadVersion - 1L)
            }
            val raw = auth.rawRequest(
                "POST",
                "/rest/v1/rpc/lanu_apply_versioned_crm_mutation",
                request.toString(),
                session.accessToken,
            )
            when (AtomicMutationResultParser.parse(raw)) {
                AtomicMutationStatus.APPLIED -> RemoteSyncResult.Success
                AtomicMutationStatus.CONFLICT -> RemoteSyncResult.Conflict(
                    "Kayıt başka bir cihazda aynı temel sürümden değiştirilmiş. Yerel değişiklik korundu.",
                )
                AtomicMutationStatus.INVALID_VERSION -> RemoteSyncResult.PermanentFailure(
                    "Geçersiz CRM sürüm zinciri: local=${operation.payloadVersion}",
                )
            }
        }.getOrElse { error ->
            when (error) {
                is SupabaseHttpException -> when {
                    error.code == 401 -> RemoteSyncResult.RetryableFailure("Oturum süresi doldu.")
                    error.code == 409 || error.code == 412 -> RemoteSyncResult.Conflict(error.message)
                    error.code in 408..599 -> RemoteSyncResult.RetryableFailure(error.message)
                    else -> RemoteSyncResult.PermanentFailure(error.message)
                }
                else -> RemoteSyncResult.RetryableFailure(error.message ?: "Atomik CRM senkronizasyonu başarısız.")
            }
        }
    }

    private companion object {
        val VERSIONED_ENTITY_TYPES = setOf(
            LocalCrmRepository.ENTITY_CUSTOMER,
            LocalCrmRepository.ENTITY_NEXT_ACTION,
            LocalCrmRepository.ENTITY_OPPORTUNITY,
            CommercialCrmSync.ENTITY_CONTACT,
            CommercialCrmSync.ENTITY_QUOTE,
            CommercialCrmSync.ENTITY_QUOTE_LINE,
            CommercialCrmSync.ENTITY_ORDER,
            CommercialCrmSync.ENTITY_ORDER_LINE,
        )
    }
}

internal enum class AtomicMutationStatus { APPLIED, CONFLICT, INVALID_VERSION }

internal object AtomicMutationResultParser {
    fun parse(raw: String): AtomicMutationStatus {
        val value = raw.trim().removeSurrounding("\"").uppercase()
        return when (value) {
            "APPLIED" -> AtomicMutationStatus.APPLIED
            "CONFLICT" -> AtomicMutationStatus.CONFLICT
            "INVALID_VERSION" -> AtomicMutationStatus.INVALID_VERSION
            else -> error("Bilinmeyen atomik CRM sonucu: $raw")
        }
    }
}

internal object AtomicMutationPayloadMapper {
    fun toDatabaseRow(entityType: String, p: JSONObject, userId: String): JSONObject = when (entityType) {
        LocalCrmRepository.ENTITY_CUSTOMER -> JSONObject().apply {
            put("id", p.getString("id"))
            put("owner_user_id", userId)
            put("stage", p.getString("stage"))
            put("source", if (p.optString("businessSourceId").startsWith("manual:")) "manual" else "osm")
            put("source_id", p.getString("businessSourceId"))
            put("name", p.getString("businessName"))
            put("city", p.getString("city"))
            put("district", p.getString("district"))
            putNullable("neighborhood", p, "neighborhood")
            putNullable("address", p, "address")
            putNullable("latitude", p, "latitude")
            putNullable("longitude", p, "longitude")
            put("data_quality", p.optString("dataQuality").ifBlank { "UNKNOWN" })
            putNullable("notes", p, "notes")
            put("sync_version", p.optLong("version", 1L))
            put("created_at", epochToIso(p.getLong("createdAtEpochMs")))
            put("updated_at", epochToIso(p.getLong("updatedAtEpochMs")))
        }

        LocalCrmRepository.ENTITY_NEXT_ACTION -> JSONObject().apply {
            put("id", p.getString("id"))
            put("owner_user_id", userId)
            put("customer_id", p.getString("customerId"))
            put("type", p.getString("type"))
            put("due_at", epochToIso(p.getLong("dueAtEpochMs")))
            putNullable("note", p, "note")
            put("created_by_user_id", p.optString("createdByUserId").ifBlank { userId })
            putNullableEpoch("completed_at", p, "completedAtEpochMs")
            putNullable("completed_by_user_id", p, "completedByUserId")
            put("version", p.optLong("version", 1L))
            put("created_at", epochToIso(p.getLong("createdAtEpochMs")))
        }

        LocalCrmRepository.ENTITY_OPPORTUNITY -> JSONObject().apply {
            put("id", p.getString("id"))
            put("owner_user_id", userId)
            put("customer_id", p.getString("customerId"))
            put("title", p.getString("title"))
            put("status", p.getString("status"))
            if (p.isNull("estimatedValueMinor")) {
                put("amount", JSONObject.NULL)
            } else {
                put("amount", BigDecimal.valueOf(p.getLong("estimatedValueMinor"), 2).toPlainString())
            }
            putNullable("currency", p, "currency")
            put("amount_origin", p.optString("valueOrigin").ifBlank { "UNKNOWN" })
            putNullable("note", p, "notes")
            put("version", p.optLong("version", 1L))
            put("created_at", epochToIso(p.getLong("createdAtEpochMs")))
            put("updated_at", epochToIso(p.getLong("updatedAtEpochMs")))
        }

        CommercialCrmSync.ENTITY_CONTACT -> JSONObject().apply {
            put("id", p.getString("id"))
            put("customer_id", p.getString("customerId"))
            put("full_name", p.getString("fullName"))
            putNullable("role", p, "role")
            putNullable("phone", p, "phone")
            putNullable("email", p, "email")
            put("is_primary", p.optBoolean("isPrimary", false))
            put("created_at_epoch_ms", p.getLong("createdAtEpochMs"))
            put("updated_at_epoch_ms", p.getLong("updatedAtEpochMs"))
            put("version", p.optLong("version", 1L))
        }

        CommercialCrmSync.ENTITY_QUOTE -> JSONObject().apply {
            put("id", p.getString("id"))
            put("customer_id", p.getString("customerId"))
            putNullable("opportunity_id", p, "opportunityId")
            put("quote_number", p.getString("quoteNumber"))
            put("status", p.getString("status"))
            put("currency", p.getString("currency"))
            put("total_minor", p.getLong("totalMinor"))
            putNullable("valid_until_epoch_ms", p, "validUntilEpochMs")
            putNullable("notes", p, "notes")
            put("created_at_epoch_ms", p.getLong("createdAtEpochMs"))
            put("updated_at_epoch_ms", p.getLong("updatedAtEpochMs"))
            put("version", p.optLong("version", 1L))
        }

        CommercialCrmSync.ENTITY_QUOTE_LINE -> commercialLine(p, "quote_id", "quoteId")

        CommercialCrmSync.ENTITY_ORDER -> JSONObject().apply {
            put("id", p.getString("id"))
            put("customer_id", p.getString("customerId"))
            putNullable("quote_id", p, "quoteId")
            put("order_number", p.getString("orderNumber"))
            put("status", p.getString("status"))
            put("currency", p.getString("currency"))
            put("total_minor", p.getLong("totalMinor"))
            putNullable("notes", p, "notes")
            put("created_at_epoch_ms", p.getLong("createdAtEpochMs"))
            put("updated_at_epoch_ms", p.getLong("updatedAtEpochMs"))
            put("version", p.optLong("version", 1L))
        }

        CommercialCrmSync.ENTITY_ORDER_LINE -> commercialLine(p, "order_id", "orderId")
        else -> error("Atomik mutasyon için bilinmeyen entity: $entityType")
    }

    private fun commercialLine(p: JSONObject, parentColumn: String, parentPayloadKey: String) =
        JSONObject().apply {
            put("id", p.getString("id"))
            put(parentColumn, p.getString(parentPayloadKey))
            putNullable("product_id", p, "productId")
            put("product_name", p.getString("productName"))
            put("unit", p.getString("unit"))
            put("quantity_milli", p.getLong("quantityMilli"))
            put("unit_price_minor", p.getLong("unitPriceMinor"))
            put("discount_basis_points", p.getInt("discountBasisPoints"))
            put("line_total_minor", p.getLong("lineTotalMinor"))
            put("created_at_epoch_ms", p.getLong("createdAtEpochMs"))
            put("updated_at_epoch_ms", p.getLong("updatedAtEpochMs"))
            put("version", p.optLong("version", 1L))
        }

    private fun JSONObject.putNullable(targetKey: String, source: JSONObject, sourceKey: String) {
        if (source.isNull(sourceKey)) put(targetKey, JSONObject.NULL) else put(targetKey, source.get(sourceKey))
    }

    private fun JSONObject.putNullableEpoch(targetKey: String, source: JSONObject, sourceKey: String) {
        if (source.isNull(sourceKey)) {
            put(targetKey, JSONObject.NULL)
        } else {
            val epoch = source.optLong(sourceKey, 0L)
            if (epoch <= 0L) put(targetKey, JSONObject.NULL) else put(targetKey, epochToIso(epoch))
        }
    }

    private fun epochToIso(epochMs: Long): String = java.time.Instant.ofEpochMilli(epochMs).toString()
}

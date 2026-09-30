package com.lanu.globaldonuksatisradari.crm

import org.json.JSONArray
import org.json.JSONObject

/** Equal-version divergence guard for contact/quote/order/product-line pushes. */
class SupabaseSafeCommercialPush(
    private val auth: SupabaseAuthClient,
) {
    private val delegate = SupabaseCommercialRemoteDataSource(auth)

    suspend fun apply(operation: SyncOperationEntity): RemoteSyncResult {
        val session = auth.ensureSession() ?: return RemoteSyncResult.NotConfigured
        return runCatching {
            val payload = JSONObject(operation.payloadJson)
            val table = tableFor(operation.entityType)
            val outgoing = rowFor(operation.entityType, payload)
            val remote = fetchRemoteRow(table, payload.optString("id"), session)

            if (remote != null) {
                val remoteVersion = remote.optLong("version", 1L)
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
        CommercialCrmSync.ENTITY_CONTACT -> "lanu_crm_contacts"
        CommercialCrmSync.ENTITY_QUOTE -> "lanu_crm_quotes"
        CommercialCrmSync.ENTITY_QUOTE_LINE -> "lanu_crm_quote_lines"
        CommercialCrmSync.ENTITY_ORDER -> "lanu_crm_orders"
        CommercialCrmSync.ENTITY_ORDER_LINE -> "lanu_crm_order_lines"
        else -> error("Bilinmeyen ticari CRM entity: $entityType")
    }

    private fun rowFor(entityType: String, p: JSONObject): JSONObject = when (entityType) {
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

        CommercialCrmSync.ENTITY_QUOTE_LINE -> commercialLineRow(p, "quote_id", "quoteId")

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

        CommercialCrmSync.ENTITY_ORDER_LINE -> commercialLineRow(p, "order_id", "orderId")
        else -> error("Bilinmeyen ticari CRM entity: $entityType")
    }

    private fun commercialLineRow(p: JSONObject, parentColumn: String, parentPayloadKey: String) =
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

    private fun JSONObject.putNullable(targetKey: String, source: JSONObject, sourceKey: String) {
        if (source.isNull(sourceKey)) put(targetKey, JSONObject.NULL) else put(targetKey, source.get(sourceKey))
    }

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

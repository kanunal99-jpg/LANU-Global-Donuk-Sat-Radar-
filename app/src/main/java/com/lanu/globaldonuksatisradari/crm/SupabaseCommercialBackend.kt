package com.lanu.globaldonuksatisradari.crm

import androidx.room.withTransaction
import org.json.JSONArray
import org.json.JSONObject

/**
 * Adds contact + commercial cloud synchronization without changing the proven core CRM adapter.
 * The composite below routes legacy CRM entities to SupabaseCrmRemoteDataSource and commercial
 * entities here. This keeps a tested fallback boundary while allowing one WorkManager pipeline.
 */
class SupabaseFullCrmRemoteDataSource(
    private val auth: SupabaseAuthClient,
) : RemoteCrmDataSource {
    private val core = SupabaseCrmRemoteDataSource(auth)
    private val commercial = SupabaseCommercialRemoteDataSource(auth)

    override suspend fun apply(operation: SyncOperationEntity): RemoteSyncResult =
        if (operation.entityType in COMMERCIAL_ENTITY_TYPES) {
            commercial.apply(operation)
        } else {
            core.apply(operation)
        }

    override suspend fun pullInto(database: LanuCrmDatabase): RemotePullResult {
        return when (val coreResult = core.pullInto(database)) {
            RemotePullResult.Success -> commercial.pullInto(database)
            RemotePullResult.NotConfigured -> RemotePullResult.NotConfigured
            is RemotePullResult.RetryableFailure -> coreResult
        }
    }

    private companion object {
        val COMMERCIAL_ENTITY_TYPES = setOf(
            CommercialCrmSync.ENTITY_CONTACT,
            CommercialCrmSync.ENTITY_QUOTE,
            CommercialCrmSync.ENTITY_QUOTE_LINE,
            CommercialCrmSync.ENTITY_ORDER,
            CommercialCrmSync.ENTITY_ORDER_LINE,
        )
    }
}

class SupabaseCommercialRemoteDataSource(
    private val auth: SupabaseAuthClient,
) : RemoteCrmDataSource {
    override suspend fun apply(operation: SyncOperationEntity): RemoteSyncResult {
        val session = auth.ensureSession() ?: return RemoteSyncResult.NotConfigured
        return runCatching {
            val payload = JSONObject(operation.payloadJson)
            val table = tableFor(operation.entityType)
            val remoteVersion = fetchRemoteVersion(table, payload.getString("id"), session)
            if (remoteVersion != null && remoteVersion > operation.payloadVersion) {
                return RemoteSyncResult.Conflict(
                    "Uzak kayıt sürümü daha yeni: remote=$remoteVersion local=${operation.payloadVersion}",
                )
            }
            val row = rowFor(operation.entityType, payload)
            auth.rawRequest(
                "POST",
                "/rest/v1/$table?on_conflict=id",
                JSONArray().put(row).toString(),
                session.accessToken,
            )
            RemoteSyncResult.Success
        }.getOrElse { error -> mapFailure(error) }
    }

    override suspend fun pullInto(database: LanuCrmDatabase): RemotePullResult {
        val session = auth.ensureSession() ?: return RemotePullResult.NotConfigured
        return runCatching {
            // These child tables intentionally do not duplicate owner_user_id. Their production RLS
            // walks the parent customer relationship, so an unfiltered Data API read is still owner-
            // scoped server-side. This also prevents client-side ownership filters from drifting.
            val contacts = fetchRlsScoped("lanu_crm_contacts", "updated_at_epoch_ms", session)
            val quotes = fetchRlsScoped("lanu_crm_quotes", "updated_at_epoch_ms", session)
            val quoteLines = fetchRlsScoped("lanu_crm_quote_lines", "updated_at_epoch_ms", session)
            val orders = fetchRlsScoped("lanu_crm_orders", "updated_at_epoch_ms", session)
            val orderLines = fetchRlsScoped("lanu_crm_order_lines", "updated_at_epoch_ms", session)

            database.withTransaction {
                contacts.forEach { p ->
                    val id = p.getString("id")
                    val version = p.optLong("version", 1L)
                    val local = database.contactDao().findById(id)
                    if (acceptRemote(local?.version, local?.syncState, version)) {
                        database.contactDao().upsert(
                            CrmContactEntity(
                                id = id,
                                customerId = p.getString("customer_id"),
                                fullName = p.getString("full_name"),
                                role = p.nullableString("role"),
                                phone = p.nullableString("phone"),
                                email = p.nullableString("email"),
                                isPrimary = p.optBoolean("is_primary", false),
                                createdAtEpochMs = p.getLong("created_at_epoch_ms"),
                                updatedAtEpochMs = p.getLong("updated_at_epoch_ms"),
                                version = version,
                                syncState = SyncState.SYNCED.name,
                            ),
                        )
                    }
                }

                quotes.forEach { p ->
                    val id = p.getString("id")
                    val version = p.optLong("version", 1L)
                    val local = database.quoteDao().findById(id)
                    if (acceptRemote(local?.version, local?.syncState, version)) {
                        database.quoteDao().upsert(
                            CrmQuoteEntity(
                                id = id,
                                customerId = p.getString("customer_id"),
                                opportunityId = p.nullableString("opportunity_id"),
                                quoteNumber = p.getString("quote_number"),
                                status = p.getString("status"),
                                currency = p.getString("currency"),
                                totalMinor = p.getLong("total_minor"),
                                validUntilEpochMs = p.nullableLong("valid_until_epoch_ms"),
                                notes = p.nullableString("notes"),
                                createdAtEpochMs = p.getLong("created_at_epoch_ms"),
                                updatedAtEpochMs = p.getLong("updated_at_epoch_ms"),
                                version = version,
                                syncState = SyncState.SYNCED.name,
                            ),
                        )
                    }
                }

                quoteLines.forEach { p ->
                    val id = p.getString("id")
                    val version = p.optLong("version", 1L)
                    val local = database.quoteLineDao().findById(id)
                    if (acceptRemote(local?.version, local?.syncState, version)) {
                        database.quoteLineDao().upsert(
                            CrmQuoteLineEntity(
                                id = id,
                                quoteId = p.getString("quote_id"),
                                productId = p.nullableString("product_id"),
                                productName = p.getString("product_name"),
                                unit = p.getString("unit"),
                                quantityMilli = p.getLong("quantity_milli"),
                                unitPriceMinor = p.getLong("unit_price_minor"),
                                discountBasisPoints = p.getInt("discount_basis_points"),
                                lineTotalMinor = p.getLong("line_total_minor"),
                                createdAtEpochMs = p.getLong("created_at_epoch_ms"),
                                updatedAtEpochMs = p.getLong("updated_at_epoch_ms"),
                                version = version,
                                syncState = SyncState.SYNCED.name,
                            ),
                        )
                    }
                }

                orders.forEach { p ->
                    val id = p.getString("id")
                    val version = p.optLong("version", 1L)
                    val local = database.orderDao().findById(id)
                    if (acceptRemote(local?.version, local?.syncState, version)) {
                        database.orderDao().upsert(
                            CrmOrderEntity(
                                id = id,
                                customerId = p.getString("customer_id"),
                                quoteId = p.nullableString("quote_id"),
                                orderNumber = p.getString("order_number"),
                                status = p.getString("status"),
                                currency = p.getString("currency"),
                                totalMinor = p.getLong("total_minor"),
                                notes = p.nullableString("notes"),
                                createdAtEpochMs = p.getLong("created_at_epoch_ms"),
                                updatedAtEpochMs = p.getLong("updated_at_epoch_ms"),
                                version = version,
                                syncState = SyncState.SYNCED.name,
                            ),
                        )
                    }
                }

                orderLines.forEach { p ->
                    val id = p.getString("id")
                    val version = p.optLong("version", 1L)
                    val local = database.orderLineDao().findById(id)
                    if (acceptRemote(local?.version, local?.syncState, version)) {
                        database.orderLineDao().upsert(
                            CrmOrderLineEntity(
                                id = id,
                                orderId = p.getString("order_id"),
                                productId = p.nullableString("product_id"),
                                productName = p.getString("product_name"),
                                unit = p.getString("unit"),
                                quantityMilli = p.getLong("quantity_milli"),
                                unitPriceMinor = p.getLong("unit_price_minor"),
                                discountBasisPoints = p.getInt("discount_basis_points"),
                                lineTotalMinor = p.getLong("line_total_minor"),
                                createdAtEpochMs = p.getLong("created_at_epoch_ms"),
                                updatedAtEpochMs = p.getLong("updated_at_epoch_ms"),
                                version = version,
                                syncState = SyncState.SYNCED.name,
                            ),
                        )
                    }
                }
            }
            RemotePullResult.Success
        }.getOrElse { error ->
            RemotePullResult.RetryableFailure(error.message ?: "Ticari CRM verisi alınamadı.")
        }
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

    private fun fetchRemoteVersion(table: String, id: String, session: SupabaseSession): Long? {
        val text = auth.rawRequest(
            "GET",
            "/rest/v1/$table?select=version&id=eq.$id&limit=1",
            null,
            session.accessToken,
        )
        val array = JSONArray(text)
        if (array.length() == 0) return null
        return array.getJSONObject(0).optLong("version", 1L)
    }

    private fun fetchRlsScoped(
        table: String,
        orderColumn: String,
        session: SupabaseSession,
    ): List<JSONObject> {
        val result = mutableListOf<JSONObject>()
        var offset = 0
        val pageSize = 500
        while (true) {
            val text = auth.rawRequest(
                "GET",
                "/rest/v1/$table?select=*&order=$orderColumn.asc&limit=$pageSize&offset=$offset",
                null,
                session.accessToken,
            )
            val page = JSONArray(text)
            for (index in 0 until page.length()) result += page.getJSONObject(index)
            if (page.length() < pageSize) return result
            offset += pageSize
        }
    }

    private fun acceptRemote(localVersion: Long?, localSyncState: String?, remoteVersion: Long): Boolean =
        CommercialPullConflictPolicy.acceptRemote(localVersion, localSyncState, remoteVersion)

    private fun mapFailure(error: Throwable): RemoteSyncResult = when (error) {
        is SupabaseHttpException -> when {
            error.code == 401 -> RemoteSyncResult.RetryableFailure("Oturum süresi doldu.")
            error.code == 409 || error.code == 412 -> RemoteSyncResult.Conflict(error.message)
            error.code in 408..599 -> RemoteSyncResult.RetryableFailure(error.message)
            else -> RemoteSyncResult.PermanentFailure(error.message)
        }
        else -> RemoteSyncResult.RetryableFailure(error.message ?: "Bilinmeyen ağ hatası")
    }

    private fun JSONObject.nullableString(key: String): String? =
        if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() }

    private fun JSONObject.nullableLong(key: String): Long? =
        if (isNull(key)) null else optLong(key)

    private fun JSONObject.putNullable(targetKey: String, source: JSONObject, sourceKey: String) {
        if (source.isNull(sourceKey)) put(targetKey, JSONObject.NULL) else put(targetKey, source.get(sourceKey))
    }
}

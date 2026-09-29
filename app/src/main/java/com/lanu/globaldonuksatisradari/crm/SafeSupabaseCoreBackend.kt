package com.lanu.globaldonuksatisradari.crm

import androidx.room.withTransaction
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal

/**
 * Production remote used by WorkManager while the legacy core pull implementation is retained for
 * compatibility. Pushes delegate to the existing proven adapters; pulls use the shared dirty-state
 * conflict policy so offline edits cannot be silently overwritten before their queued mutation is
 * compared with the server version.
 */
class SafeSupabaseFullCrmRemoteDataSource(
    private val auth: SupabaseAuthClient,
) : RemoteCrmDataSource {
    private val corePush = SupabaseCrmRemoteDataSource(auth)
    private val commercial = SupabaseCommercialRemoteDataSource(auth)

    override suspend fun apply(operation: SyncOperationEntity): RemoteSyncResult =
        if (operation.entityType in COMMERCIAL_ENTITY_TYPES) {
            commercial.apply(operation)
        } else {
            corePush.apply(operation)
        }

    override suspend fun pullInto(database: LanuCrmDatabase): RemotePullResult {
        return when (val coreResult = pullCoreInto(database)) {
            RemotePullResult.Success -> commercial.pullInto(database)
            RemotePullResult.NotConfigured -> RemotePullResult.NotConfigured
            is RemotePullResult.RetryableFailure -> coreResult
        }
    }

    private suspend fun pullCoreInto(database: LanuCrmDatabase): RemotePullResult {
        val session = auth.ensureSession() ?: return RemotePullResult.NotConfigured
        return runCatching {
            val customers = fetchAll("lanu_crm_customers", "updated_at", session)
            val activities = fetchAll("lanu_crm_activities", "created_at", session)
            val nextActions = fetchAll("lanu_crm_next_actions", "updated_at", session)
            val opportunities = fetchAll("lanu_crm_opportunities", "updated_at", session)
            val transitions = fetchAll("lanu_crm_stage_transitions", "changed_at", session)

            database.withTransaction {
                customers.forEach { p ->
                    val id = p.getString("id")
                    val remoteVersion = p.optLong("sync_version", 1L)
                    val local = database.customerDao().findById(id)
                    if (CrmPullConflictPolicy.acceptRemote(local?.version, local?.syncState, remoteVersion)) {
                        database.customerDao().upsert(
                            CrmCustomerEntity(
                                id = id,
                                businessSourceId = p.optString("source_id"),
                                businessName = p.optString("name"),
                                city = p.optString("city"),
                                district = p.optString("district"),
                                neighborhood = p.optString("neighborhood").takeIf(String::isNotBlank),
                                address = p.optString("address").takeIf(String::isNotBlank),
                                latitude = p.optDouble("latitude").takeIf { !p.isNull("latitude") },
                                longitude = p.optDouble("longitude").takeIf { !p.isNull("longitude") },
                                dataQuality = runCatching {
                                    DataQuality.valueOf(p.optString("data_quality", "UNKNOWN"))
                                }.getOrDefault(DataQuality.UNKNOWN).name,
                                stage = p.optString("stage", CrmStage.PROSPECT.name),
                                ownerUserId = session.userId,
                                notes = p.optString("notes").takeIf(String::isNotBlank),
                                createdAtEpochMs = parseInstant(p.optString("created_at")),
                                updatedAtEpochMs = parseInstant(p.optString("updated_at")),
                                version = remoteVersion,
                                syncState = SyncState.SYNCED.name,
                            ),
                        )
                    }
                }

                activities.forEach { p ->
                    val id = p.getString("id")
                    val local = database.activityDao().findById(id)
                    if (local == null || local.syncState == SyncState.SYNCED.name) {
                        database.activityDao().upsert(
                            CrmActivityEntity(
                                id = id,
                                customerId = p.getString("customer_id"),
                                type = p.getString("type"),
                                occurredAtEpochMs = parseInstant(p.optString("occurred_at")),
                                note = p.optString("note").takeIf(String::isNotBlank),
                                createdByUserId = p.optString("owner_user_id").takeIf(String::isNotBlank),
                                createdAtEpochMs = parseInstant(p.optString("created_at")),
                                version = 1L,
                                syncState = SyncState.SYNCED.name,
                            ),
                        )
                    }
                }

                nextActions.forEach { p ->
                    val id = p.getString("id")
                    val remoteVersion = p.optLong("version", 1L)
                    val local = database.nextActionDao().findById(id)
                    if (CrmPullConflictPolicy.acceptRemote(local?.version, local?.syncState, remoteVersion)) {
                        database.nextActionDao().upsert(
                            CrmNextActionEntity(
                                id = id,
                                customerId = p.getString("customer_id"),
                                type = p.getString("type"),
                                dueAtEpochMs = parseInstant(p.optString("due_at")),
                                note = p.optString("note").takeIf(String::isNotBlank),
                                createdByUserId = p.optString("created_by_user_id").takeIf(String::isNotBlank),
                                createdAtEpochMs = parseInstant(p.optString("created_at")),
                                completedAtEpochMs = p.optString("completed_at")
                                    .takeIf(String::isNotBlank)
                                    ?.let(::parseInstant),
                                completedByUserId = p.optString("completed_by_user_id")
                                    .takeIf(String::isNotBlank),
                                version = remoteVersion,
                                syncState = SyncState.SYNCED.name,
                            ),
                        )
                    }
                }

                opportunities.forEach { p ->
                    val id = p.getString("id")
                    val local = database.opportunityDao().findById(id)
                    val remoteVersion = p.optLong("version", 1L)
                    if (CrmPullConflictPolicy.acceptRemote(local?.version, local?.syncState, remoteVersion)) {
                        val amountMinor = p.optString("amount").takeIf(String::isNotBlank)?.let {
                            runCatching { BigDecimal(it).movePointRight(2).longValueExact() }.getOrNull()
                        }
                        database.opportunityDao().upsert(
                            CrmOpportunityEntity(
                                id = id,
                                customerId = p.getString("customer_id"),
                                title = p.optString("title"),
                                status = p.optString("status", CrmOpportunityStatus.OPEN.name),
                                notes = p.optString("note").takeIf(String::isNotBlank),
                                estimatedValueMinor = amountMinor,
                                currency = p.optString("currency").takeIf(String::isNotBlank),
                                valueOrigin = p.optString("amount_origin", CrmValueOrigin.UNKNOWN.name),
                                createdAtEpochMs = parseInstant(p.optString("created_at")),
                                updatedAtEpochMs = parseInstant(p.optString("updated_at")),
                                version = remoteVersion,
                                syncState = SyncState.SYNCED.name,
                            ),
                        )
                    }
                }

                transitions.forEach { p ->
                    database.stageTransitionDao().insert(
                        CrmStageTransitionEntity(
                            id = p.getString("id"),
                            customerId = p.getString("customer_id"),
                            fromStage = p.optString("from_stage").takeIf(String::isNotBlank),
                            toStage = p.optString("to_stage"),
                            changedAtEpochMs = parseInstant(p.optString("changed_at")),
                            changedByUserId = p.optString("changed_by_user_id").takeIf(String::isNotBlank),
                            clientVersion = p.optLong("client_version", 1L),
                        ),
                    )
                }
            }
            RemotePullResult.Success
        }.getOrElse { error ->
            RemotePullResult.RetryableFailure(error.message ?: "Uzak CRM verisi alınamadı.")
        }
    }

    private fun fetchAll(
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
                "/rest/v1/$table?select=*&owner_user_id=eq.${session.userId}" +
                    "&order=$orderColumn.asc&limit=$pageSize&offset=$offset",
                null,
                session.accessToken,
            )
            val page = JSONArray(text)
            for (index in 0 until page.length()) result += page.getJSONObject(index)
            if (page.length() < pageSize) return result
            offset += pageSize
        }
    }

    private fun parseInstant(value: String): Long =
        runCatching { java.time.Instant.parse(value).toEpochMilli() }
            .getOrDefault(System.currentTimeMillis())

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

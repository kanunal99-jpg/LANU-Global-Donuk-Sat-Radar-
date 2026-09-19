package com.lanu.globaldonuksatisradari.crm

import androidx.room.withTransaction
import com.lanu.globaldonuksatisradari.data.VerifiedBusiness
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONObject
import java.util.UUID

class LocalCrmRepository(
    private val database: LanuCrmDatabase,
    private val now: () -> Long = { System.currentTimeMillis() },
    private val idGenerator: () -> String = { UUID.randomUUID().toString() },
) {
    fun observeCustomers(city: String? = null): Flow<List<CrmCustomer>> {
        val source = city?.let(database.customerDao()::observeByCity) ?: database.customerDao().observeAll()
        return source.map { entities -> entities.map(CrmMappings::toDomain) }
    }

    fun observeActivities(customerId: String): Flow<List<CrmActivity>> =
        database.activityDao().observeForCustomer(customerId).map { it.map(CrmMappings::toDomain) }

    fun observeActivitiesForRegion(
        city: String,
        district: String?,
    ): Flow<List<CrmActivity>> =
        database.activityDao()
            .observeForRegion(city, district)
            .map { it.map(CrmMappings::toDomain) }

    fun observeStageTransitions(customerId: String): Flow<List<CrmStageTransition>> =
        database.stageTransitionDao()
            .observeForCustomer(customerId)
            .map { it.map(CrmMappings::toDomain) }

    suspend fun updateCustomerNotes(
        customerId: String,
        notes: String?,
    ): CrmCustomer {
        val current = database.customerDao().findById(customerId)
            ?: error("CRM müşterisi bulunamadı: $customerId")
        val timestamp = now()
        val updated = current.copy(
            notes = notes?.trim()?.takeIf { it.isNotEmpty() },
            updatedAtEpochMs = timestamp,
            version = current.version + 1L,
            syncState = SyncState.PENDING_UPLOAD.name,
        )
        database.withTransaction {
            check(
                database.customerDao().updateNotes(
                    id = updated.id,
                    notes = updated.notes,
                    updatedAtEpochMs = updated.updatedAtEpochMs,
                    version = updated.version,
                    state = updated.syncState,
                ) == 1,
            ) { "CRM notu güncellenemedi: $customerId" }
            database.syncOperationDao().insert(
                SyncOperationEntity(
                    id = idGenerator(),
                    entityType = ENTITY_CUSTOMER,
                    entityId = updated.id,
                    operation = OP_UPDATE,
                    payloadVersion = updated.version,
                    payloadJson = CrmPayloads.customer(CrmMappings.toDomain(updated)),
                    createdAtEpochMs = timestamp,
                    attemptCount = 0,
                    lastError = null,
                ),
            )
        }
        return CrmMappings.toDomain(updated)
    }

    suspend fun addBusinessAsCustomer(
        business: VerifiedBusiness,
        ownerUserId: String? = null,
    ): CrmCustomer = database.withTransaction {
        val existing = database.customerDao().findByBusinessSourceId(business.id)
        if (existing != null) return@withTransaction CrmMappings.toDomain(existing)

        val timestamp = now()
        val customer = CrmCustomer(
            id = idGenerator(),
            businessSourceId = business.id,
            businessName = business.name,
            city = business.city,
            district = business.district,
            neighborhood = business.neighborhood,
            stage = CrmStage.PROSPECT,
            ownerUserId = ownerUserId,
            createdAtEpochMs = timestamp,
            updatedAtEpochMs = timestamp,
            version = 1L,
            syncState = SyncState.PENDING_UPLOAD,
        )

        database.customerDao().upsert(CrmMappings.toEntity(customer))
        database.stageTransitionDao().insert(
            CrmStageTransitionEntity(
                id = idGenerator(),
                customerId = customer.id,
                fromStage = null,
                toStage = CrmStage.PROSPECT.name,
                changedAtEpochMs = timestamp,
                changedByUserId = ownerUserId,
                clientVersion = customer.version,
            ),
        )
        database.syncOperationDao().insert(
            SyncOperationEntity(
                id = idGenerator(),
                entityType = ENTITY_CUSTOMER,
                entityId = customer.id,
                operation = OP_CREATE,
                payloadVersion = customer.version,
                payloadJson = CrmPayloads.customer(customer),
                createdAtEpochMs = timestamp,
                attemptCount = 0,
                lastError = null,
            ),
        )
        customer
    }

    suspend fun transitionStage(
        customerId: String,
        to: CrmStage,
        changedByUserId: String? = null,
        note: String? = null,
    ): CrmCustomer {
        val current = database.customerDao().findById(customerId)
            ?: error("CRM müşterisi bulunamadı: $customerId")
        val from = CrmStage.valueOf(current.stage)
        require(CrmStageRules.canTransition(from, to)) {
            "Geçersiz CRM aşama geçişi: ${from.name} → ${to.name}"
        }

        val timestamp = now()
        val updated = current.copy(
            stage = to.name,
            updatedAtEpochMs = timestamp,
            version = current.version + 1L,
            syncState = SyncState.PENDING_UPLOAD.name,
            notes = note ?: current.notes,
        )
        database.withTransaction {
            database.customerDao().upsert(updated)
            database.stageTransitionDao().insert(
                CrmStageTransitionEntity(
                    id = idGenerator(),
                    customerId = customerId,
                    fromStage = from.name,
                    toStage = to.name,
                    changedAtEpochMs = timestamp,
                    changedByUserId = changedByUserId,
                    clientVersion = updated.version,
                ),
            )
            database.syncOperationDao().insert(
                SyncOperationEntity(
                    id = idGenerator(),
                    entityType = ENTITY_CUSTOMER,
                    entityId = updated.id,
                    operation = OP_UPDATE,
                    payloadVersion = updated.version,
                    payloadJson = CrmPayloads.customer(CrmMappings.toDomain(updated)),
                    createdAtEpochMs = timestamp,
                    attemptCount = 0,
                    lastError = null,
                ),
            )
        }
        return CrmMappings.toDomain(updated)
    }

    suspend fun recordActivity(
        customerId: String,
        type: CrmActivityType,
        occurredAtEpochMs: Long = now(),
        note: String? = null,
        createdByUserId: String? = null,
    ): CrmActivity {
        require(database.customerDao().findById(customerId) != null) {
            "Aktivite için CRM müşterisi bulunamadı: $customerId"
        }
        val timestamp = now()
        val activity = CrmActivity(
            id = idGenerator(),
            customerId = customerId,
            type = type,
            occurredAtEpochMs = occurredAtEpochMs,
            note = note,
            createdByUserId = createdByUserId,
            createdAtEpochMs = timestamp,
            version = 1L,
            syncState = SyncState.PENDING_UPLOAD,
        )

        database.withTransaction {
            database.activityDao().upsert(CrmMappings.toEntity(activity))
            database.syncOperationDao().insert(
                SyncOperationEntity(
                    id = idGenerator(),
                    entityType = ENTITY_ACTIVITY,
                    entityId = activity.id,
                    operation = OP_CREATE,
                    payloadVersion = activity.version,
                    payloadJson = CrmPayloads.activity(activity),
                    createdAtEpochMs = timestamp,
                    attemptCount = 0,
                    lastError = null,
                ),
            )
        }
        return activity
    }

    fun observeNextActions(customerId: String): Flow<List<CrmNextAction>> =
        database.nextActionDao().observeForCustomer(customerId).map { it.map(CrmMappings::toDomain) }

    fun observeOpenNextActions(limit: Int = 100): Flow<List<CrmNextAction>> =
        database.nextActionDao().observeOpen(limit).map { it.map(CrmMappings::toDomain) }

    fun observeOpenNextActionsForRegion(
        city: String,
        district: String?,
    ): Flow<List<CrmNextAction>> =
        database.nextActionDao()
            .observeOpenForRegion(city, district)
            .map { it.map(CrmMappings::toDomain) }

    fun observeDueNextActionsForRegion(
        city: String,
        district: String?,
        nowEpochMs: Long = now(),
    ): Flow<List<CrmNextAction>> =
        database.nextActionDao()
            .observeDueForRegion(city, district, nowEpochMs)
            .map { it.map(CrmMappings::toDomain) }

    fun observeDueNextActions(nowEpochMs: Long = now(), limit: Int = 100): Flow<List<CrmNextAction>> =
        database.nextActionDao().observeDue(nowEpochMs, limit).map { it.map(CrmMappings::toDomain) }

    suspend fun createNextAction(
        customerId: String,
        type: CrmNextActionType,
        dueAtEpochMs: Long,
        note: String? = null,
        createdByUserId: String? = null,
    ): CrmNextAction {
        require(database.customerDao().findById(customerId) != null) {
            "Takip aksiyonu için CRM müşterisi bulunamadı: $customerId"
        }
        require(dueAtEpochMs > 0L) { "Takip zamanı geçerli olmalıdır." }

        val timestamp = now()
        val action = CrmNextAction(
            id = idGenerator(),
            customerId = customerId,
            type = type,
            dueAtEpochMs = dueAtEpochMs,
            note = note,
            createdByUserId = createdByUserId,
            createdAtEpochMs = timestamp,
            syncState = SyncState.PENDING_UPLOAD,
        )
        database.withTransaction {
            database.nextActionDao().upsert(CrmMappings.toEntity(action))
            database.syncOperationDao().insert(
                SyncOperationEntity(
                    id = idGenerator(),
                    entityType = ENTITY_NEXT_ACTION,
                    entityId = action.id,
                    operation = OP_CREATE,
                    payloadVersion = action.version,
                    payloadJson = CrmPayloads.nextAction(action),
                    createdAtEpochMs = timestamp,
                    attemptCount = 0,
                    lastError = null,
                ),
            )
        }
        return action
    }

    suspend fun completeNextAction(
        actionId: String,
        completedByUserId: String? = null,
    ): CrmNextAction {
        val current = database.nextActionDao().findById(actionId)
            ?: error("Takip aksiyonu bulunamadı: $actionId")
        require(current.completedAtEpochMs == null) { "Takip aksiyonu zaten tamamlandı." }

        val timestamp = now()
        database.withTransaction {
            val updated = database.nextActionDao().complete(
                id = actionId,
                completedAtEpochMs = timestamp,
                completedByUserId = completedByUserId,
                syncState = SyncState.PENDING_UPLOAD.name,
            )
            check(updated == 1) { "Takip aksiyonu tamamlanamadı: $actionId" }
            val latest = database.nextActionDao().findById(actionId)
                ?: error("Tamamlanan takip aksiyonu okunamadı: $actionId")
            val completedAction = CrmMappings.toDomain(latest)
            database.syncOperationDao().insert(
                SyncOperationEntity(
                    id = idGenerator(),
                    entityType = ENTITY_NEXT_ACTION,
                    entityId = actionId,
                    operation = OP_UPDATE,
                    payloadVersion = completedAction.version,
                    payloadJson = CrmPayloads.nextAction(completedAction),
                    createdAtEpochMs = timestamp,
                    attemptCount = 0,
                    lastError = null,
                ),
            )

            val activity = CrmActivity(
                id = idGenerator(),
                customerId = completedAction.customerId,
                type = completedAction.type.toActivityType(),
                occurredAtEpochMs = timestamp,
                note = completedAction.note,
                createdByUserId = completedByUserId,
                createdAtEpochMs = timestamp,
                version = 1L,
                syncState = SyncState.PENDING_UPLOAD,
            )
            database.activityDao().upsert(CrmMappings.toEntity(activity))
            database.syncOperationDao().insert(
                SyncOperationEntity(
                    id = idGenerator(),
                    entityType = ENTITY_ACTIVITY,
                    entityId = activity.id,
                    operation = OP_CREATE,
                    payloadVersion = activity.version,
                    payloadJson = CrmPayloads.activity(activity),
                    createdAtEpochMs = timestamp,
                    attemptCount = 0,
                    lastError = null,
                ),
            )
        }
        return CrmMappings.toDomain(database.nextActionDao().findById(actionId)!!)
    }

    fun observePendingSyncCount(): Flow<Int> =
        database.syncOperationDao().observePendingCount()

    suspend fun pendingSync(limit: Int = 100): List<SyncOperation> =
        database.syncOperationDao().pending(limit).map(CrmMappings::toDomain)

    companion object {
        const val ENTITY_CUSTOMER = "customer"
        const val ENTITY_ACTIVITY = "activity"
        const val ENTITY_NEXT_ACTION = "next_action"
        const val OP_CREATE = "create"
        const val OP_UPDATE = "update"
    }
}

private object CrmMappings {
    fun toEntity(model: CrmCustomer) = CrmCustomerEntity(
        id = model.id,
        businessSourceId = model.businessSourceId,
        businessName = model.businessName,
        city = model.city,
        district = model.district,
        neighborhood = model.neighborhood,
        stage = model.stage.name,
        ownerUserId = model.ownerUserId,
        notes = model.notes,
        createdAtEpochMs = model.createdAtEpochMs,
        updatedAtEpochMs = model.updatedAtEpochMs,
        version = model.version,
        syncState = model.syncState.name,
    )

    fun toDomain(entity: CrmCustomerEntity) = CrmCustomer(
        id = entity.id,
        businessSourceId = entity.businessSourceId,
        businessName = entity.businessName,
        city = entity.city,
        district = entity.district,
        neighborhood = entity.neighborhood,
        stage = CrmStage.valueOf(entity.stage),
        ownerUserId = entity.ownerUserId,
        notes = entity.notes,
        createdAtEpochMs = entity.createdAtEpochMs,
        updatedAtEpochMs = entity.updatedAtEpochMs,
        version = entity.version,
        syncState = SyncState.valueOf(entity.syncState),
    )

    fun toEntity(model: CrmNextAction) = CrmNextActionEntity(
        id = model.id,
        customerId = model.customerId,
        type = model.type.name,
        dueAtEpochMs = model.dueAtEpochMs,
        note = model.note,
        createdByUserId = model.createdByUserId,
        createdAtEpochMs = model.createdAtEpochMs,
        completedAtEpochMs = model.completedAtEpochMs,
        completedByUserId = model.completedByUserId,
        version = model.version,
        syncState = model.syncState.name,
    )

    fun toDomain(entity: CrmNextActionEntity) = CrmNextAction(
        id = entity.id,
        customerId = entity.customerId,
        type = CrmNextActionType.valueOf(entity.type),
        dueAtEpochMs = entity.dueAtEpochMs,
        note = entity.note,
        createdByUserId = entity.createdByUserId,
        createdAtEpochMs = entity.createdAtEpochMs,
        completedAtEpochMs = entity.completedAtEpochMs,
        completedByUserId = entity.completedByUserId,
        version = entity.version,
        syncState = SyncState.valueOf(entity.syncState),
    )

    fun toEntity(model: CrmActivity) = CrmActivityEntity(
        id = model.id,
        customerId = model.customerId,
        type = model.type.name,
        occurredAtEpochMs = model.occurredAtEpochMs,
        note = model.note,
        createdByUserId = model.createdByUserId,
        createdAtEpochMs = model.createdAtEpochMs,
        version = model.version,
        syncState = model.syncState.name,
    )

    fun toDomain(entity: CrmActivityEntity) = CrmActivity(
        id = entity.id,
        customerId = entity.customerId,
        type = CrmActivityType.valueOf(entity.type),
        occurredAtEpochMs = entity.occurredAtEpochMs,
        note = entity.note,
        createdByUserId = entity.createdByUserId,
        createdAtEpochMs = entity.createdAtEpochMs,
        version = entity.version,
        syncState = SyncState.valueOf(entity.syncState),
    )

    fun toDomain(entity: CrmStageTransitionEntity) = CrmStageTransition(
        id = entity.id,
        customerId = entity.customerId,
        from = entity.fromStage?.let(CrmStage::valueOf),
        to = CrmStage.valueOf(entity.toStage),
        changedAtEpochMs = entity.changedAtEpochMs,
        changedByUserId = entity.changedByUserId,
        clientVersion = entity.clientVersion,
    )

    fun toDomain(entity: SyncOperationEntity) = SyncOperation(
        id = entity.id,
        entityType = entity.entityType,
        entityId = entity.entityId,
        operation = entity.operation,
        payloadVersion = entity.payloadVersion,
        createdAtEpochMs = entity.createdAtEpochMs,
        attemptCount = entity.attemptCount,
        lastError = entity.lastError,
    )
}

private fun CrmNextActionType.toActivityType(): CrmActivityType = when (this) {
    CrmNextActionType.CALL -> CrmActivityType.CALL
    CrmNextActionType.VISIT -> CrmActivityType.VISIT
    CrmNextActionType.MEETING -> CrmActivityType.MEETING
    CrmNextActionType.SAMPLE_FOLLOW_UP -> CrmActivityType.SAMPLE
    CrmNextActionType.PROPOSAL_FOLLOW_UP -> CrmActivityType.PROPOSAL
    CrmNextActionType.ORDER_FOLLOW_UP -> CrmActivityType.ORDER
    CrmNextActionType.NOTE -> CrmActivityType.NOTE
}

private object CrmPayloads {
    fun customer(customer: CrmCustomer): String = JSONObject().apply {
        put("id", customer.id)
        put("businessSourceId", customer.businessSourceId)
        put("businessName", customer.businessName)
        put("city", customer.city)
        put("district", customer.district)
        put("neighborhood", customer.neighborhood)
        put("stage", customer.stage.name)
        put("ownerUserId", customer.ownerUserId)
        put("notes", customer.notes)
        put("createdAtEpochMs", customer.createdAtEpochMs)
        put("updatedAtEpochMs", customer.updatedAtEpochMs)
        put("version", customer.version)
    }.toString()

    fun nextAction(action: CrmNextAction): String = JSONObject().apply {
        put("id", action.id)
        put("customerId", action.customerId)
        put("type", action.type.name)
        put("dueAtEpochMs", action.dueAtEpochMs)
        put("note", action.note)
        put("createdByUserId", action.createdByUserId)
        put("createdAtEpochMs", action.createdAtEpochMs)
        put("completedAtEpochMs", action.completedAtEpochMs)
        put("completedByUserId", action.completedByUserId)
        put("version", action.version)
    }.toString()

    fun activity(activity: CrmActivity): String = JSONObject().apply {
        put("id", activity.id)
        put("customerId", activity.customerId)
        put("type", activity.type.name)
        put("occurredAtEpochMs", activity.occurredAtEpochMs)
        put("note", activity.note)
        put("createdByUserId", activity.createdByUserId)
        put("createdAtEpochMs", activity.createdAtEpochMs)
        put("version", activity.version)
    }.toString()
}

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
            address = business.address,
            latitude = business.latitude,
            longitude = business.longitude,
            dataQuality = DataQuality.OBSERVED,
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


    suspend fun addManualCustomerPoint(
        businessName: String,
        address: String,
        city: String,
        district: String,
        neighborhood: String?,
        latitude: Double,
        longitude: Double,
        ownerUserId: String? = null,
    ): CrmCustomer = database.withTransaction {
        require(businessName.trim().isNotEmpty()) { "Nokta adı boş olamaz." }
        require(address.trim().isNotEmpty()) { "Adres boş olamaz." }
        require(city.trim().isNotEmpty()) { "İl boş olamaz." }
        require(district.trim().isNotEmpty()) { "İlçe boş olamaz." }
        require(latitude in -90.0..90.0) { "Enlem (Y) geçersiz." }
        require(longitude in -180.0..180.0) { "Boylam (X) geçersiz." }

        val timestamp = now()
        val id = idGenerator()
        val sourceId = "manual:$id"
        val customer = CrmCustomer(
            id = id,
            businessSourceId = sourceId,
            businessName = businessName.trim(),
            city = city.trim(),
            district = district.trim(),
            neighborhood = neighborhood?.trim()?.takeIf { it.isNotEmpty() },
            address = address.trim(),
            latitude = latitude,
            longitude = longitude,
            dataQuality = DataQuality.USER_ENTERED,
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

    fun observeOpportunities(customerId: String): Flow<List<CrmOpportunity>> =
        database.opportunityDao()
            .observeForCustomer(customerId)
            .map { it.map(CrmMappings::toDomain) }

    fun observeOpportunitiesForRegion(
        city: String,
        district: String?,
    ): Flow<List<CrmOpportunity>> =
        database.opportunityDao()
            .observeForRegion(city, district)
            .map { it.map(CrmMappings::toDomain) }

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

    suspend fun createOpportunity(
        customerId: String,
        title: String,
        notes: String? = null,
        estimatedValueMinor: Long? = null,
        currency: String? = null,
        valueOrigin: CrmValueOrigin = CrmValueOrigin.USER_ENTERED,
        createdByUserId: String? = null,
    ): CrmOpportunity {
        require(database.customerDao().findById(customerId) != null) {
            "Fırsat için CRM müşterisi bulunamadı: $customerId"
        }
        val normalizedTitle = title.trim()
        require(normalizedTitle.isNotEmpty()) { "Fırsat başlığı boş olamaz." }
        require(estimatedValueMinor == null || estimatedValueMinor >= 0L) {
            "Fırsat değeri negatif olamaz."
        }
        val normalizedCurrency = currency?.trim()?.uppercase()?.takeIf { it.isNotEmpty() }
        require(normalizedCurrency == null || normalizedCurrency.length == 3) {
            "Para birimi ISO 4217 biçiminde 3 harf olmalıdır."
        }

        val timestamp = now()
        val opportunity = CrmOpportunity(
            id = idGenerator(),
            customerId = customerId,
            title = normalizedTitle,
            notes = notes?.trim()?.takeIf { it.isNotEmpty() },
            estimatedValueMinor = estimatedValueMinor,
            currency = normalizedCurrency,
            valueOrigin = if (estimatedValueMinor == null) CrmValueOrigin.UNKNOWN else valueOrigin,
            createdAtEpochMs = timestamp,
            updatedAtEpochMs = timestamp,
            syncState = SyncState.PENDING_UPLOAD,
        )

        database.withTransaction {
            database.opportunityDao().upsert(CrmMappings.toEntity(opportunity))
            database.syncOperationDao().insert(
                SyncOperationEntity(
                    id = idGenerator(),
                    entityType = ENTITY_OPPORTUNITY,
                    entityId = opportunity.id,
                    operation = OP_CREATE,
                    payloadVersion = opportunity.version,
                    payloadJson = CrmPayloads.opportunity(opportunity),
                    createdAtEpochMs = timestamp,
                    attemptCount = 0,
                    lastError = null,
                ),
            )
        }
        return opportunity
    }

    suspend fun transitionOpportunity(
        opportunityId: String,
        status: CrmOpportunityStatus,
    ): CrmOpportunity {
        val current = database.opportunityDao().findById(opportunityId)
            ?: error("Satış fırsatı bulunamadı: $opportunityId")
        val timestamp = now()
        return database.withTransaction {
            check(
                database.opportunityDao().updateStatus(
                    id = opportunityId,
                    status = status.name,
                    updatedAtEpochMs = timestamp,
                    syncState = SyncState.PENDING_UPLOAD.name,
                ) == 1,
            ) { "Satış fırsatı güncellenemedi: $opportunityId" }
            val latest = database.opportunityDao().findById(opportunityId)
                ?: error("Güncel satış fırsatı okunamadı: $opportunityId")
            val updated = CrmMappings.toDomain(latest)
            database.syncOperationDao().insert(
                SyncOperationEntity(
                    id = idGenerator(),
                    entityType = ENTITY_OPPORTUNITY,
                    entityId = updated.id,
                    operation = OP_UPDATE,
                    payloadVersion = updated.version,
                    payloadJson = CrmPayloads.opportunity(updated),
                    createdAtEpochMs = timestamp,
                    attemptCount = 0,
                    lastError = null,
                ),
            )
            return@withTransaction updated
        }
    }

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
        const val ENTITY_OPPORTUNITY = "opportunity"
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
        address = model.address,
        latitude = model.latitude,
        longitude = model.longitude,
        dataQuality = model.dataQuality.name,
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
        address = entity.address,
        latitude = entity.latitude,
        longitude = entity.longitude,
        dataQuality = runCatching { DataQuality.valueOf(entity.dataQuality) }.getOrDefault(DataQuality.UNKNOWN),
        stage = CrmStage.valueOf(entity.stage),
        ownerUserId = entity.ownerUserId,
        notes = entity.notes,
        createdAtEpochMs = entity.createdAtEpochMs,
        updatedAtEpochMs = entity.updatedAtEpochMs,
        version = entity.version,
        syncState = SyncState.valueOf(entity.syncState),
    )

    fun toEntity(model: CrmOpportunity) = CrmOpportunityEntity(
        id = model.id,
        customerId = model.customerId,
        title = model.title,
        status = model.status.name,
        notes = model.notes,
        estimatedValueMinor = model.estimatedValueMinor,
        currency = model.currency,
        valueOrigin = model.valueOrigin.name,
        createdAtEpochMs = model.createdAtEpochMs,
        updatedAtEpochMs = model.updatedAtEpochMs,
        version = model.version,
        syncState = model.syncState.name,
    )

    fun toDomain(entity: CrmOpportunityEntity) = CrmOpportunity(
        id = entity.id,
        customerId = entity.customerId,
        title = entity.title,
        status = CrmOpportunityStatus.valueOf(entity.status),
        notes = entity.notes,
        estimatedValueMinor = entity.estimatedValueMinor,
        currency = entity.currency,
        valueOrigin = CrmValueOrigin.valueOf(entity.valueOrigin),
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
        put("address", customer.address)
        customer.latitude?.let { put("latitude", it) } ?: put("latitude", JSONObject.NULL)
        customer.longitude?.let { put("longitude", it) } ?: put("longitude", JSONObject.NULL)
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

    fun opportunity(opportunity: CrmOpportunity): String = JSONObject().apply {
        put("id", opportunity.id)
        put("customerId", opportunity.customerId)
        put("title", opportunity.title)
        put("status", opportunity.status.name)
        put("notes", opportunity.notes)
        put("estimatedValueMinor", opportunity.estimatedValueMinor)
        put("currency", opportunity.currency)
        put("valueOrigin", opportunity.valueOrigin.name)
        put("createdAtEpochMs", opportunity.createdAtEpochMs)
        put("updatedAtEpochMs", opportunity.updatedAtEpochMs)
        put("version", opportunity.version)
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

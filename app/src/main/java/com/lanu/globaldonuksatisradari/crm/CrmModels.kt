package com.lanu.globaldonuksatisradari.crm

/** CRM pipeline is explicit so dashboard values can be derived from persisted state later. */
enum class CrmStage {
    PROSPECT,
    VISIT,
    MEETING,
    PROPOSAL,
    SAMPLE,
    ORDER,
    ACTIVE_CUSTOMER,
    LOST,
}

enum class CrmActivityType {
    VISIT,
    CALL,
    MEETING,
    SAMPLE,
    PROPOSAL,
    ORDER,
    NOTE,
}

enum class SyncState {
    LOCAL_ONLY,
    PENDING_UPLOAD,
    SYNCED,
    CONFLICT,
    FAILED,
}

data class CrmCustomer(
    val id: String,
    val businessSourceId: String,
    val businessName: String,
    val city: String,
    val district: String,
    val neighborhood: String?,
    val stage: CrmStage = CrmStage.PROSPECT,
    val ownerUserId: String? = null,
    val notes: String? = null,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
    val version: Long = 0L,
    val syncState: SyncState = SyncState.LOCAL_ONLY,
)

data class CrmActivity(
    val id: String,
    val customerId: String,
    val type: CrmActivityType,
    val occurredAtEpochMs: Long,
    val note: String? = null,
    val createdByUserId: String? = null,
    val createdAtEpochMs: Long,
    val version: Long = 0L,
    val syncState: SyncState = SyncState.LOCAL_ONLY,
)

data class CrmStageTransition(
    val id: String,
    val customerId: String,
    val from: CrmStage?,
    val to: CrmStage,
    val changedAtEpochMs: Long,
    val changedByUserId: String? = null,
    val clientVersion: Long,
)

data class SyncOperation(
    val id: String,
    val entityType: String,
    val entityId: String,
    val operation: String,
    val payloadVersion: Long,
    val createdAtEpochMs: Long,
    val attemptCount: Int = 0,
    val lastError: String? = null,
)

/** Centralized stage-transition rules prevent UI and dashboard from inventing pipeline states. */
object CrmStageRules {
    fun canTransition(from: CrmStage, to: CrmStage): Boolean = when (from) {
        CrmStage.PROSPECT -> to in setOf(CrmStage.VISIT, CrmStage.MEETING, CrmStage.LOST)
        CrmStage.VISIT -> to in setOf(CrmStage.MEETING, CrmStage.PROPOSAL, CrmStage.LOST)
        CrmStage.MEETING -> to in setOf(CrmStage.PROPOSAL, CrmStage.SAMPLE, CrmStage.LOST)
        CrmStage.PROPOSAL -> to in setOf(CrmStage.SAMPLE, CrmStage.ORDER, CrmStage.LOST)
        CrmStage.SAMPLE -> to in setOf(CrmStage.PROPOSAL, CrmStage.ORDER, CrmStage.LOST)
        CrmStage.ORDER -> to in setOf(CrmStage.ACTIVE_CUSTOMER, CrmStage.LOST)
        CrmStage.ACTIVE_CUSTOMER -> to == CrmStage.LOST
        CrmStage.LOST -> to == CrmStage.PROSPECT
    }
}

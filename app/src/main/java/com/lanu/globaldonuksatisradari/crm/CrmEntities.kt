package com.lanu.globaldonuksatisradari.crm

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "crm_customer",
    indices = [
        Index(value = ["businessSourceId"]),
        Index(value = ["city", "district"]),
        Index(value = ["stage"]),
        Index(value = ["updatedAtEpochMs"]),
    ],
)
data class CrmCustomerEntity(
    @PrimaryKey val id: String,
    val businessSourceId: String,
    val businessName: String,
    val city: String,
    val district: String,
    val neighborhood: String?,
    val stage: String,
    val ownerUserId: String?,
    val notes: String?,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
    val version: Long,
    val syncState: String,
)

@Entity(
    tableName = "crm_activity",
    indices = [
        Index(value = ["customerId", "occurredAtEpochMs"]),
        Index(value = ["syncState"]),
    ],
)
data class CrmActivityEntity(
    @PrimaryKey val id: String,
    val customerId: String,
    val type: String,
    val occurredAtEpochMs: Long,
    val note: String?,
    val createdByUserId: String?,
    val createdAtEpochMs: Long,
    val version: Long,
    val syncState: String,
)

@Entity(
    tableName = "crm_next_action",
    indices = [
        Index(value = ["customerId", "dueAtEpochMs"]),
        Index(value = ["dueAtEpochMs", "completedAtEpochMs"]),
        Index(value = ["syncState"]),
    ],
)
data class CrmNextActionEntity(
    @PrimaryKey val id: String,
    val customerId: String,
    val type: String,
    val dueAtEpochMs: Long,
    val note: String?,
    val createdByUserId: String?,
    val createdAtEpochMs: Long,
    val completedAtEpochMs: Long?,
    val completedByUserId: String?,
    val version: Long,
    val syncState: String,
)

@Entity(
    tableName = "crm_stage_transition",
    indices = [
        Index(value = ["customerId", "changedAtEpochMs"]),
    ],
)
data class CrmStageTransitionEntity(
    @PrimaryKey val id: String,
    val customerId: String,
    val fromStage: String?,
    val toStage: String,
    val changedAtEpochMs: Long,
    val changedByUserId: String?,
    val clientVersion: Long,
)

@Entity(
    tableName = "crm_sync_operation",
    indices = [
        Index(value = ["entityType", "entityId"]),
        Index(value = ["createdAtEpochMs"]),
        Index(value = ["state", "createdAtEpochMs"]),
    ],
)
data class SyncOperationEntity(
    @PrimaryKey val id: String,
    val entityType: String,
    val entityId: String,
    val operation: String,
    val payloadVersion: Long,
    val payloadJson: String,
    val createdAtEpochMs: Long,
    val attemptCount: Int,
    val lastError: String?,
    @ColumnInfo(defaultValue = "'PENDING'")
    val state: String = SyncOperationState.PENDING.name,
)

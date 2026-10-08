package com.lanu.globaldonuksatisradari.crm

import org.json.JSONObject
import java.util.UUID

/** A create-only insert or a conditional update; never a blind conflict-merging upsert. */
internal data class CoreCrmWritePlan(
    val method: String,
    val path: String,
    val createOnly: Boolean,
)

internal fun planSafeCustomerWrite(
    operation: SyncOperationEntity,
    row: JSONObject,
): CoreCrmWritePlan {
    require(operation.entityType == LocalCrmRepository.ENTITY_CUSTOMER)
    require(UUID.fromString(operation.entityId).toString() == operation.entityId.lowercase())
    require(operation.payloadVersion >= 1L)
    require(row.getString("id") == operation.entityId)
    require(row.getLong("sync_version") == operation.payloadVersion)

    return if (operation.operation == LocalCrmRepository.OP_CREATE && operation.payloadVersion == 1L) {
        CoreCrmWritePlan(
            method = "POST",
            path = "/rest/v1/lanu_crm_customers?on_conflict=id",
            createOnly = true,
        )
    } else {
        val previousVersion = operation.payloadVersion - 1L
        CoreCrmWritePlan(
            method = "PATCH",
            path = "/rest/v1/lanu_crm_customers?id=eq.${operation.entityId}&sync_version=eq.$previousVersion",
            createOnly = false,
        )
    }
}

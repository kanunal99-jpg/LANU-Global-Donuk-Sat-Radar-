package com.lanu.globaldonuksatisradari.crm

import org.json.JSONObject

/** Cloud entity identifiers shared by repositories, sync engine and Supabase adapter. */
object CommercialCrmSync {
    const val ENTITY_CONTACT = "contact"
    const val ENTITY_QUOTE = "quote"
    const val ENTITY_QUOTE_LINE = "quote_line"
    const val ENTITY_ORDER = "order"
    const val ENTITY_ORDER_LINE = "order_line"

    fun stateFor(ownerUserId: String?): String =
        if (ownerUserId.isNullOrBlank()) SyncState.LOCAL_ONLY.name else SyncState.PENDING_UPLOAD.name

    fun contactPayload(entity: CrmContactEntity): String = JSONObject().apply {
        put("id", entity.id)
        put("customerId", entity.customerId)
        put("fullName", entity.fullName)
        put("role", entity.role)
        put("phone", entity.phone)
        put("email", entity.email)
        put("isPrimary", entity.isPrimary)
        put("createdAtEpochMs", entity.createdAtEpochMs)
        put("updatedAtEpochMs", entity.updatedAtEpochMs)
        put("version", entity.version)
    }.toString()

    fun quotePayload(entity: CrmQuoteEntity): String = JSONObject().apply {
        put("id", entity.id)
        put("customerId", entity.customerId)
        put("opportunityId", entity.opportunityId)
        put("quoteNumber", entity.quoteNumber)
        put("status", entity.status)
        put("currency", entity.currency)
        put("totalMinor", entity.totalMinor)
        put("validUntilEpochMs", entity.validUntilEpochMs)
        put("notes", entity.notes)
        put("createdAtEpochMs", entity.createdAtEpochMs)
        put("updatedAtEpochMs", entity.updatedAtEpochMs)
        put("version", entity.version)
    }.toString()

    fun quoteLinePayload(entity: CrmQuoteLineEntity): String = JSONObject().apply {
        put("id", entity.id)
        put("quoteId", entity.quoteId)
        put("productId", entity.productId)
        put("productName", entity.productName)
        put("unit", entity.unit)
        put("quantityMilli", entity.quantityMilli)
        put("unitPriceMinor", entity.unitPriceMinor)
        put("discountBasisPoints", entity.discountBasisPoints)
        put("lineTotalMinor", entity.lineTotalMinor)
        put("createdAtEpochMs", entity.createdAtEpochMs)
        put("updatedAtEpochMs", entity.updatedAtEpochMs)
        put("version", entity.version)
    }.toString()

    fun orderPayload(entity: CrmOrderEntity): String = JSONObject().apply {
        put("id", entity.id)
        put("customerId", entity.customerId)
        put("quoteId", entity.quoteId)
        put("orderNumber", entity.orderNumber)
        put("status", entity.status)
        put("currency", entity.currency)
        put("totalMinor", entity.totalMinor)
        put("notes", entity.notes)
        put("createdAtEpochMs", entity.createdAtEpochMs)
        put("updatedAtEpochMs", entity.updatedAtEpochMs)
        put("version", entity.version)
    }.toString()

    fun orderLinePayload(entity: CrmOrderLineEntity): String = JSONObject().apply {
        put("id", entity.id)
        put("orderId", entity.orderId)
        put("productId", entity.productId)
        put("productName", entity.productName)
        put("unit", entity.unit)
        put("quantityMilli", entity.quantityMilli)
        put("unitPriceMinor", entity.unitPriceMinor)
        put("discountBasisPoints", entity.discountBasisPoints)
        put("lineTotalMinor", entity.lineTotalMinor)
        put("createdAtEpochMs", entity.createdAtEpochMs)
        put("updatedAtEpochMs", entity.updatedAtEpochMs)
        put("version", entity.version)
    }.toString()

    suspend fun enqueue(
        database: LanuCrmDatabase,
        operationId: String,
        entityType: String,
        entityId: String,
        operation: String,
        payloadVersion: Long,
        payloadJson: String,
        createdAtEpochMs: Long,
    ) {
        val previous = database.syncOperationDao().maxCreatedAtEpochMs()
        val nextAfterPrevious = when (previous) {
            null -> createdAtEpochMs
            Long.MAX_VALUE -> Long.MAX_VALUE
            else -> previous + 1L
        }
        val queueTimestamp = maxOf(createdAtEpochMs, nextAfterPrevious)
        database.syncOperationDao().insert(
            SyncOperationEntity(
                id = operationId,
                entityType = entityType,
                entityId = entityId,
                operation = operation,
                payloadVersion = payloadVersion,
                payloadJson = payloadJson,
                createdAtEpochMs = queueTimestamp,
                attemptCount = 0,
                lastError = null,
            ),
        )
    }
}

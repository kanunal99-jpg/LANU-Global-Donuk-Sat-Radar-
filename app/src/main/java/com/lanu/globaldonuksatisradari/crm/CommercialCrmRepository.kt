package com.lanu.globaldonuksatisradari.crm

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.Locale
import java.util.UUID

/**
 * Offline-first commercial document repository.
 *
 * Every mutation is committed locally first. When the parent customer belongs to an authenticated
 * owner, the same Room transaction also records an upload operation. Anonymous/local-only data
 * remains LOCAL_ONLY and is never sent under a later user's identity.
 */
class CommercialCrmRepository(
    private val database: LanuCrmDatabase,
    private val ownerUserId: String? = null,
    private val now: () -> Long = { System.currentTimeMillis() },
    private val idGenerator: () -> String = { UUID.randomUUID().toString() },
) {
    fun observeQuotes(customerId: String): Flow<List<CrmQuote>> =
        database.quoteDao().observeForCustomer(customerId).map { rows -> rows.map(::quoteToDomain) }

    fun observeOrders(customerId: String): Flow<List<CrmOrder>> =
        database.orderDao().observeForCustomer(customerId).map { rows -> rows.map(::orderToDomain) }

    fun observeQuoteLines(quoteId: String): Flow<List<CrmCommercialLine>> =
        database.quoteLineDao().observeForQuote(quoteId).map { rows -> rows.map(::quoteLineToDomain) }

    fun observeOrderLines(orderId: String): Flow<List<CrmCommercialLine>> =
        database.orderLineDao().observeForOrder(orderId).map { rows -> rows.map(::orderLineToDomain) }

    fun observeQuoteLinesForCustomer(customerId: String): Flow<List<CrmCommercialLine>> =
        database.quoteLineDao().observeForCustomer(customerId).map { rows -> rows.map(::quoteLineToDomain) }

    fun observeOrderLinesForCustomer(customerId: String): Flow<List<CrmCommercialLine>> =
        database.orderLineDao().observeForCustomer(customerId).map { rows -> rows.map(::orderLineToDomain) }

    suspend fun createQuote(
        customerId: String,
        opportunityId: String?,
        quoteNumber: String,
        currency: String,
        validUntilEpochMs: Long? = null,
        notes: String? = null,
    ): CrmQuote = database.withTransaction {
        requireOwnedCustomer(customerId)
        val normalizedNumber = quoteNumber.trim()
        require(normalizedNumber.isNotEmpty()) { "Teklif numarası boş olamaz." }
        val normalizedCurrency = currency.trim().uppercase(Locale.ROOT)
        require(normalizedCurrency.length == 3) { "Para birimi ISO 4217 biçiminde 3 harf olmalıdır." }
        require(validUntilEpochMs == null || validUntilEpochMs > 0L) { "Teklif geçerlilik tarihi geçersiz." }

        opportunityId?.let { id ->
            val opportunity = database.opportunityDao().findById(id)
                ?: error("Teklife bağlanacak fırsat bulunamadı: $id")
            require(opportunity.customerId == customerId) {
                "Fırsat farklı bir müşteriye ait olduğu için teklife bağlanamaz."
            }
        }

        val timestamp = now()
        val entity = CrmQuoteEntity(
            id = idGenerator(),
            customerId = customerId,
            opportunityId = opportunityId,
            quoteNumber = normalizedNumber,
            status = CrmQuoteStatus.DRAFT.name,
            currency = normalizedCurrency,
            totalMinor = 0L,
            validUntilEpochMs = validUntilEpochMs,
            notes = notes?.trim()?.takeIf(String::isNotEmpty),
            createdAtEpochMs = timestamp,
            updatedAtEpochMs = timestamp,
            version = 1L,
            syncState = CommercialCrmSync.stateFor(ownerUserId),
        )
        database.quoteDao().upsert(entity)
        enqueueQuoteIfCloudOwned(entity, LocalCrmRepository.OP_CREATE, timestamp)
        quoteToDomain(entity)
    }

    suspend fun transitionQuoteStatus(quoteId: String, target: CrmQuoteStatus): CrmQuote =
        database.withTransaction {
            val current = database.quoteDao().findById(quoteId)
                ?: error("Teklif bulunamadı: $quoteId")
            requireOwnedCustomer(current.customerId)
            val from = CrmQuoteStatus.valueOf(current.status)
            require(CrmCommercialRules.canTransition(from, target)) {
                "Geçersiz teklif durumu: ${from.name} → ${target.name}"
            }
            if (target == CrmQuoteStatus.SENT || target == CrmQuoteStatus.ACCEPTED) {
                require(database.quoteLineDao().listForQuote(quoteId).isNotEmpty()) {
                    "Ürün satırı olmayan teklif gönderilemez veya kabul edilemez."
                }
            }
            val timestamp = now()
            val updated = current.copy(
                status = target.name,
                updatedAtEpochMs = timestamp,
                version = current.version + 1L,
                syncState = CommercialCrmSync.stateFor(ownerUserId),
            )
            database.quoteDao().upsert(updated)
            enqueueQuoteIfCloudOwned(updated, LocalCrmRepository.OP_UPDATE, timestamp)
            quoteToDomain(updated)
        }

    suspend fun addQuoteLine(
        quoteId: String,
        productId: String?,
        productName: String,
        unit: String,
        quantityMilli: Long,
        unitPriceMinor: Long,
        discountBasisPoints: Int = 0,
    ): CrmCommercialLine = database.withTransaction {
        val quote = database.quoteDao().findById(quoteId)
            ?: error("Teklif bulunamadı: $quoteId")
        requireOwnedCustomer(quote.customerId)
        require(CrmQuoteStatus.valueOf(quote.status) == CrmQuoteStatus.DRAFT) {
            "Yalnız taslak teklife ürün satırı eklenebilir."
        }
        CrmCommercialMath.validateSnapshot(productName, unit)
        val lineTotal = CrmCommercialMath.lineTotalMinor(
            quantityMilli = quantityMilli,
            unitPriceMinor = unitPriceMinor,
            discountBasisPoints = discountBasisPoints,
        )
        val timestamp = now()
        val syncState = CommercialCrmSync.stateFor(ownerUserId)
        val line = CrmQuoteLineEntity(
            id = idGenerator(),
            quoteId = quoteId,
            productId = productId?.trim()?.takeIf(String::isNotEmpty),
            productName = productName.trim(),
            unit = unit.trim(),
            quantityMilli = quantityMilli,
            unitPriceMinor = unitPriceMinor,
            discountBasisPoints = discountBasisPoints,
            lineTotalMinor = lineTotal,
            createdAtEpochMs = timestamp,
            updatedAtEpochMs = timestamp,
            version = 1L,
            syncState = syncState,
        )
        database.quoteLineDao().upsert(line)

        val total = database.quoteLineDao().totalForQuote(quoteId)
        val updatedQuote = quote.copy(
            totalMinor = total,
            updatedAtEpochMs = timestamp,
            version = quote.version + 1L,
            syncState = syncState,
        )
        database.quoteDao().upsert(updatedQuote)

        enqueueQuoteLineIfCloudOwned(line, LocalCrmRepository.OP_CREATE, timestamp)
        enqueueQuoteIfCloudOwned(updatedQuote, LocalCrmRepository.OP_UPDATE, timestamp)
        quoteLineToDomain(line)
    }

    suspend fun createOrderFromAcceptedQuote(
        quoteId: String,
        orderNumber: String,
        notes: String? = null,
    ): CrmOrder = database.withTransaction {
        val quote = database.quoteDao().findById(quoteId)
            ?: error("Siparişe dönüştürülecek teklif bulunamadı: $quoteId")
        requireOwnedCustomer(quote.customerId)
        require(CrmQuoteStatus.valueOf(quote.status) == CrmQuoteStatus.ACCEPTED) {
            "Yalnız kabul edilmiş teklif siparişe dönüştürülebilir."
        }
        val quoteLines = database.quoteLineDao().listForQuote(quoteId)
        require(quoteLines.isNotEmpty()) { "Ürün satırı olmayan teklif siparişe dönüştürülemez." }
        val normalizedNumber = orderNumber.trim()
        require(normalizedNumber.isNotEmpty()) { "Sipariş numarası boş olamaz." }

        val timestamp = now()
        val syncState = CommercialCrmSync.stateFor(ownerUserId)
        val orderId = idGenerator()
        val order = CrmOrderEntity(
            id = orderId,
            customerId = quote.customerId,
            quoteId = quote.id,
            orderNumber = normalizedNumber,
            status = CrmOrderStatus.DRAFT.name,
            currency = quote.currency,
            totalMinor = quoteLines.sumOf { it.lineTotalMinor },
            notes = notes?.trim()?.takeIf(String::isNotEmpty),
            createdAtEpochMs = timestamp,
            updatedAtEpochMs = timestamp,
            version = 1L,
            syncState = syncState,
        )
        database.orderDao().upsert(order)
        enqueueOrderIfCloudOwned(order, LocalCrmRepository.OP_CREATE, timestamp)

        quoteLines.forEach { source ->
            val orderLine = CrmOrderLineEntity(
                id = idGenerator(),
                orderId = orderId,
                productId = source.productId,
                productName = source.productName,
                unit = source.unit,
                quantityMilli = source.quantityMilli,
                unitPriceMinor = source.unitPriceMinor,
                discountBasisPoints = source.discountBasisPoints,
                lineTotalMinor = source.lineTotalMinor,
                createdAtEpochMs = timestamp,
                updatedAtEpochMs = timestamp,
                version = 1L,
                syncState = syncState,
            )
            database.orderLineDao().upsert(orderLine)
            enqueueOrderLineIfCloudOwned(orderLine, LocalCrmRepository.OP_CREATE, timestamp)
        }
        orderToDomain(order)
    }

    suspend fun transitionOrderStatus(orderId: String, target: CrmOrderStatus): CrmOrder =
        database.withTransaction {
            val current = database.orderDao().findById(orderId)
                ?: error("Sipariş bulunamadı: $orderId")
            requireOwnedCustomer(current.customerId)
            val from = CrmOrderStatus.valueOf(current.status)
            require(CrmCommercialRules.canTransition(from, target)) {
                "Geçersiz sipariş durumu: ${from.name} → ${target.name}"
            }
            val timestamp = now()
            val updated = current.copy(
                status = target.name,
                updatedAtEpochMs = timestamp,
                version = current.version + 1L,
                syncState = CommercialCrmSync.stateFor(ownerUserId),
            )
            database.orderDao().upsert(updated)
            enqueueOrderIfCloudOwned(updated, LocalCrmRepository.OP_UPDATE, timestamp)
            orderToDomain(updated)
        }

    private suspend fun requireOwnedCustomer(customerId: String): CrmCustomerEntity {
        val customer = database.customerDao().findById(customerId)
            ?: error("Ticari kayıt için CRM müşterisi bulunamadı: $customerId")
        require(customer.ownerUserId == ownerUserId) {
            "CRM müşterisi aktif kullanıcı kapsamına ait değil: $customerId"
        }
        return customer
    }

    private suspend fun enqueueQuoteIfCloudOwned(entity: CrmQuoteEntity, operation: String, timestamp: Long) {
        if (ownerUserId.isNullOrBlank()) return
        CommercialCrmSync.enqueue(
            database, idGenerator(), CommercialCrmSync.ENTITY_QUOTE, entity.id, operation,
            entity.version, CommercialCrmSync.quotePayload(entity), timestamp,
        )
    }

    private suspend fun enqueueQuoteLineIfCloudOwned(entity: CrmQuoteLineEntity, operation: String, timestamp: Long) {
        if (ownerUserId.isNullOrBlank()) return
        CommercialCrmSync.enqueue(
            database, idGenerator(), CommercialCrmSync.ENTITY_QUOTE_LINE, entity.id, operation,
            entity.version, CommercialCrmSync.quoteLinePayload(entity), timestamp,
        )
    }

    private suspend fun enqueueOrderIfCloudOwned(entity: CrmOrderEntity, operation: String, timestamp: Long) {
        if (ownerUserId.isNullOrBlank()) return
        CommercialCrmSync.enqueue(
            database, idGenerator(), CommercialCrmSync.ENTITY_ORDER, entity.id, operation,
            entity.version, CommercialCrmSync.orderPayload(entity), timestamp,
        )
    }

    private suspend fun enqueueOrderLineIfCloudOwned(entity: CrmOrderLineEntity, operation: String, timestamp: Long) {
        if (ownerUserId.isNullOrBlank()) return
        CommercialCrmSync.enqueue(
            database, idGenerator(), CommercialCrmSync.ENTITY_ORDER_LINE, entity.id, operation,
            entity.version, CommercialCrmSync.orderLinePayload(entity), timestamp,
        )
    }

    private fun quoteToDomain(entity: CrmQuoteEntity) = CrmQuote(
        id = entity.id,
        customerId = entity.customerId,
        opportunityId = entity.opportunityId,
        quoteNumber = entity.quoteNumber,
        status = CrmQuoteStatus.valueOf(entity.status),
        currency = entity.currency,
        totalMinor = entity.totalMinor,
        validUntilEpochMs = entity.validUntilEpochMs,
        notes = entity.notes,
        createdAtEpochMs = entity.createdAtEpochMs,
        updatedAtEpochMs = entity.updatedAtEpochMs,
        version = entity.version,
        syncState = SyncState.valueOf(entity.syncState),
    )

    private fun orderToDomain(entity: CrmOrderEntity) = CrmOrder(
        id = entity.id,
        customerId = entity.customerId,
        quoteId = entity.quoteId,
        orderNumber = entity.orderNumber,
        status = CrmOrderStatus.valueOf(entity.status),
        currency = entity.currency,
        totalMinor = entity.totalMinor,
        notes = entity.notes,
        createdAtEpochMs = entity.createdAtEpochMs,
        updatedAtEpochMs = entity.updatedAtEpochMs,
        version = entity.version,
        syncState = SyncState.valueOf(entity.syncState),
    )

    private fun quoteLineToDomain(entity: CrmQuoteLineEntity) = CrmCommercialLine(
        id = entity.id,
        parentId = entity.quoteId,
        productId = entity.productId,
        productName = entity.productName,
        unit = entity.unit,
        quantityMilli = entity.quantityMilli,
        unitPriceMinor = entity.unitPriceMinor,
        discountBasisPoints = entity.discountBasisPoints,
        lineTotalMinor = entity.lineTotalMinor,
        createdAtEpochMs = entity.createdAtEpochMs,
        updatedAtEpochMs = entity.updatedAtEpochMs,
        version = entity.version,
        syncState = SyncState.valueOf(entity.syncState),
    )

    private fun orderLineToDomain(entity: CrmOrderLineEntity) = CrmCommercialLine(
        id = entity.id,
        parentId = entity.orderId,
        productId = entity.productId,
        productName = entity.productName,
        unit = entity.unit,
        quantityMilli = entity.quantityMilli,
        unitPriceMinor = entity.unitPriceMinor,
        discountBasisPoints = entity.discountBasisPoints,
        lineTotalMinor = entity.lineTotalMinor,
        createdAtEpochMs = entity.createdAtEpochMs,
        updatedAtEpochMs = entity.updatedAtEpochMs,
        version = entity.version,
        syncState = SyncState.valueOf(entity.syncState),
    )
}

package com.lanu.globaldonuksatisradari.crm

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.Locale
import java.util.UUID

/**
 * Offline-first commercial document repository.
 *
 * Quote/order documents and their product snapshots are persisted locally first. They remain
 * LOCAL_ONLY until the LANU Supabase schema/RLS for commercial tables is verified; this avoids
 * falsely reporting cloud synchronization for a backend that is not currently validated.
 */
class CommercialCrmRepository(
    private val database: LanuCrmDatabase,
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

    suspend fun createQuote(
        customerId: String,
        opportunityId: String?,
        quoteNumber: String,
        currency: String,
        validUntilEpochMs: Long? = null,
        notes: String? = null,
    ): CrmQuote = database.withTransaction {
        require(database.customerDao().findById(customerId) != null) {
            "Teklif için CRM müşterisi bulunamadı: $customerId"
        }
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
            syncState = SyncState.LOCAL_ONLY.name,
        )
        database.quoteDao().upsert(entity)
        quoteToDomain(entity)
    }

    suspend fun transitionQuoteStatus(quoteId: String, target: CrmQuoteStatus): CrmQuote =
        database.withTransaction {
            val current = database.quoteDao().findById(quoteId)
                ?: error("Teklif bulunamadı: $quoteId")
            val from = CrmQuoteStatus.valueOf(current.status)
            require(CrmCommercialRules.canTransition(from, target)) {
                "Geçersiz teklif durumu: ${from.name} → ${target.name}"
            }
            if (target == CrmQuoteStatus.SENT || target == CrmQuoteStatus.ACCEPTED) {
                require(database.quoteLineDao().listForQuote(quoteId).isNotEmpty()) {
                    "Ürün satırı olmayan teklif gönderilemez veya kabul edilemez."
                }
            }
            val updated = current.copy(
                status = target.name,
                updatedAtEpochMs = now(),
                version = current.version + 1L,
                syncState = SyncState.LOCAL_ONLY.name,
            )
            database.quoteDao().upsert(updated)
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
            syncState = SyncState.LOCAL_ONLY.name,
        )
        database.quoteLineDao().upsert(line)
        val total = database.quoteLineDao().totalForQuote(quoteId)
        database.quoteDao().upsert(
            quote.copy(
                totalMinor = total,
                updatedAtEpochMs = timestamp,
                version = quote.version + 1L,
                syncState = SyncState.LOCAL_ONLY.name,
            ),
        )
        quoteLineToDomain(line)
    }

    suspend fun createOrderFromAcceptedQuote(
        quoteId: String,
        orderNumber: String,
        notes: String? = null,
    ): CrmOrder = database.withTransaction {
        val quote = database.quoteDao().findById(quoteId)
            ?: error("Siparişe dönüştürülecek teklif bulunamadı: $quoteId")
        require(CrmQuoteStatus.valueOf(quote.status) == CrmQuoteStatus.ACCEPTED) {
            "Yalnız kabul edilmiş teklif siparişe dönüştürülebilir."
        }
        val quoteLines = database.quoteLineDao().listForQuote(quoteId)
        require(quoteLines.isNotEmpty()) { "Ürün satırı olmayan teklif siparişe dönüştürülemez." }
        val normalizedNumber = orderNumber.trim()
        require(normalizedNumber.isNotEmpty()) { "Sipariş numarası boş olamaz." }

        val timestamp = now()
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
            syncState = SyncState.LOCAL_ONLY.name,
        )
        database.orderDao().upsert(order)
        quoteLines.forEach { source ->
            database.orderLineDao().upsert(
                CrmOrderLineEntity(
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
                    syncState = SyncState.LOCAL_ONLY.name,
                ),
            )
        }
        orderToDomain(order)
    }

    suspend fun transitionOrderStatus(orderId: String, target: CrmOrderStatus): CrmOrder =
        database.withTransaction {
            val current = database.orderDao().findById(orderId)
                ?: error("Sipariş bulunamadı: $orderId")
            val from = CrmOrderStatus.valueOf(current.status)
            require(CrmCommercialRules.canTransition(from, target)) {
                "Geçersiz sipariş durumu: ${from.name} → ${target.name}"
            }
            val updated = current.copy(
                status = target.name,
                updatedAtEpochMs = now(),
                version = current.version + 1L,
                syncState = SyncState.LOCAL_ONLY.name,
            )
            database.orderDao().upsert(updated)
            orderToDomain(updated)
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

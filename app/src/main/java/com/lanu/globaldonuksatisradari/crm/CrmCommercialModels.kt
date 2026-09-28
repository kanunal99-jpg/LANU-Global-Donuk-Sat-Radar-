package com.lanu.globaldonuksatisradari.crm

import java.math.BigDecimal
import java.math.RoundingMode

enum class CrmQuoteStatus {
    DRAFT,
    SENT,
    ACCEPTED,
    REJECTED,
    EXPIRED,
    CANCELLED,
}

enum class CrmOrderStatus {
    DRAFT,
    CONFIRMED,
    PREPARING,
    DISPATCHED,
    DELIVERED,
    CANCELLED,
}

data class CrmQuote(
    val id: String,
    val customerId: String,
    val opportunityId: String?,
    val quoteNumber: String,
    val status: CrmQuoteStatus,
    val currency: String,
    val totalMinor: Long,
    val validUntilEpochMs: Long?,
    val notes: String?,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
    val version: Long,
    val syncState: SyncState,
)

data class CrmOrder(
    val id: String,
    val customerId: String,
    val quoteId: String?,
    val orderNumber: String,
    val status: CrmOrderStatus,
    val currency: String,
    val totalMinor: Long,
    val notes: String?,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
    val version: Long,
    val syncState: SyncState,
)

/** Immutable transaction-line snapshot used by quote/order flows. */
data class CrmCommercialLine(
    val id: String,
    val parentId: String,
    val productId: String?,
    val productName: String,
    val unit: String,
    val quantityMilli: Long,
    val unitPriceMinor: Long,
    val discountBasisPoints: Int,
    val lineTotalMinor: Long,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
    val version: Long,
    val syncState: SyncState,
)

object CrmCommercialRules {
    fun canTransition(from: CrmQuoteStatus, to: CrmQuoteStatus): Boolean = when (from) {
        CrmQuoteStatus.DRAFT -> to in setOf(CrmQuoteStatus.SENT, CrmQuoteStatus.CANCELLED)
        CrmQuoteStatus.SENT -> to in setOf(
            CrmQuoteStatus.ACCEPTED,
            CrmQuoteStatus.REJECTED,
            CrmQuoteStatus.EXPIRED,
            CrmQuoteStatus.CANCELLED,
        )
        CrmQuoteStatus.ACCEPTED,
        CrmQuoteStatus.REJECTED,
        CrmQuoteStatus.EXPIRED,
        CrmQuoteStatus.CANCELLED,
        -> false
    }

    fun canTransition(from: CrmOrderStatus, to: CrmOrderStatus): Boolean = when (from) {
        CrmOrderStatus.DRAFT -> to in setOf(CrmOrderStatus.CONFIRMED, CrmOrderStatus.CANCELLED)
        CrmOrderStatus.CONFIRMED -> to in setOf(CrmOrderStatus.PREPARING, CrmOrderStatus.CANCELLED)
        CrmOrderStatus.PREPARING -> to in setOf(CrmOrderStatus.DISPATCHED, CrmOrderStatus.CANCELLED)
        CrmOrderStatus.DISPATCHED -> to in setOf(CrmOrderStatus.DELIVERED, CrmOrderStatus.CANCELLED)
        CrmOrderStatus.DELIVERED,
        CrmOrderStatus.CANCELLED,
        -> false
    }
}

object CrmCommercialMath {
    /**
     * Calculates a line total in currency minor units.
     * quantityMilli is fixed-point thousandths and discountBasisPoints is 0..10000.
     */
    fun lineTotalMinor(
        quantityMilli: Long,
        unitPriceMinor: Long,
        discountBasisPoints: Int = 0,
    ): Long {
        require(quantityMilli > 0L) { "Miktar sıfırdan büyük olmalıdır." }
        require(unitPriceMinor >= 0L) { "Birim fiyat negatif olamaz." }
        require(discountBasisPoints in 0..10_000) { "İskonto 0..10000 baz puan aralığında olmalıdır." }

        val quantity = BigDecimal.valueOf(quantityMilli).divide(BigDecimal.valueOf(1_000L))
        val gross = BigDecimal.valueOf(unitPriceMinor).multiply(quantity)
        val discountFactor = BigDecimal.ONE.subtract(
            BigDecimal.valueOf(discountBasisPoints.toLong())
                .divide(BigDecimal.valueOf(10_000L)),
        )
        return gross.multiply(discountFactor)
            .setScale(0, RoundingMode.HALF_UP)
            .longValueExact()
    }

    fun validateSnapshot(productName: String, unit: String) {
        require(productName.trim().isNotEmpty()) { "Ürün adı boş olamaz." }
        require(unit.trim().isNotEmpty()) { "Birim boş olamaz." }
    }
}

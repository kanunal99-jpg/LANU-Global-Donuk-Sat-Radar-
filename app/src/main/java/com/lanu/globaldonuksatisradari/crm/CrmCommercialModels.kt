package com.lanu.globaldonuksatisradari.crm

import java.math.BigDecimal
import java.math.RoundingMode

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

package com.lanu.globaldonuksatisradari.crm

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Commercial line snapshots deliberately store both the local catalog product id and the
 * product/unit/price values used at transaction time. Historical quotes/orders therefore do
 * not silently change when the product catalog is edited later.
 *
 * quantityMilli uses fixed-point thousandths (1.000 unit = 1000) to avoid binary floating
 * point errors in persisted commercial calculations.
 */
@Entity(
    tableName = "crm_quote_line",
    indices = [
        Index(value = ["quoteId", "updatedAtEpochMs"]),
        Index(value = ["productId"]),
        Index(value = ["syncState"]),
    ],
)
data class CrmQuoteLineEntity(
    @PrimaryKey val id: String,
    val quoteId: String,
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
    val syncState: String,
)

@Entity(
    tableName = "crm_order_line",
    indices = [
        Index(value = ["orderId", "updatedAtEpochMs"]),
        Index(value = ["productId"]),
        Index(value = ["syncState"]),
    ],
)
data class CrmOrderLineEntity(
    @PrimaryKey val id: String,
    val orderId: String,
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
    val syncState: String,
)

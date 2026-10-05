package com.lanu.globaldonuksatisradari.crm

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CrmQuoteLineDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(line: CrmQuoteLineEntity)

    @Query("SELECT * FROM crm_quote_line WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): CrmQuoteLineEntity?

    @Query("SELECT * FROM crm_quote_line WHERE quoteId = :quoteId ORDER BY createdAtEpochMs ASC, id ASC")
    fun observeForQuote(quoteId: String): Flow<List<CrmQuoteLineEntity>>

    @Query(
        "SELECT l.* FROM crm_quote_line l " +
            "JOIN crm_quote q ON q.id = l.quoteId " +
            "WHERE q.customerId = :customerId " +
            "ORDER BY l.createdAtEpochMs ASC, l.id ASC",
    )
    fun observeForCustomer(customerId: String): Flow<List<CrmQuoteLineEntity>>

    @Query("SELECT * FROM crm_quote_line WHERE quoteId = :quoteId ORDER BY createdAtEpochMs ASC, id ASC")
    suspend fun listForQuote(quoteId: String): List<CrmQuoteLineEntity>

    @Query("SELECT COALESCE(SUM(lineTotalMinor), 0) FROM crm_quote_line WHERE quoteId = :quoteId")
    suspend fun totalForQuote(quoteId: String): Long

    @Query("DELETE FROM crm_quote_line WHERE quoteId = :quoteId")
    suspend fun deleteForQuote(quoteId: String)

    @Query("UPDATE crm_quote_line SET syncState = :state WHERE id = :id")
    suspend fun updateSyncState(id: String, state: String): Int
}

@Dao
interface CrmOrderLineDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(line: CrmOrderLineEntity)

    @Query("SELECT * FROM crm_order_line WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): CrmOrderLineEntity?

    @Query("SELECT * FROM crm_order_line WHERE orderId = :orderId ORDER BY createdAtEpochMs ASC, id ASC")
    fun observeForOrder(orderId: String): Flow<List<CrmOrderLineEntity>>

    @Query(
        "SELECT l.* FROM crm_order_line l " +
            "JOIN crm_order o ON o.id = l.orderId " +
            "WHERE o.customerId = :customerId " +
            "ORDER BY l.createdAtEpochMs ASC, l.id ASC",
    )
    fun observeForCustomer(customerId: String): Flow<List<CrmOrderLineEntity>>

    @Query("SELECT * FROM crm_order_line WHERE orderId = :orderId ORDER BY createdAtEpochMs ASC, id ASC")
    suspend fun listForOrder(orderId: String): List<CrmOrderLineEntity>

    @Query("SELECT COALESCE(SUM(lineTotalMinor), 0) FROM crm_order_line WHERE orderId = :orderId")
    suspend fun totalForOrder(orderId: String): Long

    @Query("DELETE FROM crm_order_line WHERE orderId = :orderId")
    suspend fun deleteForOrder(orderId: String)

    @Query("UPDATE crm_order_line SET syncState = :state WHERE id = :id")
    suspend fun updateSyncState(id: String, state: String): Int
}

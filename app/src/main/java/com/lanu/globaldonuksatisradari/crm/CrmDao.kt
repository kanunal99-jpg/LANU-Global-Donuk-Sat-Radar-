package com.lanu.globaldonuksatisradari.crm

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CrmCustomerDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(customer: CrmCustomerEntity)

    @Query("SELECT * FROM crm_customer WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): CrmCustomerEntity?

    @Query("SELECT * FROM crm_customer WHERE businessSourceId = :businessSourceId LIMIT 1")
    suspend fun findByBusinessSourceId(businessSourceId: String): CrmCustomerEntity?

    @Query("SELECT * FROM crm_customer WHERE city = :city ORDER BY updatedAtEpochMs DESC")
    fun observeByCity(city: String): Flow<List<CrmCustomerEntity>>

    @Query("SELECT * FROM crm_customer ORDER BY updatedAtEpochMs DESC")
    fun observeAll(): Flow<List<CrmCustomerEntity>>

    @Query("UPDATE crm_customer SET syncState = :state WHERE id = :id")
    suspend fun updateSyncState(id: String, state: String)
}

@Dao
interface CrmActivityDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(activity: CrmActivityEntity)

    @Query("SELECT * FROM crm_activity WHERE customerId = :customerId ORDER BY occurredAtEpochMs DESC")
    suspend fun findForCustomer(customerId: String): List<CrmActivityEntity>

    @Query("SELECT * FROM crm_activity WHERE customerId = :customerId ORDER BY occurredAtEpochMs DESC")
    fun observeForCustomer(customerId: String): Flow<List<CrmActivityEntity>>

    @Query("UPDATE crm_activity SET syncState = :state WHERE id = :id")
    suspend fun updateSyncState(id: String, state: String)
}

@Dao
interface CrmStageTransitionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(transition: CrmStageTransitionEntity)

    @Query("SELECT * FROM crm_stage_transition WHERE customerId = :customerId ORDER BY changedAtEpochMs DESC")
    fun observeForCustomer(customerId: String): Flow<List<CrmStageTransitionEntity>>
}

@Dao
interface SyncOperationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(operation: SyncOperationEntity)

    @Query("SELECT * FROM crm_sync_operation WHERE state = 'PENDING' ORDER BY createdAtEpochMs ASC LIMIT :limit")
    suspend fun pending(limit: Int): List<SyncOperationEntity>

    @Query(
        "UPDATE crm_sync_operation " +
            "SET attemptCount = :attemptCount, lastError = :lastError, state = :state " +
            "WHERE id = :id",
    )
    suspend fun updateAttemptAndState(
        id: String,
        attemptCount: Int,
        lastError: String?,
        state: String,
    )

    @Query("UPDATE crm_sync_operation SET state = :state, lastError = :lastError WHERE id = :id")
    suspend fun updateState(id: String, state: String, lastError: String?)

    @Query("DELETE FROM crm_sync_operation WHERE id = :id")
    suspend fun delete(id: String)
}

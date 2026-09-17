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
}

@Dao
interface CrmActivityDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(activity: CrmActivityEntity)

    @Query("SELECT * FROM crm_activity WHERE customerId = :customerId ORDER BY occurredAtEpochMs DESC")
    suspend fun findForCustomer(customerId: String): List<CrmActivityEntity>

    @Query("SELECT * FROM crm_activity WHERE customerId = :customerId ORDER BY occurredAtEpochMs DESC")
    fun observeForCustomer(customerId: String): Flow<List<CrmActivityEntity>>
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

    @Query("SELECT * FROM crm_sync_operation ORDER BY createdAtEpochMs ASC LIMIT :limit")
    suspend fun pending(limit: Int): List<SyncOperationEntity>

    @Query("UPDATE crm_sync_operation SET attemptCount = :attemptCount, lastError = :lastError WHERE id = :id")
    suspend fun updateAttempt(id: String, attemptCount: Int, lastError: String?)

    @Query("DELETE FROM crm_sync_operation WHERE id = :id")
    suspend fun delete(id: String)
}

package com.lanu.globaldonuksatisradari.crm

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CrmSyncEngineTest {
    private fun operation(
        id: String = "op-1",
        attemptCount: Int = 0,
    ) = SyncOperationEntity(
        id = id,
        entityType = "customer",
        entityId = "customer-1",
        operation = "UPDATE",
        payloadVersion = 1,
        payloadJson = "{}",
        createdAtEpochMs = attemptCount.toLong() + 1L,
        attemptCount = attemptCount,
        lastError = null,
    )

    @Test
    fun retryableFailure_incrementsAttempt_andKeepsOperationPending() = runTest {
        val dao = FakeSyncOperationDao(listOf(operation()))
        val engine = CrmSyncEngine(
            dao,
            object : RemoteCrmDataSource {
                override suspend fun apply(operation: SyncOperationEntity) =
                    RemoteSyncResult.RetryableFailure("network")
            },
        )

        val result = engine.processOne()

        assertEquals(SyncProcessResult.Deferred("op-1", 1, "network"), result)
        assertEquals(1, dao.operation?.attemptCount)
        assertEquals("network", dao.operation?.lastError)
        assertEquals(SyncOperationState.PENDING.name, dao.operation?.state)
    }

    @Test
    fun success_deletesOperation_andIsIdempotentAtQueueBoundary() = runTest {
        val dao = FakeSyncOperationDao(listOf(operation()))
        val engine = CrmSyncEngine(
            dao,
            object : RemoteCrmDataSource {
                override suspend fun apply(operation: SyncOperationEntity) = RemoteSyncResult.Success
            },
        )

        assertEquals(SyncProcessResult.Synced("op-1"), engine.processOne())
        assertEquals(null, dao.operation)
        assertEquals(SyncProcessResult.NoWork, engine.processOne())
    }

    @Test
    fun conflict_parksOperation_andDoesNotBlockFutureQueueRuns() = runTest {
        val dao = FakeSyncOperationDao(listOf(operation()))
        val engine = CrmSyncEngine(
            dao,
            object : RemoteCrmDataSource {
                override suspend fun apply(operation: SyncOperationEntity) =
                    RemoteSyncResult.Conflict("version mismatch")
            },
        )

        assertEquals(SyncProcessResult.Conflict("op-1", "version mismatch"), engine.processOne())
        assertEquals(SyncOperationState.CONFLICT.name, dao.allOperations.single().state)
        assertEquals(SyncProcessResult.NoWork, engine.processOne())
    }

    @Test
    fun retryExhaustion_marksOperationFailed_andRemovesItFromPendingQueue() = runTest {
        val dao = FakeSyncOperationDao(listOf(operation(attemptCount = 4)))
        val engine = CrmSyncEngine(
            dao,
            object : RemoteCrmDataSource {
                override suspend fun apply(operation: SyncOperationEntity) =
                    RemoteSyncResult.RetryableFailure("still offline")
            },
        )

        assertEquals(SyncProcessResult.Failed("op-1", "still offline"), engine.processOne())
        assertEquals(SyncOperationState.FAILED.name, dao.operation?.state)
        assertEquals(SyncProcessResult.NoWork, engine.processOne())
    }

    @Test
    fun batch_processesMultipleSuccessfulOperations() = runTest {
        val dao = FakeSyncOperationDao(listOf(operation("op-1"), operation("op-2")))
        val engine = CrmSyncEngine(
            dao,
            object : RemoteCrmDataSource {
                override suspend fun apply(operation: SyncOperationEntity) = RemoteSyncResult.Success
            },
        )

        assertEquals(
            listOf(SyncProcessResult.Synced("op-1"), SyncProcessResult.Synced("op-2")),
            engine.processBatch(maxOperations = 5),
        )
        assertTrue(dao.allOperations.isEmpty())
    }

    @Test
    fun unconfiguredBackend_doesNotConsumeQueue() = runTest {
        val dao = FakeSyncOperationDao(listOf(operation()))
        val engine = CrmSyncEngine(dao, UnconfiguredRemoteCrmDataSource)

        assertEquals(SyncProcessResult.RemoteNotConfigured, engine.processOne())
        assertEquals(0, dao.operation?.attemptCount)
        assertEquals(SyncOperationState.PENDING.name, dao.operation?.state)
    }

    @Test
    fun retryPolicy_isBoundedExponentialBackoff() {
        val policy = CrmSyncRetryPolicy(maxAttempts = 5, baseDelayMs = 1000, maxDelayMs = 5000)
        assertEquals(1000L, policy.delayMs(1))
        assertEquals(2000L, policy.delayMs(2))
        assertEquals(4000L, policy.delayMs(3))
        assertEquals(5000L, policy.delayMs(4))
        assertEquals(false, policy.shouldRetry(5))
    }

    private class FakeSyncOperationDao(
        initial: List<SyncOperationEntity>,
    ) : SyncOperationDao {
        val allOperations = initial.toMutableList()
        val operation: SyncOperationEntity?
            get() = allOperations.firstOrNull { it.state == SyncOperationState.PENDING.name }

        override suspend fun insert(operation: SyncOperationEntity) {
            allOperations.removeAll { it.id == operation.id }
            allOperations += operation
        }

        override suspend fun pending(limit: Int): List<SyncOperationEntity> =
            allOperations
                .filter { it.state == SyncOperationState.PENDING.name }
                .sortedBy { it.createdAtEpochMs }
                .take(limit)

        override suspend fun updateAttemptAndState(
            id: String,
            attemptCount: Int,
            lastError: String?,
            state: String,
        ) {
            val index = allOperations.indexOfFirst { it.id == id }
            if (index >= 0) {
                allOperations[index] = allOperations[index].copy(
                    attemptCount = attemptCount,
                    lastError = lastError,
                    state = state,
                )
            }
        }

        override suspend fun updateState(id: String, state: String, lastError: String?) {
            val index = allOperations.indexOfFirst { it.id == id }
            if (index >= 0) {
                allOperations[index] = allOperations[index].copy(
                    state = state,
                    lastError = lastError,
                )
            }
        }

        override suspend fun delete(id: String) {
            allOperations.removeAll { it.id == id }
        }
    }
}

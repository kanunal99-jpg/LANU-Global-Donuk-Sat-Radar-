package com.lanu.globaldonuksatisradari.crm

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CrmSyncEngineTest {
    private fun operation(attemptCount: Int = 0) = SyncOperationEntity(
        id = "op-1",
        entityType = "customer",
        entityId = "customer-1",
        operation = "UPDATE",
        payloadVersion = 1,
        payloadJson = "{}",
        createdAtEpochMs = 1L,
        attemptCount = attemptCount,
        lastError = null,
    )

    @Test
    fun retryableFailure_incrementsAttempt_andKeepsOperation() = runTest {
        val dao = FakeSyncOperationDao(operation())
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
    }

    @Test
    fun success_deletesOperation_andIsIdempotentAtQueueBoundary() = runTest {
        val dao = FakeSyncOperationDao(operation())
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
    fun unconfiguredBackend_doesNotConsumeQueue() = runTest {
        val dao = FakeSyncOperationDao(operation())
        val engine = CrmSyncEngine(dao, UnconfiguredRemoteCrmDataSource)

        assertEquals(SyncProcessResult.RemoteNotConfigured, engine.processOne())
        assertEquals(0, dao.operation?.attemptCount)
        assertTrue(dao.operation != null)
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

    private class FakeSyncOperationDao(initial: SyncOperationEntity?) : SyncOperationDao {
        var operation: SyncOperationEntity? = initial

        override suspend fun insert(operation: SyncOperationEntity) {
            this.operation = operation
        }

        override suspend fun pending(limit: Int): List<SyncOperationEntity> =
            operation?.let(::listOf)?.take(limit) ?: emptyList()

        override suspend fun updateAttempt(id: String, attemptCount: Int, lastError: String?) {
            operation = operation?.takeIf { it.id == id }?.copy(
                attemptCount = attemptCount,
                lastError = lastError,
            )
        }

        override suspend fun delete(id: String) {
            if (operation?.id == id) operation = null
        }
    }
}

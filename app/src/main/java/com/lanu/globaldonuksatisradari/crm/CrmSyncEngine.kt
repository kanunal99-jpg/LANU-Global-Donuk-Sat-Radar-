package com.lanu.globaldonuksatisradari.crm

/** Remote boundary for CRM synchronization. No concrete backend is assumed here. */
interface RemoteCrmDataSource {
    suspend fun apply(operation: SyncOperationEntity): RemoteSyncResult
}

sealed interface RemoteSyncResult {
    data object Success : RemoteSyncResult
    data object NotConfigured : RemoteSyncResult
    data class RetryableFailure(val reason: String) : RemoteSyncResult
    data class Conflict(val reason: String) : RemoteSyncResult
    data class PermanentFailure(val reason: String) : RemoteSyncResult
}

sealed interface SyncProcessResult {
    data object NoWork : SyncProcessResult
    data object RemoteNotConfigured : SyncProcessResult
    data class Synced(val operationId: String) : SyncProcessResult
    data class Deferred(val operationId: String, val nextAttempt: Int, val reason: String) : SyncProcessResult
    data class Conflict(val operationId: String, val reason: String) : SyncProcessResult
    data class Failed(val operationId: String, val reason: String) : SyncProcessResult
}

class CrmSyncEngine(
    private val syncDao: SyncOperationDao,
    private val remote: RemoteCrmDataSource,
    private val policy: CrmSyncRetryPolicy = CrmSyncRetryPolicy(),
) {
    suspend fun processOne(): SyncProcessResult {
        val operation = syncDao.pending(1).firstOrNull() ?: return SyncProcessResult.NoWork
        return when (val result = remote.apply(operation)) {
            RemoteSyncResult.Success -> {
                syncDao.delete(operation.id)
                SyncProcessResult.Synced(operation.id)
            }
            RemoteSyncResult.NotConfigured -> SyncProcessResult.RemoteNotConfigured
            is RemoteSyncResult.Conflict -> SyncProcessResult.Conflict(operation.id, result.reason)
            is RemoteSyncResult.PermanentFailure -> {
                syncDao.updateAttempt(operation.id, operation.attemptCount + 1, result.reason)
                SyncProcessResult.Failed(operation.id, result.reason)
            }
            is RemoteSyncResult.RetryableFailure -> {
                val nextAttempt = operation.attemptCount + 1
                syncDao.updateAttempt(operation.id, nextAttempt, result.reason)
                if (policy.shouldRetry(nextAttempt)) {
                    SyncProcessResult.Deferred(operation.id, nextAttempt, result.reason)
                } else {
                    SyncProcessResult.Failed(operation.id, result.reason)
                }
            }
        }
    }
}

/** Exponential backoff with a bounded delay; attempt 1 is the first retry. */
data class CrmSyncRetryPolicy(
    val maxAttempts: Int = 5,
    val baseDelayMs: Long = 1_000L,
    val maxDelayMs: Long = 60_000L,
) {
    init {
        require(maxAttempts >= 1)
        require(baseDelayMs >= 0)
        require(maxDelayMs >= baseDelayMs)
    }

    fun shouldRetry(attempt: Int): Boolean = attempt < maxAttempts

    fun delayMs(attempt: Int): Long {
        require(attempt >= 1)
        var delay = baseDelayMs
        repeat((attempt - 1).coerceAtMost(30)) {
            delay = (delay * 2).coerceAtMost(maxDelayMs)
        }
        return delay.coerceAtMost(maxDelayMs)
    }
}

/** Explicit adapter used until an authorized backend is configured. It never claims a sync occurred. */
object UnconfiguredRemoteCrmDataSource : RemoteCrmDataSource {
    override suspend fun apply(operation: SyncOperationEntity): RemoteSyncResult =
        RemoteSyncResult.NotConfigured
}

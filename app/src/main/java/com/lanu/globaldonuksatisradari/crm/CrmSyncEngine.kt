package com.lanu.globaldonuksatisradari.crm

interface CrmSyncStateStore {
    suspend fun mark(entityType: String, entityId: String, state: SyncState)
}

object NoOpCrmSyncStateStore : CrmSyncStateStore {
    override suspend fun mark(entityType: String, entityId: String, state: SyncState) = Unit
}

class RoomCrmSyncStateStore(
    private val database: LanuCrmDatabase,
) : CrmSyncStateStore {
    override suspend fun mark(entityType: String, entityId: String, state: SyncState) {
        when (entityType) {
            LocalCrmRepository.ENTITY_CUSTOMER ->
                database.customerDao().updateSyncState(entityId, state.name)

            LocalCrmRepository.ENTITY_ACTIVITY ->
                database.activityDao().updateSyncState(entityId, state.name)

            LocalCrmRepository.ENTITY_NEXT_ACTION ->
                database.nextActionDao().updateSyncState(entityId, state.name)

            LocalCrmRepository.ENTITY_OPPORTUNITY ->
                database.opportunityDao().updateSyncState(entityId, state.name)

            CommercialCrmSync.ENTITY_CONTACT ->
                database.contactDao().updateSyncState(entityId, state.name)

            CommercialCrmSync.ENTITY_QUOTE ->
                database.quoteDao().updateSyncState(entityId, state.name)

            CommercialCrmSync.ENTITY_QUOTE_LINE ->
                database.quoteLineDao().updateSyncState(entityId, state.name)

            CommercialCrmSync.ENTITY_ORDER ->
                database.orderDao().updateSyncState(entityId, state.name)

            CommercialCrmSync.ENTITY_ORDER_LINE ->
                database.orderLineDao().updateSyncState(entityId, state.name)
        }
    }
}

/** Resolves local ownership before a queued operation may be pushed to an authenticated cloud user. */
fun interface CrmSyncOwnershipResolver {
    suspend fun isOwnedBy(operation: SyncOperationEntity, ownerUserId: String): Boolean
}

object AllowAllCrmSyncOwnershipResolver : CrmSyncOwnershipResolver {
    override suspend fun isOwnedBy(operation: SyncOperationEntity, ownerUserId: String): Boolean = true
}

class RoomCrmSyncOwnershipResolver(
    private val database: LanuCrmDatabase,
) : CrmSyncOwnershipResolver {
    override suspend fun isOwnedBy(operation: SyncOperationEntity, ownerUserId: String): Boolean {
        val customer = when (operation.entityType) {
            LocalCrmRepository.ENTITY_CUSTOMER ->
                database.customerDao().findById(operation.entityId)

            LocalCrmRepository.ENTITY_ACTIVITY ->
                database.activityDao().findById(operation.entityId)?.customerId?.let {
                    database.customerDao().findById(it)
                }

            LocalCrmRepository.ENTITY_NEXT_ACTION ->
                database.nextActionDao().findById(operation.entityId)?.customerId?.let {
                    database.customerDao().findById(it)
                }

            LocalCrmRepository.ENTITY_OPPORTUNITY ->
                database.opportunityDao().findById(operation.entityId)?.customerId?.let {
                    database.customerDao().findById(it)
                }

            CommercialCrmSync.ENTITY_CONTACT ->
                database.contactDao().findById(operation.entityId)?.customerId?.let {
                    database.customerDao().findById(it)
                }

            CommercialCrmSync.ENTITY_QUOTE ->
                database.quoteDao().findById(operation.entityId)?.customerId?.let {
                    database.customerDao().findById(it)
                }

            CommercialCrmSync.ENTITY_QUOTE_LINE ->
                database.quoteLineDao().findById(operation.entityId)?.quoteId?.let { quoteId ->
                    database.quoteDao().findById(quoteId)?.customerId
                }?.let { database.customerDao().findById(it) }

            CommercialCrmSync.ENTITY_ORDER ->
                database.orderDao().findById(operation.entityId)?.customerId?.let {
                    database.customerDao().findById(it)
                }

            CommercialCrmSync.ENTITY_ORDER_LINE ->
                database.orderLineDao().findById(operation.entityId)?.orderId?.let { orderId ->
                    database.orderDao().findById(orderId)?.customerId
                }?.let { database.customerDao().findById(it) }

            else -> null
        }
        return customer?.ownerUserId == ownerUserId
    }
}

/** Remote boundary for CRM synchronization. */
interface RemoteCrmDataSource {
    suspend fun apply(operation: SyncOperationEntity): RemoteSyncResult
    suspend fun pullInto(database: LanuCrmDatabase): RemotePullResult = RemotePullResult.NotConfigured
}

sealed interface RemotePullResult {
    data object NotConfigured : RemotePullResult
    data object Success : RemotePullResult
    data class RetryableFailure(val reason: String) : RemotePullResult
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
    private val stateStore: CrmSyncStateStore = NoOpCrmSyncStateStore,
    private val ownerUserId: String? = null,
    private val ownershipResolver: CrmSyncOwnershipResolver = AllowAllCrmSyncOwnershipResolver,
) {
    private suspend fun nextOperation(): SyncOperationEntity? {
        val owner = ownerUserId
        val candidates = if (owner == null) {
            syncDao.pending(OWNERSHIP_SCAN_LIMIT)
        } else {
            // Owner filtering happens in SQLite before LIMIT. Another user's large offline queue can
            // therefore never hide/starve this session's work behind the scan limit.
            syncDao.pendingForOwner(owner, OWNERSHIP_SCAN_LIMIT)
        }
        return if (owner == null) {
            candidates.firstOrNull()
        } else {
            // Keep the resolver as a second independent boundary in case a future DAO query regresses.
            candidates.firstOrNull { ownershipResolver.isOwnedBy(it, owner) }
        }
    }

    suspend fun processOne(): SyncProcessResult {
        val operation = nextOperation() ?: return SyncProcessResult.NoWork

        return when (val result = remote.apply(operation)) {
            RemoteSyncResult.Success -> {
                syncDao.delete(operation.id)
                if (syncDao.countForEntity(operation.entityType, operation.entityId) == 0) {
                    stateStore.mark(operation.entityType, operation.entityId, SyncState.SYNCED)
                }
                SyncProcessResult.Synced(operation.id)
            }
            RemoteSyncResult.NotConfigured -> SyncProcessResult.RemoteNotConfigured
            is RemoteSyncResult.Conflict -> {
                val nextAttempt = operation.attemptCount + 1
                stateStore.mark(operation.entityType, operation.entityId, SyncState.CONFLICT)
                syncDao.updateAttemptAndState(
                    id = operation.id,
                    attemptCount = nextAttempt,
                    lastError = result.reason,
                    state = SyncOperationState.CONFLICT.name,
                )
                SyncProcessResult.Conflict(operation.id, result.reason)
            }
            is RemoteSyncResult.PermanentFailure -> {
                val nextAttempt = operation.attemptCount + 1
                stateStore.mark(operation.entityType, operation.entityId, SyncState.FAILED)
                syncDao.updateAttemptAndState(
                    id = operation.id,
                    attemptCount = nextAttempt,
                    lastError = result.reason,
                    state = SyncOperationState.FAILED.name,
                )
                SyncProcessResult.Failed(operation.id, result.reason)
            }
            is RemoteSyncResult.RetryableFailure -> {
                // A long network outage must NEVER turn recoverable local edits into a
                // permanently stuck FAILED outbox row. Saturate the retry counter so
                // WorkManager's bounded backoff may continue until connectivity returns.
                // Only permanent server errors and explicit version conflicts are parked.
                val nextAttempt = operation.attemptCount.coerceIn(0, policy.maxAttempts - 1) + 1
                syncDao.updateAttemptAndState(
                    id = operation.id,
                    attemptCount = nextAttempt,
                    lastError = result.reason,
                    state = SyncOperationState.PENDING.name,
                )
                SyncProcessResult.Deferred(operation.id, nextAttempt, result.reason)
            }
        }
    }

    suspend fun processBatch(maxOperations: Int = DEFAULT_BATCH_SIZE): List<SyncProcessResult> {
        require(maxOperations >= 1)
        val results = mutableListOf<SyncProcessResult>()
        repeat(maxOperations) {
            when (val result = processOne()) {
                SyncProcessResult.NoWork -> return results
                SyncProcessResult.RemoteNotConfigured,
                is SyncProcessResult.Deferred,
                -> {
                    results += result
                    return results
                }
                else -> results += result
            }
        }
        return results
    }

    companion object {
        const val DEFAULT_BATCH_SIZE = 20
        private const val OWNERSHIP_SCAN_LIMIT = 500
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

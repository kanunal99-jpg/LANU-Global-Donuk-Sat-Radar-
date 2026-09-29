package com.lanu.globaldonuksatisradari.crm

/**
 * Canonical production synchronization boundary.
 *
 * Pulls reuse the dirty-row-safe composite path. Versioned pushes go through the production
 * SECURITY INVOKER optimistic-concurrency RPC so version check + mutation + operation-id
 * idempotency happen in one PostgreSQL transaction under RLS. Append-only activities keep the
 * proven row-equivalence guard so a retry is idempotent and a same-id different payload conflicts.
 */
class HardenedSupabaseFullCrmRemoteDataSource(
    auth: SupabaseAuthClient,
) : RemoteCrmDataSource {
    private val composite = SupabaseFullCrmRemoteDataSource(auth)
    private val atomicPush = SupabaseAtomicMutationRemoteDataSource(auth)
    private val appendOnlyCorePush = SupabaseSafeCorePush(auth)

    override suspend fun apply(operation: SyncOperationEntity): RemoteSyncResult =
        if (operation.entityType == LocalCrmRepository.ENTITY_ACTIVITY) {
            appendOnlyCorePush.apply(operation)
        } else {
            atomicPush.apply(operation)
        }

    override suspend fun pullInto(database: LanuCrmDatabase): RemotePullResult =
        composite.pullInto(database)
}

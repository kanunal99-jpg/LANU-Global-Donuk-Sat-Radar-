package com.lanu.globaldonuksatisradari.crm

/**
 * Canonical production synchronization boundary.
 *
 * Pulls reuse the dirty-row-safe composite path. Versioned pushes go through the production
 * SECURITY INVOKER optimistic-concurrency RPC so version check + mutation + operation-id
 * idempotency happen in one PostgreSQL transaction under RLS. Append-only entities (currently
 * activities) fall back to the proven direct adapter inside [SupabaseAtomicMutationRemoteDataSource].
 */
class HardenedSupabaseFullCrmRemoteDataSource(
    auth: SupabaseAuthClient,
) : RemoteCrmDataSource {
    private val composite = SupabaseFullCrmRemoteDataSource(auth)
    private val atomicPush = SupabaseAtomicMutationRemoteDataSource(auth)

    override suspend fun apply(operation: SyncOperationEntity): RemoteSyncResult =
        atomicPush.apply(operation)

    override suspend fun pullInto(database: LanuCrmDatabase): RemotePullResult =
        composite.pullInto(database)
}

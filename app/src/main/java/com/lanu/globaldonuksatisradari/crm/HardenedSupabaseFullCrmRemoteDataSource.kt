package com.lanu.globaldonuksatisradari.crm

/**
 * Production synchronization boundary.
 *
 * Pulls reuse the dirty-row-safe composite path. Legacy/core pushes pass through the equal-version
 * divergence guard, while commercial pushes use their dedicated adapter. Keeping this wrapper
 * separate makes the fallback layers explicit and avoids rewriting the already-tested adapters.
 */
class HardenedSupabaseFullCrmRemoteDataSource(
    auth: SupabaseAuthClient,
) : RemoteCrmDataSource {
    private val composite = SupabaseFullCrmRemoteDataSource(auth)
    private val safeCorePush = SupabaseSafeCorePush(auth)

    override suspend fun apply(operation: SyncOperationEntity): RemoteSyncResult =
        if (operation.entityType in COMMERCIAL_ENTITY_TYPES) {
            composite.apply(operation)
        } else {
            safeCorePush.apply(operation)
        }

    override suspend fun pullInto(database: LanuCrmDatabase): RemotePullResult =
        composite.pullInto(database)

    private companion object {
        val COMMERCIAL_ENTITY_TYPES = setOf(
            CommercialCrmSync.ENTITY_CONTACT,
            CommercialCrmSync.ENTITY_QUOTE,
            CommercialCrmSync.ENTITY_QUOTE_LINE,
            CommercialCrmSync.ENTITY_ORDER,
            CommercialCrmSync.ENTITY_ORDER_LINE,
        )
    }
}

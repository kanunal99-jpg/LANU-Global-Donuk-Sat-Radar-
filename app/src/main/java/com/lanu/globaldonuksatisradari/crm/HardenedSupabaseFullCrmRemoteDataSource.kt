package com.lanu.globaldonuksatisradari.crm

/**
 * Production synchronization boundary.
 *
 * Pulls reuse the dirty-row-safe composite path. All pushes pass through equal-version divergence
 * guards: an identical retry is idempotent, while same-version/different-content writes become an
 * explicit conflict instead of silently overwriting another device.
 */
class HardenedSupabaseFullCrmRemoteDataSource(
    auth: SupabaseAuthClient,
) : RemoteCrmDataSource {
    private val composite = SupabaseFullCrmRemoteDataSource(auth)
    private val safeCorePush = SupabaseSafeCorePush(auth)
    private val safeCommercialPush = SupabaseSafeCommercialPush(auth)

    override suspend fun apply(operation: SyncOperationEntity): RemoteSyncResult =
        if (operation.entityType in COMMERCIAL_ENTITY_TYPES) {
            safeCommercialPush.apply(operation)
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

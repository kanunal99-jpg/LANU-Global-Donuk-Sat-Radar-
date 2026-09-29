package com.lanu.globaldonuksatisradari.crm

/**
 * Decides whether an RLS-scoped remote commercial row may replace its local Room copy.
 *
 * Dirty/local states are deliberately preserved during pull. The WorkManager pipeline pulls first
 * and pushes second; overwriting a PENDING_UPLOAD row here would erase the user's offline edit
 * before the push side can compare versions and surface a CONFLICT. Only clean SYNCED rows may be
 * refreshed from remote, while rows not present locally can always be inserted.
 */
internal object CommercialPullConflictPolicy {
    fun acceptRemote(
        localVersion: Long?,
        localSyncState: String?,
        remoteVersion: Long,
    ): Boolean {
        if (localVersion == null) return true
        if (localSyncState != SyncState.SYNCED.name) return false
        return remoteVersion >= localVersion
    }
}

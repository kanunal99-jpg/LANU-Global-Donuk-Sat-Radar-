package com.lanu.globaldonuksatisradari.crm

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CommercialPullConflictPolicyTest {
    @Test
    fun `missing local row accepts remote row`() {
        assertTrue(
            CrmPullConflictPolicy.acceptRemote(
                localVersion = null,
                localSyncState = null,
                remoteVersion = 1L,
            ),
        )
    }

    @Test
    fun `synced local row accepts same or newer remote version`() {
        assertTrue(
            CrmPullConflictPolicy.acceptRemote(
                localVersion = 2L,
                localSyncState = SyncState.SYNCED.name,
                remoteVersion = 2L,
            ),
        )
        assertTrue(
            CrmPullConflictPolicy.acceptRemote(
                localVersion = 2L,
                localSyncState = SyncState.SYNCED.name,
                remoteVersion = 3L,
            ),
        )
    }

    @Test
    fun `synced local row rejects stale remote version`() {
        assertFalse(
            CrmPullConflictPolicy.acceptRemote(
                localVersion = 3L,
                localSyncState = SyncState.SYNCED.name,
                remoteVersion = 2L,
            ),
        )
    }

    @Test
    fun `pending upload is never overwritten even by newer remote`() {
        assertFalse(
            CrmPullConflictPolicy.acceptRemote(
                localVersion = 2L,
                localSyncState = SyncState.PENDING_UPLOAD.name,
                remoteVersion = 99L,
            ),
        )
    }

    @Test
    fun `conflict failed and local only states are preserved`() {
        listOf(
            SyncState.CONFLICT,
            SyncState.FAILED,
            SyncState.LOCAL_ONLY,
        ).forEach { state ->
            assertFalse(
                CrmPullConflictPolicy.acceptRemote(
                    localVersion = 2L,
                    localSyncState = state.name,
                    remoteVersion = 3L,
                ),
                "Remote pull must not overwrite local state=$state",
            )
        }
    }

    @Test
    fun `commercial compatibility policy delegates to shared rule`() {
        assertFalse(
            CommercialPullConflictPolicy.acceptRemote(
                localVersion = 5L,
                localSyncState = SyncState.PENDING_UPLOAD.name,
                remoteVersion = 6L,
            ),
        )
    }
}

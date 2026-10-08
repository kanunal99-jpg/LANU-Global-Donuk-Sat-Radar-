package com.lanu.globaldonuksatisradari.crm

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/** Uses an isolated encrypted test preferences file; never touches a user's login. */
@RunWith(AndroidJUnit4::class)
class SupabaseCrossAccountInstrumentationTest {
    private fun withIsolatedTokenStore(test: (SecureTokenStore) -> Unit) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = SecureTokenStore(context, "lanu_phase2_auth_test_${UUID.randomUUID()}")
        try {
            test(store)
        } finally {
            store.clear()
        }
    }

    @Test(timeout = 60_000)
    fun workerCannotUseAccountAAfterAccountBSignedIn() = withIsolatedTokenStore { store ->
        val a = SupabaseSession("access-A", "refresh-A", "account-A")
        val b = SupabaseSession("access-B", "refresh-B", "account-B")
        store.save(a)
        val staleWorker = SupabaseAuthClient(store)
        store.save(b)
        // Must return BEFORE contacting Supabase; cached A is not authorized as B.
        assertNull(runBlocking { staleWorker.ensureSession() })
        staleWorker.signOut()
        assertEquals(b, store.load()) // stale A must not delete B's credentials
    }

    @Test(timeout = 60_000)
    fun workerCannotReuseSessionAfterUiSignOut() = withIsolatedTokenStore { store ->
        store.save(SupabaseSession("access-A", "refresh-A", "account-A"))
        val staleWorker = SupabaseAuthClient(store)
        store.clear()
        assertNull(runBlocking { staleWorker.ensureSession() })
        assertNull(store.load())
    }

    @Test(timeout = 60_000)
    fun staleRefreshCannotReplaceNewAccountCredentials() = withIsolatedTokenStore { store ->
        store.save(SupabaseSession("access-A", "refresh-A", "account-A"))
        val staleWorker = SupabaseAuthClient(store)
        val newerAccount = SupabaseSession("access-B", "refresh-B", "account-B")
        store.save(newerAccount)
        // Must fail locally before any refresh HTTP request is made.
        val refreshResult = runBlocking { staleWorker.refresh() }
        assertFalse(refreshResult.isSuccess)
        assertEquals(newerAccount, store.load())
    }
}

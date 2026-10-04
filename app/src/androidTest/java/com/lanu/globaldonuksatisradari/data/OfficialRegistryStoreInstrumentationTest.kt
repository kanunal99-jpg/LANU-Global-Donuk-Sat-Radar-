package com.lanu.globaldonuksatisradari.data

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OfficialRegistryStoreInstrumentationTest {

    @Test
    fun authorizedChunksMergeAndSameRegistryRefreshReplacesOlderRow() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val store = OfficialRegistryStore(context)
        val token = System.nanoTime().toString()
        val city = "Audit Merge " + token

        fun csv(registration: String, businessName: String, phone: String): ByteArray =
            """
                Sicil No;Firma Ünvanı;Durum;Telefon
                $registration;$businessName;Faal;$phone
            """.trimIndent().toByteArray(Charsets.UTF_8)

        store.importDocument(
            source = OfficialRegistrySource.CHAMBER,
            fileName = "chunk-1.csv",
            bytes = csv("AUDIT-$token-A", "Audit İlk", "03121111111"),
            importedAtEpochMs = 10_000L,
            defaultCity = city,
        )
        store.importDocument(
            source = OfficialRegistrySource.CHAMBER,
            fileName = "chunk-2.csv",
            bytes = csv("AUDIT-$token-B", "Audit İkinci", "03122222222"),
            importedAtEpochMs = 11_000L,
            defaultCity = city,
        )

        val afterChunks = store.recordsFor(OfficialRegistrySource.CHAMBER, city, null)
            .filter { it.registrationNumber?.startsWith("AUDIT-$token-") == true }
        assertEquals(2, afterChunks.size)

        store.importDocument(
            source = OfficialRegistrySource.CHAMBER,
            fileName = "chunk-1-refresh.csv",
            bytes = csv("AUDIT-$token-A", "Audit İlk Güncel", "03123333333"),
            importedAtEpochMs = 12_000L,
            defaultCity = city,
        )

        val refreshed = store.recordsFor(OfficialRegistrySource.CHAMBER, city, null)
            .filter { it.registrationNumber?.startsWith("AUDIT-$token-") == true }
        assertEquals(2, refreshed.size)
        val first = refreshed.single { it.registrationNumber == "AUDIT-$token-A" }
        assertEquals("Audit İlk Güncel", first.businessName)
        assertEquals("03123333333", first.phone)
        assertTrue(refreshed.any { it.registrationNumber == "AUDIT-$token-B" })
    }

}

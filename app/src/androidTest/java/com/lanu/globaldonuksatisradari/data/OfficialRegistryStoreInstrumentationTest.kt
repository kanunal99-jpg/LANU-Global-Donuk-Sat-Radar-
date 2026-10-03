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
    fun chamberImportsArePartitionedByCityAndDoNotOverwriteOtherCities() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val store = OfficialRegistryStore(context)

        fun csv(registration: String, businessName: String): ByteArray =
            """
                Sicil No;Firma Ünvanı;Durum
                $registration;$businessName;Faal
            """.trimIndent().toByteArray(Charsets.UTF_8)

        store.importDocument(
            source = OfficialRegistrySource.CHAMBER,
            fileName = "audit-ankara.csv",
            bytes = csv("AUDIT-A-1", "Audit Ankara İlk"),
            importedAtEpochMs = 10_000L,
            defaultCity = "Audit Ankara",
        )
        store.importDocument(
            source = OfficialRegistrySource.CHAMBER,
            fileName = "audit-bursa.csv",
            bytes = csv("AUDIT-B-1", "Audit Bursa"),
            importedAtEpochMs = 11_000L,
            defaultCity = "Audit Bursa",
        )

        val ankaraBeforeRefresh = store.recordsFor("Audit Ankara", null)
            .filter { it.registrationNumber == "AUDIT-A-1" }
        val bursaBeforeRefresh = store.recordsFor("Audit Bursa", null)
            .filter { it.registrationNumber == "AUDIT-B-1" }

        assertEquals(1, ankaraBeforeRefresh.size)
        assertEquals(1, bursaBeforeRefresh.size)

        store.importDocument(
            source = OfficialRegistrySource.CHAMBER,
            fileName = "audit-ankara-refresh.csv",
            bytes = csv("AUDIT-A-2", "Audit Ankara Güncel"),
            importedAtEpochMs = 12_000L,
            defaultCity = "Audit Ankara",
        )

        val ankaraAfterRefresh = store.recordsFor("Audit Ankara", null)
            .filter { it.businessName.startsWith("Audit Ankara") }
        val bursaAfterRefresh = store.recordsFor("Audit Bursa", null)
            .filter { it.registrationNumber == "AUDIT-B-1" }

        assertEquals(1, ankaraAfterRefresh.size)
        assertEquals("AUDIT-A-2", ankaraAfterRefresh.single().registrationNumber)
        assertEquals(1, bursaAfterRefresh.size)
        assertTrue(bursaAfterRefresh.single().businessName == "Audit Bursa")
    }
}

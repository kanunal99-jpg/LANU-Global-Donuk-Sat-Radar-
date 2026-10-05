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


    @Test
    fun ttsgKeepsMultipleEventsForSameRegistryNumber() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val store = OfficialRegistryStore(context)
        val token = System.nanoTime().toString()
        val registration = "TTSG-$token"

        val csv = """
            Sicil No;Ticari Unvan;İl;İlan Türü;Yayın Tarihi;Gazete Sayı;Gazete Sayfa
            $registration;TTSG Olay Testi AŞ;Ankara;Değişiklik - Adres;01.10.2026;11678;100
            $registration;TTSG Olay Testi AŞ;Ankara;Değişiklik - Unvan;02.10.2026;11679;101
        """.trimIndent().toByteArray(Charsets.UTF_8)

        store.importDocument(
            source = OfficialRegistrySource.TTSG,
            fileName = "ttsg-events.csv",
            bytes = csv,
            importedAtEpochMs = 30_000L,
            defaultCity = "Ankara",
        )

        val events = store.recordsFor(OfficialRegistrySource.TTSG, "Ankara", null)
            .filter { it.registrationNumber == registration }

        assertEquals(2, events.size)
        assertTrue(events.any { it.registryEvent == "Değişiklik - Adres" && it.publicationDate == "01.10.2026" })
        assertTrue(events.any { it.registryEvent == "Değişiklik - Unvan" && it.publicationDate == "02.10.2026" })
        assertTrue(events.all { it.status == null })
    }

    @Test
    fun registryRowsWithoutLocationNeverLeakIntoArbitraryCityOrDistrict() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val store = OfficialRegistryStore(context)
        val token = System.nanoTime().toString()
        val registration = "UNSCOPED-" + token
        val csv = """
            MERSİS No;Firma Ünvanı;Durum
            $registration;Konumu Bilinmeyen Resmî Firma;Faal
        """.trimIndent().toByteArray(Charsets.UTF_8)

        store.importDocument(
            source = OfficialRegistrySource.MERSIS,
            fileName = "unscoped.csv",
            bytes = csv,
            importedAtEpochMs = 20_000L,
        )

        assertTrue(
            store.records(OfficialRegistrySource.MERSIS)
                .any { it.registrationNumber == registration },
        )
        assertTrue(
            store.recordsFor(OfficialRegistrySource.MERSIS, "Bursa", null)
                .none { it.registrationNumber == registration },
        )
        assertTrue(
            store.recordsFor(OfficialRegistrySource.MERSIS, "İstanbul", "Kadıköy")
                .none { it.registrationNumber == registration },
        )
    }

}

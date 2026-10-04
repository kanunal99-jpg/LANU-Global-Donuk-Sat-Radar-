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

        assertEquals(2, ankaraAfterRefresh.size)
        assertTrue(ankaraAfterRefresh.any { it.registrationNumber == "AUDIT-A-1" })
        assertTrue(ankaraAfterRefresh.any { it.registrationNumber == "AUDIT-A-2" })
        assertEquals(1, bursaAfterRefresh.size)
        assertTrue(bursaAfterRefresh.single().businessName == "Audit Bursa")
    }

    @Test
    fun authorizedTobbIdentityFieldsSurviveStoreRoundTrip() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val store = OfficialRegistryStore(context)
        val json = """
            [{
              "uyeOid":"AUDIT-TOBB-OID-1",
              "unvan":"Audit Yetkili Firma AŞ",
              "tabelaUnvani":"Audit Tabela",
              "mersisNo":"0123456789012345",
              "ticaretSicilNo":"AUDIT-TS-1",
              "odaBorsaNo":"16",
              "vergiNo":"1234567890",
              "durum":"FAAL",
              "il":"Audit Bursa Tobb",
              "ilce":"Nilüfer",
              "anaFaaliyetKodu":"46.90.01",
              "anaFaaliyetAciklamasi":"Toptan ticaret"
            }]
        """.trimIndent().toByteArray(Charsets.UTF_8)

        store.importDocument(
            source = OfficialRegistrySource.TOBB,
            fileName = "audit-tobb.json",
            bytes = json,
            importedAtEpochMs = 20_000L,
        )

        val record = store.recordsFor(
            source = OfficialRegistrySource.TOBB,
            city = "Audit Bursa Tobb",
            district = "Nilüfer",
        ).single { it.sourceRecordId == "AUDIT-TOBB-OID-1" }

        assertEquals("Audit Tabela", record.signboardName)
        assertEquals("0123456789012345", record.mersisNumber)
        assertEquals("AUDIT-TS-1", record.registrationNumber)
        assertEquals("16", record.chamberCode)
        assertEquals("1234567890", record.taxNumber)
        assertEquals("Toptan ticaret", record.businessType)
        assertTrue(OfficialRegistryTrust.isIdentityVerified(record))
    }

}

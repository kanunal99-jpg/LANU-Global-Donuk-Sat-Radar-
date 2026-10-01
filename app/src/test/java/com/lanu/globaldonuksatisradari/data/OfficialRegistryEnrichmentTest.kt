package com.lanu.globaldonuksatisradari.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OfficialRegistryEnrichmentTest {

    private val osmSource = DataSourceDescriptor(
        id = "osm-overpass",
        name = "OSM",
        publisher = "OSM",
        licenseOrTerms = "ODbL",
        sourceUrl = "https://www.openstreetmap.org/",
        lastVerifiedAtEpochMs = 1L,
    )

    private fun business(
        name: String = "Örnek Gıda",
        phone: String? = null,
        address: String? = "Eski Adres",
    ) = VerifiedBusiness(
        id = "node:1",
        name = name,
        city = "İstanbul",
        district = "Kadıköy",
        neighborhood = null,
        source = osmSource,
        verifiedAtEpochMs = 10L,
        latitude = 40.9900,
        longitude = 29.0300,
        category = "restaurant",
        address = address,
        phone = phone,
    )

    private fun record(
        source: OfficialRegistrySource = OfficialRegistrySource.ITO,
        name: String = "Örnek Gıda",
        status: String? = "Faal",
        phone: String? = "0216 555 44 33",
        address: String? = "Caferağa Mah. Moda Cad. No:10 Kadıköy / İstanbul",
        registrationNumber: String? = "123456",
    ) = OfficialRegistryRecord(
        source = source,
        registrationNumber = registrationNumber,
        businessName = name,
        status = status,
        city = "İstanbul",
        district = "Kadıköy",
        neighborhood = "Caferağa",
        address = address,
        phone = phone,
        website = "https://example.com",
        importedAtEpochMs = 20L,
    )

    @Test
    fun officialActiveRecordOverridesPhoneAndOpenAddressButKeepsOsmCoordinates() {
        val enriched = OfficialRegistryEnricher.enrich(
            businesses = listOf(business()),
            records = listOf(record()),
        ).single()

        assertEquals("0216 555 44 33", enriched.phone)
        assertEquals("Caferağa Mah. Moda Cad. No:10 Kadıköy / İstanbul", enriched.address)
        assertEquals("Caferağa", enriched.neighborhood)
        assertEquals(40.9900, enriched.latitude)
        assertEquals(29.0300, enriched.longitude)
        assertEquals("osm-overpass", enriched.source.id)
        assertEquals("official-ito", enriched.officialRegistryEvidence?.source?.id)
        assertEquals("123456", enriched.officialRegistryEvidence?.registrationNumber)
        assertTrue(enriched.officialRegistryEvidence?.explicitlyActive == true)
        assertTrue(enriched.officialRegistryEvidence?.fieldsUsed?.contains("phone") == true)
        assertTrue(enriched.officialRegistryEvidence?.fieldsUsed?.contains("address") == true)
    }

    @Test
    fun explicitlyInactiveOfficialRecordRemovesMatchedBusiness() {
        val result = OfficialRegistryEnricher.enrich(
            businesses = listOf(business(phone = "0216 555 44 33")),
            records = listOf(record(status = "Terkin")),
        )

        assertTrue(result.isEmpty())
    }

    @Test
    fun ambiguousSameNameRecordsAreNotUsedWithoutEnoughIdentityEvidence() {
        val first = record(
            address = null,
            phone = null,
            registrationNumber = "1",
        )
        val second = record(
            address = null,
            phone = null,
            registrationNumber = "2",
        )

        val enriched = OfficialRegistryEnricher.enrich(
            businesses = listOf(business(address = null)),
            records = listOf(first, second),
        ).single()

        assertNull(enriched.officialRegistryEvidence)
        assertNull(enriched.phone)
    }

    @Test
    fun csvImportRecognizesTurkishOfficialHeadersAndSanitizesFields() {
        val csv = """
            Sicil No;Firma Ünvanı;Durum;İl;İlçe;Mahalle;Açık Adres;Telefon No;Web Sitesi
            123456;"Örnek Gıda Ltd. Şti.";Faal;İstanbul;Kadıköy;Caferağa;"Moda Cad. No:10";"0216 555 44 33";example.com
        """.trimIndent()

        val records = OfficialRegistryImportParser.parse(
            bytes = csv.toByteArray(Charsets.UTF_8),
            fileName = "ito.csv",
            source = OfficialRegistrySource.ITO,
            importedAtEpochMs = 100L,
        )

        assertEquals(1, records.size)
        val record = records.single()
        assertEquals("123456", record.registrationNumber)
        assertEquals("Örnek Gıda Ltd. Şti.", record.businessName)
        assertEquals("Faal", record.status)
        assertEquals("İstanbul", record.city)
        assertEquals("Kadıköy", record.district)
        assertEquals("Caferağa", record.neighborhood)
        assertEquals("Moda Cad. No:10", record.address)
        assertEquals("0216 555 44 33", record.phone)
        assertEquals("https://example.com", record.website)
    }

    @Test
    fun officialSourceContractsRequireAuthorizedImportInsteadOfAnonymousScraping() {
        assertEquals(SourceAccessMethod.OFFICIAL_BULK_REQUEST, OfficialRegistrySource.ITO.contract.accessMethod)
        assertEquals(SourceAccessMethod.AUTHENTICATED_EXPORT, OfficialRegistrySource.MERSIS.contract.accessMethod)
        assertEquals(SourceAccessMethod.AUTHENTICATED_EXPORT, OfficialRegistrySource.ESBIS.contract.accessMethod)
        assertTrue(OfficialRegistrySource.entries.all { it.contract.permittedUseVerified })
        assertTrue(OfficialRegistrySource.entries.all { it.contract.supportsBulk })
        assertFalse(OfficialRegistrySource.entries.any { it.contract.accessMethod == SourceAccessMethod.PUBLIC_SEARCH })
    }
}

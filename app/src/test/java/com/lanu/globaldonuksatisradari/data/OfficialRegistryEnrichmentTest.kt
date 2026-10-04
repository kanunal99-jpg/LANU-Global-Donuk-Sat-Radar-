package com.lanu.globaldonuksatisradari.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
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
        city: String = "İstanbul",
        district: String = "Kadıköy",
        neighborhood: String? = "Caferağa",
    ) = OfficialRegistryRecord(
        source = source,
        registrationNumber = registrationNumber,
        businessName = name,
        status = status,
        city = city,
        district = district,
        neighborhood = neighborhood,
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
    fun explicitlyInactiveOfficialRecordIsRetainedWithoutOverwritingOperationalFields() {
        val result = OfficialRegistryEnricher.enrich(
            businesses = listOf(
                business(
                    phone = "0555 111 22 33",
                    address = "Güncel Operasyon Adresi",
                ),
            ),
            records = listOf(
                record(
                    status = "Terkin",
                    phone = "0216 000 00 00",
                    address = "Eski Sicil Adresi",
                ),
            ),
        )

        assertEquals(1, result.size)
        val enriched = result.single()
        assertTrue(enriched.officialRegistryEvidence?.explicitlyInactive == true)
        assertEquals("Terkin", enriched.officialRegistryEvidence?.status)
        assertEquals("0555 111 22 33", enriched.phone)
        assertEquals("Güncel Operasyon Adresi", enriched.address)
        assertEquals(null, enriched.website)
        assertTrue(enriched.officialRegistryEvidence?.fieldsUsed?.contains("status") == true)
        assertTrue(enriched.officialRegistryEvidence?.fieldsUsed?.contains("phone") == false)
        assertTrue(enriched.officialRegistryEvidence?.fieldsUsed?.contains("address") == false)
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
    fun xlsxImportReadsInlineStringOfficialColumns() {
        val sheet = """<?xml version="1.0" encoding="UTF-8"?>
            <worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
              <sheetData>
                <row r="1">
                  <c r="A1" t="inlineStr"><is><t>Firma Ünvanı</t></is></c>
                  <c r="B1" t="inlineStr"><is><t>Durum</t></is></c>
                  <c r="C1" t="inlineStr"><is><t>İl</t></is></c>
                  <c r="D1" t="inlineStr"><is><t>İlçe</t></is></c>
                  <c r="E1" t="inlineStr"><is><t>Açık Adres</t></is></c>
                  <c r="F1" t="inlineStr"><is><t>Telefon</t></is></c>
                </row>
                <row r="2">
                  <c r="A2" t="inlineStr"><is><t>Test Lokanta</t></is></c>
                  <c r="B2" t="inlineStr"><is><t>Faal</t></is></c>
                  <c r="C2" t="inlineStr"><is><t>İstanbul</t></is></c>
                  <c r="D2" t="inlineStr"><is><t>Kadıköy</t></is></c>
                  <c r="E2" t="inlineStr"><is><t>Rıhtım Cad. No:1</t></is></c>
                  <c r="F2" t="inlineStr"><is><t>0216 000 00 00</t></is></c>
                </row>
              </sheetData>
            </worksheet>
        """.trimIndent()

        val bytes = ByteArrayOutputStream().also { output ->
            ZipOutputStream(output).use { zip ->
                zip.putNextEntry(ZipEntry("xl/worksheets/sheet1.xml"))
                zip.write(sheet.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
        }.toByteArray()

        val records = OfficialRegistryImportParser.parse(
            bytes = bytes,
            fileName = "resmi.xlsx",
            source = OfficialRegistrySource.MERSIS,
            importedAtEpochMs = 200L,
        )

        assertEquals(1, records.size)
        assertEquals("Test Lokanta", records.single().businessName)
        assertEquals("Rıhtım Cad. No:1", records.single().address)
        assertEquals("0216 000 00 00", records.single().phone)
    }

    @Test
    fun legalTitleCanMatchShortTradeNameWhenRegionAlsoMatches() {
        val match = OfficialRegistryMatcher.bestMatch(
            name = "Arslan Gıda",
            city = "İstanbul",
            district = "Sultanbeyli",
            address = "Dilber Sokak",
            phone = null,
            records = listOf(
                record(
                    name = "ARSLAN GIDA SANAYİ VE TİCARET LİMİTED ŞİRKETİ",
                    address = "Dilber Sokak No:10 Sultanbeyli İstanbul",
                    district = "Sultanbeyli",
                    neighborhood = null,
                ),
            ),
        )

        assertEquals("123456", match?.registrationNumber)
    }

    @Test
    fun sourceSelectionWithoutRegistryIdCannotOverrideTrustedBusinessFields() {
        val result = OfficialRegistryEnricher.enrich(
            businesses = listOf(
                business(
                    phone = "0216 111 22 33",
                    address = "OSM Adresi",
                ),
            ),
            records = listOf(
                record(
                    registrationNumber = null,
                    status = "Faal",
                    phone = "0216 999 88 77",
                    address = "Kaynağı doğrulanmamış adres",
                ),
            ),
        ).single()

        assertEquals("0216 111 22 33", result.phone)
        assertEquals("OSM Adresi", result.address)
        assertNull(result.officialRegistryEvidence)
    }

    @Test
    fun itoPublicMemberHeadersPreserveRegistryStatusDistrictAndNace() {
        val csv = """
            Sicil No;Ünvan;Üyelik Durum;Semt;NACE
            1103007;TANHAŞ DADAŞ EKMEK FIRIN VE UNLU MAMULLERİ SANAYİ TİCARET LİMİTED ŞİRKETİ;Faal;SULTANBEYLİ;10.71.02
        """.trimIndent()

        val record = OfficialRegistryImportParser.parse(
            bytes = csv.toByteArray(Charsets.UTF_8),
            fileName = "ito-uye-listesi.csv",
            source = OfficialRegistrySource.ITO,
            importedAtEpochMs = 300L,
        ).single()

        assertEquals("1103007", record.registrationNumber)
        assertEquals("Faal", record.status)
        assertEquals("İstanbul", record.city)
        assertEquals("SULTANBEYLİ", record.district)
        assertEquals("10.71.02", record.naceCode)
        assertTrue(OfficialRegistryTrust.isIdentityVerified(record))
    }

    @Test
    fun exportedRegistryIdHeaderCanBeReimportedWithoutLosingIdentity() {
        val csv = """
            Nokta Adı;Sicil / Kayıt No;Durum;İl;İlçe;Açık Adres
            Örnek Gıda;123456;Faal;İstanbul;Kadıköy;Moda Cad. No:10
        """.trimIndent()

        val record = OfficialRegistryImportParser.parse(
            bytes = csv.toByteArray(Charsets.UTF_8),
            fileName = "lanu-registry.csv",
            source = OfficialRegistrySource.ITO,
            importedAtEpochMs = 400L,
        ).single()

        assertEquals("123456", record.registrationNumber)
        assertTrue(OfficialRegistryTrust.isIdentityVerified(record))
    }

    @Test
    fun lanuExportWithoutRegistryNumberStaysUnverifiedWhenReimported() {
        val csv = """
            Nokta Adı;Telefon No;İl;İlçe;Mahalle;Açık Adres;Kaynak;Durum
            Test Market;+90 555 111 22 33;İstanbul;Ataşehir;İçerenköy;Örnek Cadde;İTO;Durum belirtilmemiş
        """.trimIndent()

        val record = OfficialRegistryImportParser.parse(
            bytes = csv.toByteArray(Charsets.UTF_8),
            fileName = "lanu-ito-export.csv",
            source = OfficialRegistrySource.ITO,
            importedAtEpochMs = 500L,
        ).single()

        assertEquals("Test Market", record.businessName)
        assertEquals("+90 555 111 22 33", record.phone)
        assertNull(record.registrationNumber)
        assertFalse(OfficialRegistryTrust.isIdentityVerified(record))
    }

    @Test
    fun chamberExportCollectsMultiplePhoneColumnsWithoutDuplicates() {
        val csv = """
            Oda Sicil No;Ünvan;İl;İlçe;Telefon 1;Telefon 2;GSM;Cep Telefonu;Durum
            765432;Örnek Ticaret;Ankara;Çankaya;0312 111 22 33;0312 444 55 66;0532 777 88 99;0532 777 88 99;Faal
        """.trimIndent()

        val record = OfficialRegistryImportParser.parse(
            bytes = csv.toByteArray(Charsets.UTF_8),
            fileName = "oda-uye-listesi.csv",
            source = OfficialRegistrySource.CHAMBER,
            importedAtEpochMs = 600L,
        ).single()

        assertEquals("765432", record.registrationNumber)
        assertEquals(
            "0312 111 22 33 / 0312 444 55 66 / 0532 777 88 99",
            record.phone,
        )
        assertTrue(OfficialRegistryTrust.isIdentityVerified(record))
    }

    @Test
    fun matcherAcceptsAnyPhoneFromMultiPhoneChamberRecord() {
        val match = OfficialRegistryMatcher.bestMatch(
            name = "Farklı Yazılmış Ünvan",
            city = "Ankara",
            district = "Çankaya",
            address = null,
            phone = "0532 777 88 99",
            records = listOf(
                record(
                    source = OfficialRegistrySource.CHAMBER,
                    name = "Örnek Ticaret Limited Şirketi",
                    phone = "0312 111 22 33 / 0532 777 88 99",
                    city = "Ankara",
                    district = "Çankaya",
                ),
            ),
        )

        assertEquals("123456", match?.registrationNumber)
    }

    @Test
    fun tobbStyleWorkplaceAndOfficePhonesAreCollected() {
        val csv = """
            Oda Sicil No;Firma Ünvanı;İl;İlçe;İşyeri Tel;Büro Tel;İşyeri Adresi
            445566;Örnek Sanayi AŞ;Bursa;Nilüfer;0224 111 22 33;0224 444 55 66;Organize Sanayi Bölgesi No:1
        """.trimIndent()

        val record = OfficialRegistryImportParser.parse(
            bytes = csv.toByteArray(Charsets.UTF_8),
            fileName = "tobb-kapasite.csv",
            source = OfficialRegistrySource.TOBB,
            importedAtEpochMs = 700L,
        ).single()

        assertEquals("445566", record.registrationNumber)
        assertEquals("0224 111 22 33 / 0224 444 55 66", record.phone)
        assertEquals("Organize Sanayi Bölgesi No:1", record.address)
        assertTrue(OfficialRegistryTrust.isIdentityVerified(record))
    }

    @Test
    fun localChamberImportUsesSelectedCityWhenFileHasNoCityColumn() {
        val csv = """
            Oda Sicil No;Ünvan;İlçe;Telefon;Durum
            998877;Örnek Oda Üyesi;Seyhan;0322 111 22 33;Faal
        """.trimIndent()

        val record = OfficialRegistryImportParser.parse(
            bytes = csv.toByteArray(Charsets.UTF_8),
            fileName = "yerel-oda.csv",
            source = OfficialRegistrySource.CHAMBER,
            importedAtEpochMs = 800L,
            defaultCity = "Adana",
        ).single()

        assertEquals("Adana", record.city)
        assertEquals("Seyhan", record.district)
        assertEquals("0322 111 22 33", record.phone)
    }

    @Test
    fun officialSourceContractsRequireAuthorizedImportInsteadOfAnonymousScraping() {
        assertEquals(SourceAccessMethod.OFFICIAL_BULK_REQUEST, OfficialRegistrySource.ITO.contract.accessMethod)
        assertEquals(SourceAccessMethod.OFFICIAL_BULK_REQUEST, OfficialRegistrySource.CHAMBER.contract.accessMethod)
        assertEquals(SourceAccessMethod.AUTHENTICATED_EXPORT, OfficialRegistrySource.TOBB.contract.accessMethod)
        assertEquals(SourceAccessMethod.AUTHENTICATED_EXPORT, OfficialRegistrySource.MERSIS.contract.accessMethod)
        assertEquals(SourceAccessMethod.AUTHENTICATED_EXPORT, OfficialRegistrySource.ESBIS.contract.accessMethod)
        assertTrue(OfficialRegistrySource.entries.all { it.contract.permittedUseVerified })
        assertTrue(OfficialRegistrySource.entries.all { it.contract.supportsBulk })
        assertFalse(OfficialRegistrySource.entries.any { it.contract.accessMethod == SourceAccessMethod.PUBLIC_SEARCH })
    }

    @Test
    fun identityVerifiedRegistryRowCanBecomeStandaloneBroadInventoryBusiness() {
        val standalone = OfficialRegistryDiscovery.toVerifiedBusiness(
            record = record(
                name = "Bağımsız Sanayi AŞ",
                registrationNumber = "998877",
                status = "Faal",
                address = "Organize Sanayi Bölgesi No:5",
                phone = "0216 777 66 55",
            ).copy(naceCode = "10.89.09"),
            selectedCity = "İstanbul",
            selectedDistrict = "Kadıköy",
        )

        requireNotNull(standalone)
        assertEquals("Bağımsız Sanayi AŞ", standalone.name)
        assertEquals("official-ito", standalone.source.id)
        assertEquals("NACE 10.89.09", standalone.category)
        assertEquals("998877", standalone.officialRegistryEvidence?.registrationNumber)
        assertTrue(standalone.officialRegistryEvidence?.explicitlyActive == true)
    }

    @Test
    fun broadInventoryAddsOfficialBusinessMissingFromOsmWithoutDuplicatingMatchedOne() {
        val matched = record(
            name = "Örnek Gıda",
            registrationNumber = "123456",
            status = "Faal",
        )
        val registryOnly = record(
            name = "Haritada Olmayan Toptancı",
            registrationNumber = "654321",
            status = "Faal",
            phone = "0216 999 88 77",
            address = "Sanayi Cad. No:22",
        )

        val merged = OfficialRegistryDiscovery.mergeIntoBroadInventory(
            discovered = listOf(business()),
            records = listOf(matched, registryOnly),
            selectedCity = "İstanbul",
            selectedDistrict = "Kadıköy",
        )

        assertEquals(2, merged.size)
        assertTrue(merged.any { it.name == "Örnek Gıda" && it.officialRegistryEvidence?.registrationNumber == "123456" })
        assertTrue(merged.any { it.name == "Haritada Olmayan Toptancı" && it.source.id == "official-ito" })
    }

    @Test
    fun registryRowWithoutIdentityNumberDoesNotPretendToBeStandaloneVerifiedBusiness() {
        val standalone = OfficialRegistryDiscovery.toVerifiedBusiness(
            record = record(registrationNumber = null),
            selectedCity = "İstanbul",
            selectedDistrict = "Kadıköy",
        )

        assertNull(standalone)
    }


    @Test
    fun zipImportCombinesMultipleAuthorizedRegistryFiles() {
        val zipBytes = ByteArrayOutputStream().also { output ->
            ZipOutputStream(output).use { zip ->
                listOf(
                    "gida.csv" to """
                        Oda Sicil No;Ünvan;Tescil Adresi;İlçe;Durum
                        1001;Birinci Oda Üyesi;Merkez Mah. No:1;İnegöl;Faal
                    """.trimIndent(),
                    "hizmet.csv" to """
                        Oda Sicil No;Ünvan;Tescil Adresi;İlçe;Durum
                        1002;İkinci Oda Üyesi;Sanayi Cad. No:2;İnegöl;Faal
                    """.trimIndent(),
                ).forEach { (name, payload) ->
                    zip.putNextEntry(ZipEntry(name))
                    zip.write(payload.toByteArray(Charsets.UTF_8))
                    zip.closeEntry()
                }
                zip.putNextEntry(ZipEntry("README.md"))
                zip.write("ignored".toByteArray())
                zip.closeEntry()
            }
        }.toByteArray()

        val records = OfficialRegistryImportParser.parse(
            bytes = zipBytes,
            fileName = "inegol-yetkili-export.zip",
            source = OfficialRegistrySource.CHAMBER,
            importedAtEpochMs = 900L,
            defaultCity = "Bursa",
        )

        assertEquals(2, records.size)
        assertEquals(setOf("1001", "1002"), records.mapNotNull { it.registrationNumber }.toSet())
        assertTrue(records.all { it.city == "Bursa" })
        assertTrue(records.all { it.district == "İnegöl" })
    }

    @Test
    fun tescilAdresiHeaderFromPublicChamberListIsRecognizedWithoutFakeRegistryIdentity() {
        val csv = """
            Unvan;Tescil Adresi
            Örnek Açık Oda Üyesi;Cuma Mah. Atatürk Bul. No:10 İnegöl / Bursa
        """.trimIndent()

        val record = OfficialRegistryImportParser.parse(
            bytes = csv.toByteArray(Charsets.UTF_8),
            fileName = "public-chamber-list.csv",
            source = OfficialRegistrySource.CHAMBER,
            importedAtEpochMs = 901L,
            defaultCity = "Bursa",
        ).single()

        assertEquals("Örnek Açık Oda Üyesi", record.businessName)
        assertEquals("Cuma Mah. Atatürk Bul. No:10 İnegöl / Bursa", record.address)
        assertEquals("Bursa", record.city)
        assertNull(record.registrationNumber)
        assertFalse(OfficialRegistryTrust.isIdentityVerified(record))
    }


    @Test
    fun authorizedExportPreservesSignboardTaxIdAndNace() {
        val csv = """
            Oda Sicil No;Ünvan;Tabela Ünvanı;Vergi Numarası;NACE Kodu;Durum;İl;İlçe
            778899;ÖRNEK TİCARET LİMİTED ŞİRKETİ;Örnek Market;1234567890;47.11.01;Faal;Bursa;İnegöl
        """.trimIndent()

        val record = OfficialRegistryImportParser.parse(
            bytes = csv.toByteArray(Charsets.UTF_8),
            fileName = "yetkili-oda-export.csv",
            source = OfficialRegistrySource.CHAMBER,
            importedAtEpochMs = 902L,
        ).single()

        assertEquals("778899", record.registrationNumber)
        assertEquals("Örnek Market", record.signboardName)
        assertEquals("1234567890", record.taxOrNationalId)
        assertEquals("47.11.01", record.naceCode)
        assertTrue(OfficialRegistryTrust.isIdentityVerified(record))
    }


    @Test
    fun tobbAllMembersJsonExportMapsDocumentedBusinessFieldsAndIgnoresPersonalCardData() {
        val json = """
            {
              "obResult": {
                "hatali": false,
                "donusDegeri": "[{\\\"uyeOid\\\":\\\"U-1\\\",\\\"mersisNo\\\":\\\"0123456789012345\\\",\\\"unvan\\\":\\\"TOBB TEST SANAYİ AŞ\\\",\\\"uyeOdaSicilNo\\\":\\\"5544\\\",\\\"ticaretSicilNo\\\":\\\"7788\\\",\\\"vergiNo\\\":\\\"1234567890\\\",\\\"durum\\\":\\\"Faal\\\",\\\"adres\\\":\\\"Organize Sanayi Bölgesi No:1\\\",\\\"il\\\":\\\"16\\\",\\\"ilce\\\":\\\"205\\\",\\\"mahalle\\\":\\\"OSB\\\",\\\"meslekGrubuAdi\\\":\\\"Makine ve İmalat\\\"}]"
              }
            }
        """.trimIndent()

        val record = OfficialRegistryImportParser.parseJsonExport(
            payload = json,
            source = OfficialRegistrySource.TOBB,
            importedAtEpochMs = 903L,
            defaultCity = "Bursa",
        ).single()

        assertEquals("TOBB TEST SANAYİ AŞ", record.businessName)
        assertEquals("5544", record.registrationNumber)
        assertEquals("1234567890", record.taxOrNationalId)
        assertEquals("Bursa", record.city)
        assertNull(record.district)
        assertEquals("OSB", record.neighborhood)
        assertEquals("Organize Sanayi Bölgesi No:1", record.address)
        assertEquals("Faal", record.status)
    }

    @Test
    fun tobbMemberCardJsonPreservesSignboardWebsitePhoneAndNace() {
        val json = """
            {
              "uyelikTemelBilgileri": {
                "unvan": "TOBB KART TEST LTD ŞTİ",
                "tabelaUnvani": "Kart Test",
                "uyeOdaSicilNo": "9900",
                "vergiNo": "9876543210",
                "uyelikDurum": "Faal",
                "webAdresi": "https://kart.example",
                "anaFaaliyetKodu": "46.90.01"
              },
              "telefonList": [
                {"telefonNo": "02241112233", "birincilTelefon": "1"}
              ]
            }
        """.trimIndent()

        val record = OfficialRegistryImportParser.parseJsonExport(
            payload = json,
            source = OfficialRegistrySource.TOBB,
            importedAtEpochMs = 904L,
            defaultCity = "Bursa",
        ).single()

        assertEquals("Kart Test", record.signboardName)
        assertEquals("9876543210", record.taxOrNationalId)
        assertEquals("02241112233", record.phone)
        assertEquals("https://kart.example", record.website)
        assertEquals("46.90.01", record.naceCode)
    }

}

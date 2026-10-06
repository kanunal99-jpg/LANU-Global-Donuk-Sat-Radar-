package com.lanu.globaldonuksatisradari.crm

import com.lanu.globaldonuksatisradari.data.OfficialRegistryRecord
import com.lanu.globaldonuksatisradari.data.OfficialRegistrySource
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

class CrmExcelExporterTest {
    @Test
    fun exportsRequestedHeadersAndLocationData() {
        val customer = CrmCustomer(
            id = "1",
            businessSourceId = "osm:1",
            businessName = "Test Noktası",
            signboardName = "Test Tabelası",
            city = "İstanbul",
            district = "Kadıköy",
            neighborhood = "Caferağa",
            address = "Moda Caddesi 1",
            latitude = 40.987654,
            longitude = 29.123456,
            contactName = "Ayşe Yılmaz",
            businessType = "Restoran",
            taxOrNationalId = "1234567890",
            phone = "05550000000",
            website = "https://example.com",
            registryStatus = CrmRegistryStatus.ACTIVE,
            registrySource = "İTO Resmî Üye/Firma Kaydı",
            registryNumber = "SICIL-123",
            tags = setOf("Sıcak Lead", "Zincir"),
            createdAtEpochMs = 1L,
            updatedAtEpochMs = 1L,
        )

        val officialRecords = listOf(
            OfficialRegistryRecord(
                source = OfficialRegistrySource.MERSIS,
                registrationNumber = "SICIL-123",
                businessName = "TEST NOKTASI GIDA LİMİTED ŞİRKETİ",
                status = "Faal",
                city = "İstanbul",
                district = "Kadıköy",
                neighborhood = "Caferağa",
                address = "Resmî Adres",
                phone = null,
                website = null,
                importedAtEpochMs = 2L,
                naceCode = "56.10.01",
                taxOrNationalId = "1234567890",
            ),
            OfficialRegistryRecord(
                source = OfficialRegistrySource.TTSG,
                registrationNumber = "SICIL-123",
                businessName = "TEST NOKTASI GIDA LİMİTED ŞİRKETİ",
                status = null,
                city = "İstanbul",
                district = "Kadıköy",
                neighborhood = null,
                address = "Tarihsel İlan Adresi",
                phone = null,
                website = null,
                importedAtEpochMs = 3L,
                mersisNumber = "0123456789012345",
                registryOffice = "İSTANBUL",
                registryEvent = "Değişiklik - Adres",
                publicationDate = "10.10.2025",
                registrationDate = "08.10.2025",
                gazetteNumber = "11434",
                gazettePage = "89",
                sourceReference = "ilan-ref-1",
            ),
        )

        val bytes = CrmExcelExporter.build(listOf(customer), officialRecords)
        assertTrue(bytes.size > 1000)

        var sheetXml = ""
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.name == "xl/worksheets/sheet1.xml") {
                    sheetXml = zip.bufferedReader(Charsets.UTF_8).readText()
                    break
                }
            }
        }

        CrmExcelExporter.headers.forEach { header ->
            assertTrue("Missing header: $header", sheetXml.contains(header))
        }
        assertTrue(sheetXml.contains("Ayşe Yılmaz"))
        assertTrue(sheetXml.contains("Test Noktası"))
        assertTrue(sheetXml.contains("Test Tabelası"))
        assertTrue(sheetXml.contains("Restoran"))
        assertTrue(sheetXml.contains("AKTİF"))
        assertTrue(sheetXml.contains("İTO Resmî Üye/Firma Kaydı"))
        assertTrue(sheetXml.contains("SICIL-123"))
        assertTrue(sheetXml.contains("Radar"))
        assertTrue(sheetXml.contains("TEST NOKTASI GIDA LİMİTED ŞİRKETİ"))
        assertTrue(sheetXml.contains("0123456789012345"))
        assertTrue(sheetXml.contains("İSTANBUL"))
        assertTrue(sheetXml.contains("Değişiklik - Adres"))
        assertTrue(sheetXml.contains("10.10.2025"))
        assertTrue(sheetXml.contains("08.10.2025"))
        assertTrue(sheetXml.contains("11434"))
        assertTrue(sheetXml.contains("89"))
        assertTrue(sheetXml.contains("ilan-ref-1"))
        assertTrue(sheetXml.contains("56.10.01"))
        assertTrue(sheetXml.contains("Tarihsel İlan Adresi"))
        assertTrue(sheetXml.contains("1234567890"))
        assertTrue(sheetXml.contains("05550000000"))
        assertTrue(sheetXml.contains("https://example.com"))
        assertTrue(sheetXml.contains("29.123456"))
        assertTrue(sheetXml.contains("40.987654"))
        assertTrue(sheetXml.contains("maps.google.com/?q=40.987654,29.123456"))
        assertTrue(sheetXml.contains("Etiketler"))
        assertTrue(sheetXml.contains("Sıcak Lead | Zincir"))
    }

    @Test
    fun weakLocationPlaceholdersAreNotWrittenToWorkbook() {
        val customer = CrmCustomer(
            id = "weak-location",
            businessSourceId = "node:123",
            businessName = "Zayıf Adres Testi",
            city = "Ağrı",
            district = "Bilinmiyor",
            neighborhood = "Bilinmiyor",
            address = "Ağrı",
            latitude = 39.7,
            longitude = 43.0,
            createdAtEpochMs = 1L,
            updatedAtEpochMs = 1L,
        )

        val sheetXml = workbookSheetXml(CrmExcelExporter.build(listOf(customer)))

        assertTrue(!sheetXml.contains(">Bilinmiyor<"))
        assertTrue(sheetXml.contains("Zayıf Adres Testi"))
    }

    @Test
    fun rawOsmBusinessTypeIsHumanReadableInWorkbook() {
        val customer = CrmCustomer(
            id = "2",
            businessSourceId = "osm:2",
            businessName = "Örnek Market",
            city = "Ağrı",
            district = "Patnos",
            neighborhood = "Yeni Mahalle",
            address = "Test adres",
            latitude = 39.2,
            longitude = 42.8,
            businessType = "supermarket",
            createdAtEpochMs = 1L,
            updatedAtEpochMs = 1L,
        )

        val bytes = CrmExcelExporter.build(listOf(customer))
        var sheetXml = ""
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.name == "xl/worksheets/sheet1.xml") {
                    sheetXml = zip.bufferedReader(Charsets.UTF_8).readText()
                    break
                }
            }
        }

        assertTrue(sheetXml.contains("Süpermarket"))
        assertTrue(!sheetXml.contains(">supermarket<"))
    }

    private fun workbookSheetXml(bytes: ByteArray): String {
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.name == "xl/worksheets/sheet1.xml") {
                    return zip.bufferedReader(Charsets.UTF_8).readText()
                }
            }
        }
        error("Worksheet bulunamadı")
    }
}

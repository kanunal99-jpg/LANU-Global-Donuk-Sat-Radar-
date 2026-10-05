package com.lanu.globaldonuksatisradari.data

import com.lanu.globaldonuksatisradari.crm.CrmCustomer
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

class OfficialRegistryExcelExporterTest {

    @Test
    fun exportsStandardLanuHeadersAndOfficialRegistryFields() {
        val records = listOf(
            OfficialRegistryRecord(
                source = OfficialRegistrySource.ITO,
                registrationNumber = "123456",
                businessName = "Gülüm Market",
                status = "Faal",
                city = "İstanbul",
                district = "Sultanbeyli",
                neighborhood = "Mimar Sinan Mahallesi",
                address = "Basra Caddesi No: 10",
                phone = "+90 535 568 72 38",
                website = "https://example.com",
                importedAtEpochMs = 1L,
                naceCode = "47.11.01",
                taxOrNationalId = "1234567890",
            ),
            OfficialRegistryRecord(
                source = OfficialRegistrySource.MERSIS,
                registrationNumber = "987654",
                businessName = "Örnek Gıda",
                status = "Terkin",
                city = "İstanbul",
                district = "Kadıköy",
                neighborhood = null,
                address = "Moda Caddesi 1",
                phone = null,
                website = null,
                importedAtEpochMs = 1L,
            ),
            OfficialRegistryRecord(
                source = OfficialRegistrySource.TTSG,
                registrationNumber = "247338-0",
                businessName = "BİG MEDYA TEKNOLOJİ ANONİM ŞİRKETİ",
                status = null,
                city = "İstanbul",
                district = "Şişli",
                neighborhood = null,
                address = "Tarihsel İlan Adresi",
                phone = null,
                website = null,
                importedAtEpochMs = 1L,
                mersisNumber = "0123456789012345",
                registryOffice = "İSTANBUL",
                registryEvent = "Değişiklik - Unvan / Adres",
                publicationDate = "10.10.2025",
                registrationDate = "08.10.2025",
                gazetteNumber = "11434",
                gazettePage = "89",
                sourceReference = "ilan-ref-2",
            ),
        )

        val customers = listOf(
            CrmCustomer(
                id = "crm-123456",
                businessSourceId = "overture:place-1",
                businessName = "Gülüm Market Noktası",
                signboardName = "Gülüm Tabela",
                city = "İstanbul",
                district = "Sultanbeyli",
                neighborhood = "Mimar Sinan Mahallesi",
                address = "Güncel Operasyon Adresi 10",
                latitude = 41.123456,
                longitude = 29.654321,
                phone = "05551112233",
                registryNumber = "123456",
                createdAtEpochMs = 1L,
                updatedAtEpochMs = 1L,
            ),
            CrmCustomer(
                id = "crm-247338",
                businessSourceId = "overture:place-2",
                businessName = "Big Medya",
                signboardName = "BIG Medya Tabela",
                city = "İstanbul",
                district = "Şişli",
                neighborhood = "Merkez",
                address = "Bugünkü Operasyon Adresi",
                latitude = 41.0601,
                longitude = 28.9876,
                registryNumber = "247338-0",
                createdAtEpochMs = 1L,
                updatedAtEpochMs = 1L,
            ),
        )

        val bytes = OfficialRegistryExcelExporter.build(records, customers = customers)
        assertTrue(bytes.size > 1000)

        val sheetXml = worksheet(bytes)

        OfficialRegistryExcelExporter.headers.forEach { header ->
            assertTrue("Missing header: $header", sheetXml.contains(header))
        }
        assertTrue(sheetXml.contains("Gülüm Market"))
        assertTrue(sheetXml.contains("Gülüm Market Noktası"))
        assertTrue(sheetXml.contains("Gülüm Tabela"))
        assertTrue(sheetXml.contains("Güncel Operasyon Adresi 10"))
        assertTrue(sheetXml.contains("29.654321"))
        assertTrue(sheetXml.contains("41.123456"))
        assertTrue(sheetXml.contains("maps.google.com/?q=41.123456,29.654321"))
        assertTrue(sheetXml.contains("+90 535 568 72 38"))
        assertTrue(sheetXml.contains("Basra Caddesi No: 10"))
        assertTrue(sheetXml.contains("İTO"))
        assertTrue(sheetXml.contains("123456"))
        assertTrue(sheetXml.contains("1234567890"))
        assertTrue(sheetXml.contains("47.11.01"))
        assertTrue(sheetXml.contains("FAAL"))
        assertTrue(sheetXml.contains("Doğrulandı"))
        assertTrue(sheetXml.contains("Örnek Gıda"))
        assertTrue(sheetXml.contains("MERSİS"))
        assertTrue(sheetXml.contains("AKTİF DEĞİL"))
        assertTrue(sheetXml.contains("TTSG"))
        assertTrue(sheetXml.contains("247338-0"))
        assertTrue(sheetXml.contains("0123456789012345"))
        assertTrue(sheetXml.contains("İSTANBUL"))
        assertTrue(sheetXml.contains("Değişiklik - Unvan / Adres"))
        assertTrue(sheetXml.contains("10.10.2025"))
        assertTrue(sheetXml.contains("08.10.2025"))
        assertTrue(sheetXml.contains("11434"))
        assertTrue(sheetXml.contains("89"))
        assertTrue(sheetXml.contains("ilan-ref-2"))
        assertTrue(sheetXml.contains("BIG Medya Tabela"))
        assertTrue(sheetXml.contains("Bugünkü Operasyon Adresi"))
        assertTrue(sheetXml.contains("Tarihsel İlan Adresi"))
        assertTrue(sheetXml.contains("28.9876"))
        assertTrue(sheetXml.contains("41.0601"))
        assertTrue(sheetXml.contains("Güncel durum doğrulanmadı"))
    }

    @Test
    fun escapesExcelXmlTextSafely() {
        val bytes = OfficialRegistryExcelExporter.build(
            listOf(
                OfficialRegistryRecord(
                    source = OfficialRegistrySource.ESBIS,
                    registrationNumber = "1",
                    businessName = "A & B <Gıda>",
                    status = null,
                    city = "İstanbul",
                    district = "Üsküdar",
                    neighborhood = null,
                    address = "Test & Sokak <1>",
                    phone = null,
                    website = null,
                    importedAtEpochMs = 1L,
                ),
            ),
        )

        val sheetXml = worksheet(bytes)
        assertTrue(sheetXml.contains("A &amp; B &lt;Gıda&gt;"))
        assertTrue(sheetXml.contains("Test &amp; Sokak &lt;1&gt;"))
        assertTrue(sheetXml.contains("Durum belirtilmemiş"))
    }

    private fun worksheet(bytes: ByteArray): String {
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

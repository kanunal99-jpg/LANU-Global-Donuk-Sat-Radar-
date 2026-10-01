package com.lanu.globaldonuksatisradari.data

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
        )

        val bytes = OfficialRegistryExcelExporter.build(records)
        assertTrue(bytes.size > 1000)

        val sheetXml = worksheet(bytes)

        OfficialRegistryExcelExporter.headers.forEach { header ->
            assertTrue("Missing header: $header", sheetXml.contains(header))
        }
        assertTrue(sheetXml.contains("Gülüm Market"))
        assertTrue(sheetXml.contains("+90 535 568 72 38"))
        assertTrue(sheetXml.contains("Basra Caddesi No: 10"))
        assertTrue(sheetXml.contains("İTO"))
        assertTrue(sheetXml.contains("123456"))
        assertTrue(sheetXml.contains("47.11.01"))
        assertTrue(sheetXml.contains("FAAL"))
        assertTrue(sheetXml.contains("Doğrulandı"))
        assertTrue(sheetXml.contains("Örnek Gıda"))
        assertTrue(sheetXml.contains("MERSİS"))
        assertTrue(sheetXml.contains("AKTİF DEĞİL"))
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

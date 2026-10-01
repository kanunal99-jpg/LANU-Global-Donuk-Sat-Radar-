package com.lanu.globaldonuksatisradari.crm

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
            createdAtEpochMs = 1L,
            updatedAtEpochMs = 1L,
        )

        val bytes = CrmExcelExporter.build(listOf(customer))
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
        assertTrue(sheetXml.contains("Restoran"))
        assertTrue(sheetXml.contains("1234567890"))
        assertTrue(sheetXml.contains("05550000000"))
        assertTrue(sheetXml.contains("29.123456"))
        assertTrue(sheetXml.contains("40.987654"))
        assertTrue(sheetXml.contains("maps.google.com/?q=40.987654,29.123456"))
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
}

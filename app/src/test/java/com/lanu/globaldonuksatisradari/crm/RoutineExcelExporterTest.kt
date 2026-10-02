package com.lanu.globaldonuksatisradari.crm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

class RoutineExcelExporterTest {
    private fun customer(index: Int) = CrmCustomer(
        id = "p$index",
        businessSourceId = "test:p$index",
        businessName = "Nokta $index",
        city = "İstanbul",
        district = "Kadıköy",
        neighborhood = "Caferağa",
        address = "Test Sokak No:$index",
        latitude = 40.98 + index * 0.001,
        longitude = 29.02 + index * 0.001,
        contactName = "Kişi $index",
        phone = "0555000${index.toString().padStart(4, '0')}",
        createdAtEpochMs = 1L,
        updatedAtEpochMs = 1L,
    )

    @Test
    fun workbookContainsFourWeeklySheetsAndFiveWeekdaysEach() {
        val plan = MonthlyRoutinePlanner.plan(
            customers = List(40, ::customer),
            startCustomerId = "p0",
        )

        val entries = unzip(RoutineExcelExporter.build(plan))
        val workbook = entries.getValue("xl/workbook.xml")
        assertTrue(workbook.contains("1. Hafta"))
        assertTrue(workbook.contains("2. Hafta"))
        assertTrue(workbook.contains("3. Hafta"))
        assertTrue(workbook.contains("4. Hafta"))

        for (week in 1..4) {
            assertTrue(entries.containsKey("xl/worksheets/sheet$week.xml"))
            val sheet = entries.getValue("xl/worksheets/sheet$week.xml")
            MonthlyRoutinePlanner.WEEKDAYS.forEach { weekday ->
                assertTrue("$weekday eksik", sheet.contains(weekday))
            }
            RoutineExcelExporter.headers.forEach { header ->
                assertTrue("$header eksik", sheet.contains(header))
            }
        }
    }

    @Test
    fun workbookCarriesRequestedCustomerAndDistanceFields() {
        val plan = MonthlyRoutinePlanner.plan(
            customers = listOf(
                customer(0),
                customer(1),
            ),
            startCustomerId = "p0",
        )

        val sheet = unzip(RoutineExcelExporter.build(plan))
            .getValue("xl/worksheets/sheet1.xml")

        assertTrue(sheet.contains("Nokta 0"))
        assertTrue(sheet.contains("Kişi 0"))
        assertTrue(sheet.contains("05550000000"))
        assertTrue(sheet.contains("İstanbul"))
        assertTrue(sheet.contains("Kadıköy"))
        assertTrue(sheet.contains("Test Sokak No:0"))
        assertTrue(sheet.contains("29.02"))
        assertTrue(sheet.contains("40.98"))
        assertTrue(sheet.contains("Ziyaret Aralığı (Gün)"))
        assertTrue(sheet.contains("Frekans Kaynağı"))
        assertTrue(sheet.contains("Önceki Uzaklık"))
        assertTrue(sheet.contains("Kümülatif Uzaklık"))
        assertTrue(sheet.contains("Önceki Süre (dk)"))
        assertTrue(sheet.contains("Kümülatif Süre (dk)"))
        assertTrue(sheet.contains("Otomatik"))
    }

    @Test
    fun emptyDaysAreStillRepresentedInMonthlyWorkbook() {
        val plan = MonthlyRoutinePlanner.plan(listOf(customer(0)))
        val entries = unzip(RoutineExcelExporter.build(plan))

        assertEquals(4, (1..4).count { entries.containsKey("xl/worksheets/sheet$it.xml") })
        assertTrue(entries.getValue("xl/worksheets/sheet4.xml").contains("Planlanan nokta yok."))
    }

    private fun unzip(bytes: ByteArray): Map<String, String> {
        val entries = linkedMapOf<String, String>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                entries[entry.name] = zip.readBytes().toString(Charsets.UTF_8)
                zip.closeEntry()
            }
        }
        return entries
    }
}

package com.lanu.globaldonuksatisradari.crm

import com.lanu.globaldonuksatisradari.data.OfficialRegistryRecord
import com.lanu.globaldonuksatisradari.export.BusinessExcelOfficialIndex
import com.lanu.globaldonuksatisradari.export.BusinessExcelSchema
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object RoutineExcelExporter {
    const val MIME_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

    val headers: List<String> = BusinessExcelSchema.commonHeaders + listOf(
        "Ziyaret Aralığı (Gün)",
        "Frekans Kaynağı",
        "Önceki Uzaklık",
        "Kümülatif Uzaklık",
        "Önceki Süre (dk)",
        "Kümülatif Süre (dk)",
    )

    fun build(
        plan: MonthlyRoutinePlan,
        officialRecords: List<OfficialRegistryRecord> = emptyList(),
    ): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            zip.putXml("[Content_Types].xml", contentTypes())
            zip.putXml("_rels/.rels", rootRelationships())
            zip.putXml("xl/workbook.xml", workbook())
            zip.putXml("xl/_rels/workbook.xml.rels", workbookRelationships())
            zip.putXml("xl/styles.xml", styles())

            for (week in 1..MonthlyRoutinePlanner.WEEKS) {
                val weekDays = plan.days.filter { it.weekNumber == week }
                zip.putXml(
                    "xl/worksheets/sheet$week.xml",
                    worksheet(week, weekDays, plan, officialRecords),
                )
            }
        }
        return output.toByteArray()
    }

    private fun worksheet(
        weekNumber: Int,
        days: List<RoutineDayPlan>,
        plan: MonthlyRoutinePlan,
        officialRecords: List<OfficialRegistryRecord>,
    ): String {
        val officialIndex = BusinessExcelOfficialIndex.from(officialRecords)
        val mergeRefs = mutableListOf<String>()
        var rowNumber = 1
        val rows = buildString {
            days.forEach { day ->
                val dayStart = rowNumber
                val dayTitle = buildString {
                    append(day.weekday)
                    append(" • ")
                    append(day.stops.size)
                    append(" nokta")
                    append(" • ")
                    append("%.2f".format(java.util.Locale.US, day.totalDistanceKm))
                    append(" km • ")
                    append(day.totalEstimatedMinutes)
                    append(" dk")
                }
                append(
                    rowXml(
                        rowNumber,
                        listOf(textCell("A$rowNumber", dayTitle, style = 4)),
                        height = 24,
                    ),
                )
                mergeRefs += "A$dayStart:${columnName(headers.size)}$dayStart"
                rowNumber++

                append(
                    rowXml(
                        rowNumber,
                        headers.mapIndexed { index, value ->
                            textCell(columnName(index + 1) + rowNumber, value, style = 1)
                        },
                        height = 22,
                    ),
                )
                rowNumber++

                if (day.stops.isEmpty()) {
                    append(
                        rowXml(
                            rowNumber,
                            listOf(
                                textCell(
                                    "A$rowNumber",
                                    "Planlanan nokta yok.",
                                    style = 2,
                                ),
                            ),
                        ),
                    )
                    mergeRefs += "A$rowNumber:${columnName(headers.size)}$rowNumber"
                    rowNumber++
                } else {
                    day.stops.forEach { stop ->
                        val customer = stop.customer
                        val commonValues = BusinessExcelSchema.customerValues(
                            customer = customer,
                            officialIndex = officialIndex,
                        )
                        val cells = mutableListOf<String>()
                        commonValues.forEachIndexed { index, value ->
                            val ref = columnName(index + 1) + rowNumber
                            cells += if (index == X_COLUMN_INDEX || index == Y_COLUMN_INDEX) {
                                numericOrTextCell(ref, value, style = 3)
                            } else {
                                textCell(ref, value, style = 2)
                            }
                        }

                        val routeStart = commonValues.size + 1
                        val frequency = plan.frequencyFor(customer.id)
                        cells += numericOrTextCell(
                            columnName(routeStart) + rowNumber,
                            frequency?.intervalDays?.toString().orEmpty(),
                            style = 2,
                        )
                        cells += textCell(
                            columnName(routeStart + 1) + rowNumber,
                            when (frequency?.source) {
                                VisitFrequencySource.AUTO -> "Otomatik"
                                VisitFrequencySource.MANUAL -> "Manuel"
                                null -> ""
                            },
                            style = 2,
                        )
                        cells += numericCell(
                            columnName(routeStart + 2) + rowNumber,
                            stop.distanceFromPreviousKm,
                            style = 5,
                        )
                        cells += numericCell(
                            columnName(routeStart + 3) + rowNumber,
                            stop.cumulativeDistanceKm,
                            style = 5,
                        )
                        cells += numericCell(
                            columnName(routeStart + 4) + rowNumber,
                            stop.estimatedMinutesFromPrevious.toDouble(),
                            style = 6,
                        )
                        cells += numericCell(
                            columnName(routeStart + 5) + rowNumber,
                            stop.cumulativeEstimatedMinutes.toDouble(),
                            style = 6,
                        )
                        append(rowXml(rowNumber, cells))
                        rowNumber++
                    }
                }

                append(rowXml(rowNumber, emptyList(), height = 8))
                rowNumber++
            }
        }

        val merges = if (mergeRefs.isEmpty()) {
            ""
        } else {
            "<mergeCells count=\"${mergeRefs.size}\">" +
                mergeRefs.joinToString("") { "<mergeCell ref=\"$it\"/>" } +
                "</mergeCells>"
        }

        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
  <sheetFormatPr defaultRowHeight="18"/>
  <cols>
    <col min="1" max="4" width="28" customWidth="1"/>
    <col min="5" max="8" width="20" customWidth="1"/>
    <col min="9" max="18" width="20" customWidth="1"/>
    <col min="19" max="20" width="18" customWidth="1"/>
    <col min="21" max="21" width="32" customWidth="1"/>
    <col min="22" max="24" width="18" customWidth="1"/>
    <col min="25" max="26" width="44" customWidth="1"/>
    <col min="27" max="27" width="20" customWidth="1"/>
    <col min="28" max="28" width="32" customWidth="1"/>
    <col min="29" max="30" width="16" customWidth="1"/>
    <col min="31" max="31" width="48" customWidth="1"/>
    <col min="32" max="33" width="24" customWidth="1"/>
    <col min="34" max="39" width="20" customWidth="1"/>
  </cols>
  <sheetData>$rows</sheetData>
  $merges
  <pageMargins left="0.3" right="0.3" top="0.5" bottom="0.5" header="0.2" footer="0.2"/>
  <pageSetup orientation="landscape" fitToWidth="1" fitToHeight="0"/>
  <headerFooter><oddHeader>&amp;C&amp;B$weekNumber. Hafta - LANU Aylık Rutin Planı</oddHeader></headerFooter>
</worksheet>"""
    }

    private const val X_COLUMN_INDEX = 28
    private const val Y_COLUMN_INDEX = 29

    private fun rowXml(
        rowNumber: Int,
        cells: List<String>,
        height: Int? = null,
    ): String {
        val heightAttrs = height?.let { " ht=\"$it\" customHeight=\"1\"" }.orEmpty()
        return "<row r=\"$rowNumber\"$heightAttrs>${cells.joinToString("")}</row>"
    }

    private fun textCell(ref: String, value: String, style: Int): String =
        "<c r=\"$ref\" t=\"inlineStr\" s=\"$style\"><is><t xml:space=\"preserve\">${xml(value)}</t></is></c>"

    private fun numericOrTextCell(ref: String, value: String, style: Int): String {
        val number = value.toDoubleOrNull()
        return if (number == null) {
            textCell(ref, value, style = 2)
        } else {
            numericCell(ref, number, style)
        }
    }

    private fun numericCell(ref: String, value: Double, style: Int): String =
        "<c r=\"$ref\" s=\"$style\"><v>${java.lang.Double.toString(value)}</v></c>"

    private fun columnName(index: Int): String {
        var value = index
        val result = StringBuilder()
        while (value > 0) {
            value--
            result.append(('A'.code + value % 26).toChar())
            value /= 26
        }
        return result.reverse().toString()
    }

    private fun xml(value: String): String = buildString(value.length) {
        value.forEach { char ->
            when (char) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                else -> append(char)
            }
        }
    }

    private fun ZipOutputStream.putXml(path: String, content: String) {
        putNextEntry(ZipEntry(path))
        write(content.toByteArray(Charsets.UTF_8))
        closeEntry()
    }

    private fun contentTypes() = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
  <Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
  <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
  <Override PartName="/xl/worksheets/sheet2.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
  <Override PartName="/xl/worksheets/sheet3.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
  <Override PartName="/xl/worksheets/sheet4.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
</Types>"""

    private fun rootRelationships() = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
</Relationships>"""

    private fun workbook() = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"
 xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
  <sheets>
    <sheet name="1. Hafta" sheetId="1" r:id="rId1"/>
    <sheet name="2. Hafta" sheetId="2" r:id="rId2"/>
    <sheet name="3. Hafta" sheetId="3" r:id="rId3"/>
    <sheet name="4. Hafta" sheetId="4" r:id="rId4"/>
  </sheets>
</workbook>"""

    private fun workbookRelationships() = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
  <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet2.xml"/>
  <Relationship Id="rId3" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet3.xml"/>
  <Relationship Id="rId4" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet4.xml"/>
  <Relationship Id="rId5" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
</Relationships>"""

    private fun styles() = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
  <numFmts count="3">
    <numFmt numFmtId="164" formatCode="0.000000"/>
    <numFmt numFmtId="165" formatCode="0.00 &quot;km&quot;"/>
    <numFmt numFmtId="166" formatCode="0 &quot;dk&quot;"/>
  </numFmts>
  <fonts count="3">
    <font><sz val="11"/><name val="Calibri"/></font>
    <font><b/><color rgb="FFFFFFFF"/><sz val="11"/><name val="Calibri"/></font>
    <font><b/><color rgb="FFFFFFFF"/><sz val="12"/><name val="Calibri"/></font>
  </fonts>
  <fills count="4">
    <fill><patternFill patternType="none"/></fill>
    <fill><patternFill patternType="gray125"/></fill>
    <fill><patternFill patternType="solid"><fgColor rgb="FF1F4E78"/><bgColor indexed="64"/></patternFill></fill>
    <fill><patternFill patternType="solid"><fgColor rgb="FF548235"/><bgColor indexed="64"/></patternFill></fill>
  </fills>
  <borders count="2">
    <border><left/><right/><top/><bottom/><diagonal/></border>
    <border>
      <left style="thin"><color rgb="FFD9E2F3"/></left>
      <right style="thin"><color rgb="FFD9E2F3"/></right>
      <top style="thin"><color rgb="FFD9E2F3"/></top>
      <bottom style="thin"><color rgb="FFD9E2F3"/></bottom>
      <diagonal/>
    </border>
  </borders>
  <cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>
  <cellXfs count="7">
    <xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>
    <xf numFmtId="0" fontId="1" fillId="2" borderId="1" xfId="0" applyAlignment="1"><alignment horizontal="center" vertical="center" wrapText="1"/></xf>
    <xf numFmtId="0" fontId="0" fillId="0" borderId="1" xfId="0" applyAlignment="1"><alignment vertical="top" wrapText="1"/></xf>
    <xf numFmtId="164" fontId="0" fillId="0" borderId="1" xfId="0" applyNumberFormat="1"/>
    <xf numFmtId="0" fontId="2" fillId="3" borderId="1" xfId="0" applyAlignment="1"><alignment horizontal="left" vertical="center"/></xf>
    <xf numFmtId="165" fontId="0" fillId="0" borderId="1" xfId="0" applyNumberFormat="1"/>
    <xf numFmtId="166" fontId="0" fillId="0" borderId="1" xfId="0" applyNumberFormat="1"/>
  </cellXfs>
  <cellStyles count="1"><cellStyle name="Normal" xfId="0" builtinId="0"/></cellStyles>
</styleSheet>"""
}

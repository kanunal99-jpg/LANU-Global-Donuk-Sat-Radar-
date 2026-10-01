package com.lanu.globaldonuksatisradari.crm

import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object RoutineExcelExporter {
    const val MIME_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

    val headers: List<String> = listOf(
        "Nokta Adı",
        "Ad Soyad",
        "Telefon No",
        "İl",
        "İlçe",
        "Açık Adres",
        "X",
        "Y",
        "Kümülatif Uzaklık",
    )

    fun build(plan: MonthlyRoutinePlan): ByteArray {
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
                    worksheet(week, weekDays),
                )
            }
        }
        return output.toByteArray()
    }

    private fun worksheet(
        weekNumber: Int,
        days: List<RoutineDayPlan>,
    ): String {
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
                    append(" km")
                }
                append(
                    rowXml(
                        rowNumber,
                        listOf(textCell("A$rowNumber", dayTitle, style = 4)),
                        height = 24,
                    ),
                )
                mergeRefs += "A$dayStart:I$dayStart"
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
                    mergeRefs += "A$rowNumber:I$rowNumber"
                    rowNumber++
                } else {
                    day.stops.forEach { stop ->
                        val customer = stop.customer
                        val values = listOf(
                            customer.businessName,
                            customer.contactName.orEmpty(),
                            customer.phone.orEmpty(),
                            customer.city,
                            customer.district,
                            customer.address.orEmpty(),
                        )
                        val cells = mutableListOf<String>()
                        values.forEachIndexed { index, value ->
                            cells += textCell(
                                columnName(index + 1) + rowNumber,
                                value,
                                style = 2,
                            )
                        }
                        cells += numericOrTextCell(
                            "G$rowNumber",
                            customer.longitude?.toString().orEmpty(),
                            style = 3,
                        )
                        cells += numericOrTextCell(
                            "H$rowNumber",
                            customer.latitude?.toString().orEmpty(),
                            style = 3,
                        )
                        cells += numericCell(
                            "I$rowNumber",
                            stop.cumulativeDistanceKm,
                            style = 5,
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
    <col min="1" max="1" width="30" customWidth="1"/>
    <col min="2" max="2" width="24" customWidth="1"/>
    <col min="3" max="3" width="20" customWidth="1"/>
    <col min="4" max="5" width="18" customWidth="1"/>
    <col min="6" max="6" width="46" customWidth="1"/>
    <col min="7" max="8" width="16" customWidth="1"/>
    <col min="9" max="9" width="20" customWidth="1"/>
  </cols>
  <sheetData>$rows</sheetData>
  $merges
  <pageMargins left="0.3" right="0.3" top="0.5" bottom="0.5" header="0.2" footer="0.2"/>
  <pageSetup orientation="landscape" fitToWidth="1" fitToHeight="0"/>
  <headerFooter><oddHeader>&amp;C&amp;B$weekNumber. Hafta - LANU Aylık Rutin Planı</oddHeader></headerFooter>
</worksheet>"""
    }

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
  <numFmts count="2">
    <numFmt numFmtId="164" formatCode="0.000000"/>
    <numFmt numFmtId="165" formatCode="0.00 &quot;km&quot;"/>
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
  <cellXfs count="6">
    <xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>
    <xf numFmtId="0" fontId="1" fillId="2" borderId="1" xfId="0" applyAlignment="1"><alignment horizontal="center" vertical="center" wrapText="1"/></xf>
    <xf numFmtId="0" fontId="0" fillId="0" borderId="1" xfId="0" applyAlignment="1"><alignment vertical="top" wrapText="1"/></xf>
    <xf numFmtId="164" fontId="0" fillId="0" borderId="1" xfId="0" applyNumberFormat="1"/>
    <xf numFmtId="0" fontId="2" fillId="3" borderId="1" xfId="0" applyAlignment="1"><alignment horizontal="left" vertical="center"/></xf>
    <xf numFmtId="165" fontId="0" fillId="0" borderId="1" xfId="0" applyNumberFormat="1"/>
  </cellXfs>
  <cellStyles count="1"><cellStyle name="Normal" xfId="0" builtinId="0"/></cellStyles>
</styleSheet>"""
}

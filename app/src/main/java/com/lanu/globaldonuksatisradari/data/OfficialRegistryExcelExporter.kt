package com.lanu.globaldonuksatisradari.data

import com.lanu.globaldonuksatisradari.crm.CrmCustomer
import com.lanu.globaldonuksatisradari.export.BusinessExcelContextIndex
import com.lanu.globaldonuksatisradari.export.BusinessExcelSchema
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object OfficialRegistryExcelExporter {
    const val MIME_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

    val headers: List<String> = BusinessExcelSchema.commonHeaders

    fun build(
        records: List<OfficialRegistryRecord>,
        customers: List<CrmCustomer> = emptyList(),
        businesses: List<VerifiedBusiness> = emptyList(),
    ): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            zip.putXml("[Content_Types].xml", contentTypes())
            zip.putXml("_rels/.rels", rootRelationships())
            zip.putXml("xl/workbook.xml", workbook())
            zip.putXml("xl/_rels/workbook.xml.rels", workbookRelationships())
            zip.putXml("xl/styles.xml", styles())
            zip.putXml("xl/worksheets/sheet1.xml", worksheet(records, customers, businesses))
        }
        return output.toByteArray()
    }

    private fun worksheet(
        records: List<OfficialRegistryRecord>,
        customers: List<CrmCustomer>,
        businesses: List<VerifiedBusiness>,
    ): String {
        val contextIndex = BusinessExcelContextIndex.from(customers, businesses)
        val rows = buildString {
            append(
                rowXml(
                    1,
                    headers.mapIndexed { index, value ->
                        textCell(columnName(index + 1) + "1", value, style = 1)
                    },
                ),
            )

            records.forEachIndexed { index, record ->
                val rowNumber = index + 2
                val values = BusinessExcelSchema.registryValues(
                    record = record,
                    customer = contextIndex.customerFor(record),
                    business = contextIndex.businessFor(record),
                )
                append(
                    rowXml(
                        rowNumber,
                        values.mapIndexed { cellIndex, value ->
                            textCell(columnName(cellIndex + 1) + rowNumber, value, style = 2)
                        },
                    ),
                )
            }
        }

        val lastRow = (records.size + 1).coerceAtLeast(1)
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
  <sheetViews>
    <sheetView workbookViewId="0">
      <pane ySplit="1" topLeftCell="A2" activePane="bottomLeft" state="frozen"/>
    </sheetView>
  </sheetViews>
  <sheetFormatPr defaultRowHeight="18"/>
  <cols>
    <col min="1" max="4" width="28" customWidth="1"/>
    <col min="5" max="8" width="20" customWidth="1"/>
    <col min="9" max="18" width="20" customWidth="1"/>
    <col min="19" max="20" width="18" customWidth="1"/>
    <col min="21" max="21" width="32" customWidth="1"/>
    <col min="22" max="24" width="18" customWidth="1"/>
    <col min="25" max="26" width="44" customWidth="1"/>
    <col min="27" max="28" width="16" customWidth="1"/>
    <col min="29" max="29" width="48" customWidth="1"/>
    <col min="30" max="31" width="24" customWidth="1"/>
  </cols>
  <sheetData>${rows}</sheetData>
  <autoFilter ref="A1:${columnName(headers.size)}${lastRow}"/>
</worksheet>"""
    }

    private fun rowXml(rowNumber: Int, cells: List<String>): String =
        "<row r=\"$rowNumber\">${cells.joinToString("")}</row>"

    private fun textCell(ref: String, value: String, style: Int): String =
        "<c r=\"$ref\" t=\"inlineStr\" s=\"$style\"><is><t xml:space=\"preserve\">${xml(value)}</t></is></c>"

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
  <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
  <Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
</Types>"""

    private fun rootRelationships() = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
</Relationships>"""

    private fun workbook() = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"
 xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
  <sheets><sheet name="Resmî Sicil Noktaları" sheetId="1" r:id="rId1"/></sheets>
</workbook>"""

    private fun workbookRelationships() = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
  <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
</Relationships>"""

    private fun styles() = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
  <fonts count="2">
    <font><sz val="11"/><name val="Calibri"/></font>
    <font><b/><color rgb="FFFFFFFF"/><sz val="11"/><name val="Calibri"/></font>
  </fonts>
  <fills count="3">
    <fill><patternFill patternType="none"/></fill>
    <fill><patternFill patternType="gray125"/></fill>
    <fill><patternFill patternType="solid"><fgColor rgb="FF1F4E78"/><bgColor indexed="64"/></patternFill></fill>
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
  <cellXfs count="3">
    <xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>
    <xf numFmtId="0" fontId="1" fillId="2" borderId="1" xfId="0" applyAlignment="1"><alignment horizontal="center" vertical="center" wrapText="1"/></xf>
    <xf numFmtId="0" fontId="0" fillId="0" borderId="1" xfId="0" applyAlignment="1"><alignment vertical="top" wrapText="1"/></xf>
  </cellXfs>
  <cellStyles count="1"><cellStyle name="Normal" xfId="0" builtinId="0"/></cellStyles>
</styleSheet>"""
}

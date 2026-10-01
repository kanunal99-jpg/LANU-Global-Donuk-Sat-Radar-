package com.lanu.globaldonuksatisradari.crm

import com.lanu.globaldonuksatisradari.data.BusinessCategoryLabels
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object CrmExcelExporter {
    const val MIME_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

    val headers: List<String> = listOf(
        "Ad Soyad",
        "Nokta Adı",
        "İşletme Türü",
        "TC/Vergi No",
        "Telefon No",
        "İl",
        "İlçe",
        "Mahalle",
        "Açık Adres",
        "X",
        "Y",
        "Konum Bilgileri",
    )

    fun build(customers: List<CrmCustomer>): ByteArray {
        val sanitizedCustomers = customers.map(CrmLocationSanitizer::sanitizeWithoutNetwork)
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            zip.putXml("[Content_Types].xml", contentTypes())
            zip.putXml("_rels/.rels", rootRelationships())
            zip.putXml("xl/workbook.xml", workbook())
            zip.putXml("xl/_rels/workbook.xml.rels", workbookRelationships())
            zip.putXml("xl/styles.xml", styles())
            zip.putXml("xl/worksheets/sheet1.xml", worksheet(sanitizedCustomers))
        }
        return output.toByteArray()
    }

    private fun worksheet(customers: List<CrmCustomer>): String {
        val rows = buildString {
            append(rowXml(1, headers.mapIndexed { index, value ->
                textCell(columnName(index + 1) + "1", value, style = 1)
            }))

            customers.forEachIndexed { index, customer ->
                val rowNumber = index + 2
                val mapLink = if (customer.latitude != null && customer.longitude != null) {
                    "https://maps.google.com/?q=${customer.latitude},${customer.longitude}"
                } else {
                    ""
                }
                val values = listOf(
                    customer.contactName.orEmpty(),
                    customer.businessName,
                    BusinessCategoryLabels.displayName(customer.businessType).orEmpty(),
                    customer.taxOrNationalId.orEmpty(),
                    customer.phone.orEmpty(),
                    customer.city,
                    customer.district,
                    customer.neighborhood.orEmpty(),
                    customer.address.orEmpty(),
                    customer.longitude?.toString().orEmpty(),
                    customer.latitude?.toString().orEmpty(),
                    mapLink,
                )
                append(rowXml(rowNumber, values.mapIndexed { cellIndex, value ->
                    val ref = columnName(cellIndex + 1) + rowNumber
                    if (cellIndex == 9 || cellIndex == 10) {
                        numericOrTextCell(ref, value)
                    } else {
                        textCell(ref, value, style = 2)
                    }
                }))
            }
        }

        val lastRow = (customers.size + 1).coerceAtLeast(1)
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
  <sheetViews>
    <sheetView workbookViewId="0">
      <pane ySplit="1" topLeftCell="A2" activePane="bottomLeft" state="frozen"/>
    </sheetView>
  </sheetViews>
  <sheetFormatPr defaultRowHeight="18"/>
  <cols>
    <col min="1" max="1" width="22" customWidth="1"/>
    <col min="2" max="2" width="30" customWidth="1"/>
    <col min="3" max="3" width="22" customWidth="1"/>
    <col min="4" max="5" width="18" customWidth="1"/>
    <col min="6" max="8" width="18" customWidth="1"/>
    <col min="9" max="9" width="42" customWidth="1"/>
    <col min="10" max="11" width="16" customWidth="1"/>
    <col min="12" max="12" width="48" customWidth="1"/>
  </cols>
  <sheetData>$rows</sheetData>
  <autoFilter ref="A1:L$lastRow"/>
</worksheet>"""
    }

    private fun rowXml(rowNumber: Int, cells: List<String>): String =
        "<row r=\"$rowNumber\">${cells.joinToString("")}</row>"

    private fun textCell(ref: String, value: String, style: Int): String =
        "<c r=\"$ref\" t=\"inlineStr\" s=\"$style\"><is><t xml:space=\"preserve\">${xml(value)}</t></is></c>"

    private fun numericOrTextCell(ref: String, value: String): String {
        val number = value.toDoubleOrNull()
        return if (number == null) {
            textCell(ref, value, style = 2)
        } else {
            "<c r=\"$ref\" s=\"3\"><v>$number</v></c>"
        }
    }

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
  <sheets><sheet name="CRM Noktaları" sheetId="1" r:id="rId1"/></sheets>
</workbook>"""

    private fun workbookRelationships() = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
  <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
</Relationships>"""

    private fun styles() = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
  <numFmts count="1"><numFmt numFmtId="164" formatCode="0.000000"/></numFmts>
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
  <cellXfs count="4">
    <xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>
    <xf numFmtId="0" fontId="1" fillId="2" borderId="1" xfId="0" applyAlignment="1"><alignment horizontal="center" vertical="center" wrapText="1"/></xf>
    <xf numFmtId="0" fontId="0" fillId="0" borderId="1" xfId="0" applyAlignment="1"><alignment vertical="top" wrapText="1"/></xf>
    <xf numFmtId="164" fontId="0" fillId="0" borderId="1" xfId="0" applyNumberFormat="1"/>
  </cellXfs>
  <cellStyles count="1"><cellStyle name="Normal" xfId="0" builtinId="0"/></cellStyles>
</styleSheet>"""
}

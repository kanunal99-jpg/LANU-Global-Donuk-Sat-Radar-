package com.lanu.globaldonuksatisradari.export

import com.lanu.globaldonuksatisradari.crm.CrmExcelExporter
import com.lanu.globaldonuksatisradari.crm.RoutineExcelExporter
import com.lanu.globaldonuksatisradari.data.OfficialRegistryExcelExporter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BusinessExcelSchemaTest {
    @Test
    fun everyExcelExportUsesTheSameFullBusinessBaseColumns() {
        assertEquals(BusinessExcelSchema.commonHeaders, CrmExcelExporter.headers)
        assertEquals(BusinessExcelSchema.commonHeaders, OfficialRegistryExcelExporter.headers)
        assertEquals(
            BusinessExcelSchema.commonHeaders,
            RoutineExcelExporter.headers.take(BusinessExcelSchema.commonHeaders.size),
        )
        assertEquals(
            BusinessExcelSchema.commonHeaders.size + 6,
            RoutineExcelExporter.headers.size,
        )
        listOf(
            "Tabela Adı",
            "Ticari Unvan",
            "MERSİS No",
            "Sicil Müdürlüğü",
            "Sicil Olayı",
            "Yayın Tarihi",
            "Tescil Tarihi",
            "NACE",
            "TC/Vergi No",
            "Mahalle",
            "Açık Adres",
            "X",
            "Y",
            "Konum Bilgileri",
        ).forEach { required ->
            assertTrue("Ortak Excel alanı eksik: $required", required in BusinessExcelSchema.commonHeaders)
        }
    }
}

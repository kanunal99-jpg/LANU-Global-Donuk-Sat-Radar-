package com.lanu.globaldonuksatisradari.export

import com.lanu.globaldonuksatisradari.crm.CrmCustomer
import com.lanu.globaldonuksatisradari.crm.CrmExcelExporter
import com.lanu.globaldonuksatisradari.crm.RoutineExcelExporter
import com.lanu.globaldonuksatisradari.data.OfficialRegistryExcelExporter
import com.lanu.globaldonuksatisradari.data.OfficialRegistryRecord
import com.lanu.globaldonuksatisradari.data.OfficialRegistrySource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
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
            "Sicil Telefonu",
            "Sicil Web Sitesi",
            "X",
            "Y",
            "Konum Bilgileri",
        ).forEach { required ->
            assertTrue("Ortak Excel alanı eksik: $required", required in BusinessExcelSchema.commonHeaders)
        }
    }


    @Test
    fun registryNumberAndTaxIdNeverShareTheSameContextIndex() {
        val registryCustomer = CrmCustomer(
            id = "registry",
            businessSourceId = "radar:registry",
            businessName = "Sicil Eşleşmesi",
            city = "İstanbul",
            district = "Kadıköy",
            neighborhood = null,
            registryNumber = "1111111111",
            createdAtEpochMs = 1L,
            updatedAtEpochMs = 1L,
        )
        val taxCustomer = CrmCustomer(
            id = "tax",
            businessSourceId = "radar:tax",
            businessName = "Vergi Eşleşmesi",
            city = "İstanbul",
            district = "Kadıköy",
            neighborhood = null,
            taxOrNationalId = "1111111111",
            createdAtEpochMs = 1L,
            updatedAtEpochMs = 1L,
        )
        val index = BusinessExcelContextIndex.from(
            customers = listOf(registryCustomer, taxCustomer),
            businesses = emptyList(),
        )

        val registryRecord = OfficialRegistryRecord(
            source = OfficialRegistrySource.ITO,
            registrationNumber = "1111111111",
            businessName = "Farklı Ünvan",
            status = "Faal",
            city = "İstanbul",
            district = "Kadıköy",
            neighborhood = null,
            address = null,
            phone = null,
            website = null,
            importedAtEpochMs = 1L,
        )
        assertSame(registryCustomer, index.customerFor(registryRecord))

        val taxRecord = registryRecord.copy(
            registrationNumber = null,
            taxOrNationalId = "1111111111",
        )
        assertSame(taxCustomer, index.customerFor(taxRecord))
    }
}

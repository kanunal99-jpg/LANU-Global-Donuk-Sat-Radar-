package com.lanu.globaldonuksatisradari

import com.lanu.globaldonuksatisradari.crm.CrmCustomer
import com.lanu.globaldonuksatisradari.crm.CrmStage
import com.lanu.globaldonuksatisradari.crm.DataQuality
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CrmMapScreenTest {
    @Test
    fun customerWithCoordinatesBecomesMapPointWithStageAndSignboard() {
        val customer = CrmCustomer(
            id = "map-1",
            businessSourceId = "manual:map-1",
            businessName = "Resmî Ticari Ad",
            signboardName = "Tabela Adı",
            city = "İstanbul",
            district = "Kadıköy",
            neighborhood = "Caferağa",
            address = "Moda Cad. 1",
            latitude = 40.987,
            longitude = 29.028,
            dataQuality = DataQuality.USER_ENTERED,
            stage = CrmStage.PROPOSAL,
            createdAtEpochMs = 1L,
            updatedAtEpochMs = 2L,
        )

        assertTrue(hasValidCoordinates(customer))
        val mapBusiness = crmCustomerAsMapBusiness(customer)

        assertEquals("Tabela Adı", mapBusiness.name)
        assertEquals(40.987, mapBusiness.latitude)
        assertEquals(29.028, mapBusiness.longitude)
        assertEquals("CRM • Teklif", mapBusiness.category)
        assertEquals("local-crm-map", mapBusiness.source.id)
    }

    @Test
    fun invalidCoordinatesAreRejectedFromMap() {
        val customer = CrmCustomer(
            id = "map-2",
            businessSourceId = "manual:map-2",
            businessName = "Koordinatsız",
            city = "İstanbul",
            district = "Şişli",
            neighborhood = null,
            latitude = 95.0,
            longitude = 29.0,
            createdAtEpochMs = 1L,
            updatedAtEpochMs = 1L,
        )

        assertFalse(hasValidCoordinates(customer))
    }
}

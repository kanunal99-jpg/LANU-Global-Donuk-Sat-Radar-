package com.lanu.globaldonuksatisradari

import com.lanu.globaldonuksatisradari.crm.CrmCustomer
import com.lanu.globaldonuksatisradari.crm.CrmStage
import kotlin.test.Test
import kotlin.test.assertEquals

class CrmRegionScopeTest {
    private fun customer(id: String, city: String, district: String) = CrmCustomer(
        id = id,
        businessSourceId = "source:$id",
        businessName = id,
        city = city,
        district = district,
        neighborhood = null,
        stage = CrmStage.PROSPECT,
        createdAtEpochMs = 1L,
        updatedAtEpochMs = 1L,
    )

    @Test
    fun selectedCityNeverLeaksCustomersFromAnotherCity() {
        val customers = listOf(
            customer("adana", "Adana", "Seyhan"),
            customer("istanbul", "İstanbul", "Sultanbeyli"),
        )

        val scoped = scopeCrmCustomers(customers, "Adana", "Tümü")

        assertEquals(listOf("adana"), scoped.map { it.id })
    }

    @Test
    fun staleEuropeanDistrictIsRejectedForAnatolianScope() {
        val anatolian = City(
            name = "İstanbul",
            districts = IstanbulDistricts.ANATOLIAN,
            label = "İstanbul Anadolu",
        )
        val european = City(
            name = "İstanbul",
            districts = IstanbulDistricts.EUROPEAN,
            label = "İstanbul Avrupa",
        )

        assertEquals("Tümü", validInitialDistrict(anatolian, "Şişli"))
        assertEquals("Kadıköy", validInitialDistrict(anatolian, "kadıköy"))
        assertEquals("Tümü", validInitialDistrict(european, "Pendik"))
        assertEquals("Bakırköy", validInitialDistrict(european, "Bakırköy"))
    }

    @Test
    fun districtScopeIsCaseInsensitiveAndConsistent() {
        val customers = listOf(
            customer("one", "İstanbul", "Sultanbeyli"),
            customer("two", "İSTANBUL", "Kadıköy"),
        )

        val scoped = scopeCrmCustomers(customers, "istanbul", "sultanbeyli")

        assertEquals(listOf("one"), scoped.map { it.id })
    }
}

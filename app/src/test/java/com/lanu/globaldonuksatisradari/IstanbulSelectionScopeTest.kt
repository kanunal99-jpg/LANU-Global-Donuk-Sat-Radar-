package com.lanu.globaldonuksatisradari

import com.lanu.globaldonuksatisradari.crm.CrmCustomer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IstanbulSelectionScopeTest {
    private fun customer(id: String, district: String) = CrmCustomer(
        id = id,
        businessSourceId = "test:$id",
        businessName = "Nokta $id",
        city = "İstanbul",
        district = district,
        neighborhood = null,
        createdAtEpochMs = 1L,
        updatedAtEpochMs = 1L,
    )

    @Test
    fun allDistrictsRespectsSelectedIstanbulSide() {
        val customers = listOf(
            customer("kadikoy", "Kadıköy"),
            customer("pendik", "Pendik"),
            customer("sisli", "Şişli"),
            customer("bakirkoy", "Bakırköy"),
        )
        val anatolian = City("İstanbul", IstanbulDistricts.ANATOLIAN, "İstanbul Anadolu")
        val european = City("İstanbul", IstanbulDistricts.EUROPEAN, "İstanbul Avrupa")

        assertEquals(
            setOf("Kadıköy", "Pendik"),
            scopeCrmCustomersForSelection(customers, anatolian, "Tümü")
                .map { it.district }
                .toSet(),
        )
        assertEquals(
            setOf("Şişli", "Bakırköy"),
            scopeCrmCustomersForSelection(customers, european, "Tümü")
                .map { it.district }
                .toSet(),
        )
    }

    @Test
    fun radarDistrictGateSeparatesSidesAndSpecificDistrictStillWins() {
        val anatolian = City("İstanbul", IstanbulDistricts.ANATOLIAN, "İstanbul Anadolu")
        val european = City("İstanbul", IstanbulDistricts.EUROPEAN, "İstanbul Avrupa")

        assertTrue(districtMatchesSelection(anatolian, "Tümü", "Kadıköy"))
        assertFalse(districtMatchesSelection(anatolian, "Tümü", "Şişli"))
        assertTrue(districtMatchesSelection(european, "Tümü", "Şişli"))
        assertFalse(districtMatchesSelection(european, "Tümü", "Kadıköy"))
        assertTrue(districtMatchesSelection(anatolian, "Pendik", "Pendik"))
        assertFalse(districtMatchesSelection(anatolian, "Pendik", "Kadıköy"))
    }
}

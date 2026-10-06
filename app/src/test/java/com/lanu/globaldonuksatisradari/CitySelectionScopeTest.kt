package com.lanu.globaldonuksatisradari

import com.lanu.globaldonuksatisradari.crm.CrmCustomer
import com.lanu.globaldonuksatisradari.data.DataSourceDescriptor
import com.lanu.globaldonuksatisradari.data.VerifiedBusiness
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CitySelectionScopeTest {
    private val source = DataSourceDescriptor(
        id = "scope-test",
        name = "Scope Test",
        publisher = "LANU",
        licenseOrTerms = "Test",
        sourceUrl = "https://example.com/",
        lastVerifiedAtEpochMs = 1L,
    )

    private fun customer(id: String, district: String) = CrmCustomer(
        id = id,
        businessSourceId = id,
        businessName = id,
        city = "İstanbul",
        district = district,
        neighborhood = null,
        createdAtEpochMs = 1L,
        updatedAtEpochMs = 1L,
    )

    @Test
    fun anatolianAndEuropeanSelectionsNeverMixWhenDistrictIsAll() {
        val customers = listOf(
            customer("kadikoy", "Kadıköy"),
            customer("sisli", "Şişli"),
        )
        val anatolian = City("İstanbul", IstanbulDistricts.ANATOLIAN, "İstanbul Anadolu")
        val european = City("İstanbul", IstanbulDistricts.EUROPEAN, "İstanbul Avrupa")

        val anatolianIds = scopeCrmCustomersForCitySelection(customers, anatolian, "Tümü").map { it.id }
        val europeanIds = scopeCrmCustomersForCitySelection(customers, european, "Tümü").map { it.id }

        assertTrue("kadikoy" in anatolianIds)
        assertFalse("sisli" in anatolianIds)
        assertTrue("sisli" in europeanIds)
        assertFalse("kadikoy" in europeanIds)
    }

    @Test
    fun staleCrossSideDistrictIsRejectedForRadarAndCrm() {
        val anatolian = City("İstanbul", IstanbulDistricts.ANATOLIAN, "İstanbul Anadolu")
        val sisliCustomer = customer("sisli", "Şişli")
        val sisliBusiness = VerifiedBusiness(
            id = "sisli-business",
            name = "Şişli Test",
            city = "İstanbul",
            district = "Şişli",
            neighborhood = null,
            source = source,
            verifiedAtEpochMs = 1L,
        )

        assertTrue(
            scopeCrmCustomersForCitySelection(
                listOf(sisliCustomer),
                anatolian,
                "Şişli",
            ).isEmpty(),
        )
        assertFalse(businessMatchesCitySelection(sisliBusiness, anatolian, "Şişli"))
    }
}

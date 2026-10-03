package com.lanu.globaldonuksatisradari.data

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BusinessEntityEligibilityTest {
    private val source = DataSourceDescriptor(
        id = "test",
        name = "Test",
        publisher = "Test",
        licenseOrTerms = "https://example.com/terms",
        sourceUrl = "https://example.com/source",
        lastVerifiedAtEpochMs = 1L,
    )

    private fun business(category: String) = VerifiedBusiness(
        id = category,
        name = category,
        city = "İstanbul",
        district = "Sultanbeyli",
        neighborhood = null,
        source = source,
        verifiedAtEpochMs = 1L,
        category = category,
    )

    @Test
    fun broadInventoryRemovesMapInfrastructureButKeepsSellableCustomerPoints() {
        assertFalse(BusinessEntityEligibility.keepForBusinessInventory(business("park")))
        assertFalse(BusinessEntityEligibility.keepForBusinessInventory(business("playground")))
        assertFalse(BusinessEntityEligibility.keepForBusinessInventory(business("parking")))
        assertTrue(BusinessEntityEligibility.keepForBusinessInventory(business("fast_food_restaurant")))
        assertTrue(BusinessEntityEligibility.keepForBusinessInventory(business("manufacturer")))
        assertTrue(BusinessEntityEligibility.keepForBusinessInventory(business("hospital")))
        assertTrue(BusinessEntityEligibility.keepForBusinessInventory(business("school")))
    }
}

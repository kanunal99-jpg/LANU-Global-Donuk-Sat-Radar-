package com.lanu.globaldonuksatisradari

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SalesOpportunityTest {
    @Test
    fun restaurantGuidanceFocusesOnKitchenAndService() {
        val result = buildSalesOpportunity("Restaurant")

        assertTrue(result.focus.contains("Mutfak"))
        assertEquals(3, result.discoveryQuestions.size)
    }

    @Test
    fun unknownCategoryDoesNotInventSalesValues() {
        val result = buildSalesOpportunity(null)

        assertTrue(result.conversation.contains("varsayma"))
        assertTrue(result.sourceUrl.startsWith("https://"))
        assertTrue(result.verifiedClaims.isNotEmpty())
    }

    @Test
    fun categoryGuidanceIsStillGeneratedForCafe() {
        val result = buildSalesOpportunity("Kafe")

        assertTrue(result.focus.contains("sıcak yemek", ignoreCase = true))
        assertTrue(result.discoveryQuestions.isNotEmpty())
    }

    @Test
    fun verifiedClaimsAreGlobalDonukSourceBased() {
        val result = buildSalesOpportunity("Restaurant")

        assertEquals("https://globaldonukgida.com/", result.sourceUrl)
        assertTrue(result.verifiedClaims.any { it.contains("standart porsiyon", ignoreCase = true) })
        assertTrue(result.verifiedClaims.any { it.contains("profesyonel mutfak", ignoreCase = true) })
    }
}

package com.lanu.globaldonuksatisradari

import org.junit.Assert.assertTrue
import org.junit.Test

class LocalSalesAssistantTest {
    private val context = SalesAiContext(
        city = "İstanbul",
        district = "Kadıköy",
        crmCount = 42,
        prospectCount = 18,
        activeCustomerCount = 7,
        radarResultCount = 25,
        newBusinessCount = 3,
        sampleBusinessNames = listOf("Örnek Kafe", "Test Restoran"),
    )

    @Test
    fun summaryUsesOnlyProvidedRadarAndCrmContext() {
        val answer = LocalSalesAssistant.answer("Özet çıkar", context)

        assertTrue(answer.contains("İstanbul / Kadıköy"))
        assertTrue(answer.contains("Radar sonucu: 25"))
        assertTrue(answer.contains("Son taramadan beri yeni: 3"))
        assertTrue(answer.contains("CRM toplam: 42"))
    }

    @Test
    fun visitPlanPrioritizesNewBusinesses() {
        val answer = LocalSalesAssistant.answer("Bugünkü ziyaret planı", context)

        assertTrue(answer.contains("3 yeni işletmeyi"))
        assertTrue(answer.contains("Örnek Kafe"))
    }

    @Test
    fun messageDraftDoesNotInventPersonalOrTaxData() {
        val answer = LocalSalesAssistant.answer("WhatsApp mesajı hazırla", context)

        assertTrue(answer.contains("LANU Global Donuk Gıda"))
        assertTrue(!answer.contains("TC"))
        assertTrue(!answer.contains("Vergi No"))
    }
}

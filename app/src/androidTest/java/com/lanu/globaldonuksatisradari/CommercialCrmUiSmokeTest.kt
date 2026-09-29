package com.lanu.globaldonuksatisradari

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lanu.globaldonuksatisradari.crm.LanuCrmDatabase
import com.lanu.globaldonuksatisradari.crm.LocalCrmRepository
import com.lanu.globaldonuksatisradari.data.DataSourceDescriptor
import com.lanu.globaldonuksatisradari.data.VerifiedBusiness
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalTestApi::class)
class CommercialCrmUiSmokeTest {
    @JvmField
    @org.junit.Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private fun waitForText(text: String) {
        composeRule.waitUntilAtLeastOneExists(hasText(text, substring = false), 45_000)
    }

    private fun waitForTag(tag: String) {
        composeRule.waitUntilAtLeastOneExists(hasTestTag(tag), 45_000)
    }

    private fun scrollMainToText(text: String) {
        composeRule.waitUntil(45_000) {
            runCatching {
                composeRule.onNodeWithTag("main_scroll").performScrollToNode(hasText(text, substring = false))
                true
            }.getOrDefault(false)
        }
    }

    private fun scrollDetailToTag(tag: String) {
        composeRule.waitUntil(45_000) {
            runCatching {
                composeRule.onNodeWithTag("crm_detail_scroll").performScrollToNode(hasTestTag(tag))
                true
            }.getOrDefault(false)
        }
    }

    private fun scrollDetailToText(text: String) {
        composeRule.waitUntil(45_000) {
            runCatching {
                composeRule.onNodeWithTag("crm_detail_scroll").performScrollToNode(hasText(text, substring = false))
                true
            }.getOrDefault(false)
        }
    }

    @Test(timeout = 120_000)
    fun customerCommercialFlow_quoteLineAcceptAndOrder_isReachableFromRealUi() {
        runBlocking {
            val repository = LocalCrmRepository(LanuCrmDatabase.getInstance(composeRule.activity))
            repository.addBusinessAsCustomer(
                VerifiedBusiness(
                    id = "commercial-ui-smoke-customer",
                    name = "Ticari Smoke Müşteri",
                    city = "İstanbul",
                    district = "Kadıköy",
                    neighborhood = "Caferağa",
                    source = DataSourceDescriptor(
                        id = "smoke-verified-source",
                        name = "Smoke verified source",
                        publisher = "LANU test",
                        licenseOrTerms = "test-only",
                        sourceUrl = "https://example.invalid/test-only",
                        lastVerifiedAtEpochMs = 1L,
                    ),
                    verifiedAtEpochMs = 1L,
                ),
            )
        }

        composeRule.activityRule.scenario.recreate()
        waitForText("CRM")
        composeRule.onNode(hasText("CRM", substring = false)).performClick()
        waitForText("CRM Çalışma Alanı")
        scrollMainToText("Ticari Smoke Müşteri")
        composeRule.onNode(hasText("Ticari Smoke Müşteri", substring = false)).assertExists()
        composeRule.onAllNodes(hasText("Aç", substring = false))[0].performClick()

        waitForTag("crm_detail_back")
        scrollDetailToTag("crm_commercial_section")
        waitForTag("crm_quote_number_input")
        composeRule.onNodeWithTag("crm_quote_number_input").performTextInput("SMOKE-T-001")
        composeRule.onNodeWithTag("crm_create_quote").performClick()

        scrollDetailToText("SMOKE-T-001 • DRAFT • TRY")
        composeRule.onNodeWithTag("crm_quote_product_input").performTextInput("Smoke Ürün")
        composeRule.onNodeWithTag("crm_quote_price_input").performTextInput("125,50")
        composeRule.onNode(hasText("Teklife ürün satırı ekle", substring = false)).performClick()

        scrollDetailToText("Teklifi gönderildi olarak işaretle")
        composeRule.onNode(hasText("Teklifi gönderildi olarak işaretle", substring = false)).performClick()
        scrollDetailToText("Teklifi kabul edildi olarak işaretle")
        composeRule.onNode(hasText("Teklifi kabul edildi olarak işaretle", substring = false)).performClick()

        waitForTag("crm_order_number_input")
        composeRule.onNodeWithTag("crm_order_number_input").performTextInput("SMOKE-S-001")
        composeRule.onNode(hasText("Siparişe dönüştür", substring = false)).performClick()
        scrollDetailToText("SMOKE-S-001 • DRAFT • TRY")
        composeRule.onNode(hasText("SMOKE-S-001 • DRAFT • TRY", substring = false)).assertExists()
    }
}

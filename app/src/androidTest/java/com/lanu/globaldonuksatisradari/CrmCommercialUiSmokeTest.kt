package com.lanu.globaldonuksatisradari

import androidx.activity.compose.setContent
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lanu.globaldonuksatisradari.crm.CrmCustomer
import com.lanu.globaldonuksatisradari.crm.LanuCrmDatabase
import com.lanu.globaldonuksatisradari.crm.LocalCrmRepository
import com.lanu.globaldonuksatisradari.data.DataSourceDescriptor
import com.lanu.globaldonuksatisradari.data.VerifiedBusiness
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalTestApi::class)
class CrmCommercialUiSmokeTest {

    @JvmField
    @org.junit.Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test(timeout = 90_000)
    fun customerCommercialUi_quoteLineSendAcceptAndOrder_persistsEndToEnd() {
        val suffix = System.currentTimeMillis().toString()
        val quoteNumber = "UI-Q-$suffix"
        val orderNumber = "UI-O-$suffix"
        val customer: CrmCustomer = runBlocking {
            val repository = LocalCrmRepository(LanuCrmDatabase.getInstance(composeRule.activity))
            repository.addBusinessAsCustomer(
                VerifiedBusiness(
                    id = "commercial-ui-$suffix",
                    name = "Commercial UI Smoke $suffix",
                    city = "İstanbul",
                    district = "Kadıköy",
                    neighborhood = "Caferağa",
                    source = DataSourceDescriptor(
                        id = "instrumentation",
                        name = "Instrumentation fixture",
                        publisher = "LANU test",
                        licenseOrTerms = "test-only",
                        sourceUrl = "local://instrumentation",
                        lastVerifiedAtEpochMs = 1L,
                    ),
                    verifiedAtEpochMs = 1L,
                ),
            )
            repository.observeCustomers("İstanbul").first().first { it.businessName == "Commercial UI Smoke $suffix" }
        }

        composeRule.activity.setContent {
            LanuGlobalTheme {
                CrmCustomerContactsSection(customer = customer, onMessage = {})
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("crm_quote_number_input").performTextInput(quoteNumber)
        composeRule.onNodeWithTag("crm_create_quote").performClick()
        composeRule.waitUntilAtLeastOneExists(hasText(quoteNumber, substring = true), 20_000)

        composeRule.onNodeWithTag("crm_quote_product_input").performTextInput("Smoke Donuk Urun")
        composeRule.onNodeWithTag("crm_quote_unit_input").performTextClearance()
        composeRule.onNodeWithTag("crm_quote_unit_input").performTextInput("adet")
        composeRule.onNodeWithTag("crm_quote_quantity_input").performTextClearance()
        composeRule.onNodeWithTag("crm_quote_quantity_input").performTextInput("2")
        composeRule.onNodeWithTag("crm_quote_price_input").performTextInput("125,50")
        composeRule.onNode(hasText("Teklife ürün satırı ekle", substring = false)).performClick()
        composeRule.waitUntilAtLeastOneExists(hasText("Smoke Donuk Urun", substring = true), 20_000)

        composeRule.onNode(hasText("Teklifi gönderildi olarak işaretle", substring = false)).performClick()
        composeRule.waitUntilAtLeastOneExists(hasText("SENT", substring = true), 20_000)
        composeRule.onNode(hasText("Teklifi kabul edildi olarak işaretle", substring = false)).performClick()
        composeRule.waitUntilAtLeastOneExists(hasText("ACCEPTED", substring = true), 20_000)

        composeRule.onNodeWithTag("crm_order_number_input").performTextInput(orderNumber)
        composeRule.onNode(hasText("Siparişe dönüştür", substring = false)).performClick()
        composeRule.waitUntilAtLeastOneExists(hasText(orderNumber, substring = true), 20_000)

        runBlocking {
            val database = LanuCrmDatabase.getInstance(composeRule.activity)
            val commercial = com.lanu.globaldonuksatisradari.crm.CommercialCrmRepository(database)
            val quotes = commercial.observeQuotes(customer.id).first()
            val orders = commercial.observeOrders(customer.id).first()
            assertTrue(quotes.any { it.quoteNumber == quoteNumber && it.status.name == "ACCEPTED" })
            val order = orders.first { it.orderNumber == orderNumber }
            val orderLines = commercial.observeOrderLines(order.id).first()
            assertEquals(1, orderLines.size)
            assertEquals("Smoke Donuk Urun", orderLines.single().productName)
            assertEquals(2_000L, orderLines.single().quantityMilli)
            assertEquals(12_550L, orderLines.single().unitPriceMinor)
        }
    }
}

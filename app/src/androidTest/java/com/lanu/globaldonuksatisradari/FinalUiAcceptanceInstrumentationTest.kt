package com.lanu.globaldonuksatisradari

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import com.lanu.globaldonuksatisradari.crm.CommercialCrmRepository
import com.lanu.globaldonuksatisradari.crm.LanuCrmDatabase
import com.lanu.globaldonuksatisradari.crm.LocalCrmRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalTestApi::class)
class FinalUiAcceptanceInstrumentationTest {
    @JvmField
    @org.junit.Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private fun waitForText(text: String, timeoutMs: Long = 45_000): SemanticsNodeInteraction {
        val matcher = hasText(text, substring = false)
        composeRule.waitUntilAtLeastOneExists(matcher, timeoutMs)
        return composeRule.onNode(matcher)
    }

    private fun waitForTag(tag: String, timeoutMs: Long = 45_000): SemanticsNodeInteraction {
        val matcher = hasTestTag(tag)
        composeRule.waitUntil(timeoutMs) {
            runCatching {
                composeRule.onNode(matcher, useUnmergedTree = true).assertExists()
                true
            }.getOrDefault(false)
        }
        return composeRule.onNode(matcher, useUnmergedTree = true)
    }

    private fun scrollMainToTag(tag: String) {
        composeRule.onNode(hasTestTag("main_scroll"))
            .performScrollToNode(hasTestTag(tag))
        composeRule.waitForIdle()
    }

    /**
     * A Room Flow emission and the corresponding Compose semantics tree may land on
     * different frames. Retry the scroll until the newly rendered target is present.
     * This protects transitions without masking a permanently missing control.
     */
    private fun waitForDetailTag(tag: String, timeoutMs: Long = 20_000): SemanticsNodeInteraction {
        val matcher = hasTestTag(tag)
        try {
            composeRule.waitUntil(timeoutMs) {
                runCatching {
                    composeRule.onNode(hasTestTag("crm_detail_scroll"))
                        .performScrollToNode(matcher)
                    composeRule.onNode(matcher, useUnmergedTree = true).assertExists()
                    true
                }.getOrDefault(false)
            }
        } catch (error: Throwable) {
            throw AssertionError("CRM detail control not rendered in time: $tag", error)
        }
        return composeRule.onNode(matcher, useUnmergedTree = true)
    }

    private fun waitForStep(
        label: String,
        timeoutMs: Long = 30_000,
        condition: () -> Boolean,
    ) {
        try {
            composeRule.waitUntil(timeoutMs, condition)
        } catch (error: Throwable) {
            throw AssertionError("Kabul adımı zaman aşımına uğradı: $label", error)
        }
    }

    private fun waitForEnabledTag(tag: String, timeoutMs: Long = 30_000): SemanticsNodeInteraction {
        val matcher = hasTestTag(tag)
        composeRule.waitUntil(timeoutMs) {
            runCatching {
                composeRule.onNode(matcher, useUnmergedTree = true).assertIsEnabled()
                true
            }.getOrDefault(false)
        }
        return composeRule.onNode(matcher, useUnmergedTree = true)
    }

    @Test(timeout = 90_000)
    fun istanbulSideSelection_keepsDistrictAndNeighborhoodScopeSeparated() {
        val context = composeRule.activity
        val now = System.currentTimeMillis()
        context.getSharedPreferences("district_catalog_cache", android.content.Context.MODE_PRIVATE)
            .edit()
            .putString(
                "districts:v3:istanbul",
                JSONObject()
                    .put("savedAt", now)
                    .put("districts", JSONArray().apply { IstanbulDistricts.ALL.forEach(::put) })
                    .toString(),
            )
            .apply()
        context.getSharedPreferences("neighborhood_catalog_cache", android.content.Context.MODE_PRIVATE)
            .edit()
            .putString(
                "neighborhoods:v2:istanbul:kadikoy",
                JSONObject().put("savedAt", now).put("items", JSONArray(listOf("Caferağa", "Moda"))).toString(),
            )
            .putString(
                "neighborhoods:v2:istanbul:arnavutkoy",
                JSONObject().put("savedAt", now).put("items", JSONArray(listOf("Anadolu", "Arnavutköy Merkez"))).toString(),
            )
            .apply()
        context.getSharedPreferences("lanu_ui_state", android.content.Context.MODE_PRIVATE)
            .edit()
            .putString("selected_city", "İstanbul Anadolu")
            .putString("selected_district", "Tümü")
            .apply()

        composeRule.activityRule.scenario.recreate()
        scrollMainToTag("district_filter")
        waitForTag("district_filter").performClick()
        waitForText("Kadıköy").assertExists().performClick()

        scrollMainToTag("neighborhood_filter")
        waitForTag("neighborhood_filter").assertIsEnabled().performClick()
        waitForText("Caferağa").assertExists().performClick()

        scrollMainToTag("istanbul_side_european")
        waitForTag("istanbul_side_european").assertIsDisplayed().performClick()

        scrollMainToTag("district_filter")
        waitForTag("district_filter").performClick()
        // Avrupa listesinin ilk görünür ilçesi seçilir; Şişli/Bakırköy kapsamı unit testte ayrıca kilitlidir.
        waitForText("Arnavutköy").assertExists().performClick()

        scrollMainToTag("neighborhood_filter")
        waitForTag("neighborhood_filter").assertIsEnabled().performClick()
        waitForText("Anadolu").assertExists().performClick()
        scrollMainToTag("neighborhood_filter")
        waitForText("Mahalle: Anadolu").assertExists()
    }

    @Test(timeout = 90_000)
    fun crmTags_saveFromDetailAndPersistAcrossRoomFlow() {
        var customerId = ""
        var businessSourceId = ""
        runBlocking {
            val repository = LocalCrmRepository(LanuCrmDatabase.getInstance(composeRule.activity))
            val customer = repository.addManualCustomerPoint(
                businessName = "Etiket Smoke Nokta " + System.nanoTime(),
                address = "Etiket Test Adres",
                city = "İstanbul",
                district = "Kadıköy",
                neighborhood = "Caferağa",
                latitude = 40.991,
                longitude = 29.031,
            )
            customerId = customer.id
            businessSourceId = customer.businessSourceId
        }

        composeRule.activity.getSharedPreferences("lanu_ui_state", android.content.Context.MODE_PRIVATE)
            .edit().putString("selected_city", "İstanbul Anadolu").putString("selected_district", "Kadıköy").apply()
        composeRule.activityRule.scenario.recreate()

        waitForTag("nav_crm").performClick()
        waitForTag("crm_tab_customers").performClick()
        waitForTag("crm_open_" + businessSourceId).performScrollTo().performClick()
        composeRule.onNode(hasTestTag("crm_detail_scroll")).performScrollToNode(hasTestTag("crm_tags_input"))
        waitForTag("crm_tags_input").assertIsDisplayed().performTextInput("Sıcak Lead, Otel")
        waitForTag("crm_tags_save").performClick()

        composeRule.waitUntil(30_000) {
            runBlocking {
                LocalCrmRepository(LanuCrmDatabase.getInstance(composeRule.activity))
                    .observeCustomers("İstanbul").first().firstOrNull { it.id == customerId }?.tags?.let { tags ->
                        tags.any { it.equals("Sıcak Lead", true) } && tags.any { it.equals("Otel", true) }
                    } == true
            }
        }
    }

    @Test(timeout = 90_000)
    fun duplicateReview_mergesSelectedRecordFromUi() {
        var targetId = ""
        var sourceId = ""
        runBlocking {
            val repository = LocalCrmRepository(LanuCrmDatabase.getInstance(composeRule.activity))
            val suffix = System.nanoTime().toString()
            targetId = repository.addManualCustomerPoint(
                businessName = "UI Mükerrer Market $suffix",
                address = "UI Hedef $suffix",
                city = "Ardahan",
                district = "Merkez",
                neighborhood = "Kaptanpaşa",
                latitude = 40.9870,
                longitude = 29.0280,
                phone = "05321112233",
                taxOrNationalId = "9876543210",
            ).id
            sourceId = repository.addManualCustomerPoint(
                businessName = "UI MÜKERRER MARKET $suffix",
                address = "UI Kaynak $suffix",
                city = "Ardahan",
                district = "Merkez",
                neighborhood = "Kaptanpaşa",
                latitude = 40.9871,
                longitude = 29.0281,
                phone = "+90 532 111 22 33",
                taxOrNationalId = "9876543210",
            ).id
        }

        composeRule.activity.getSharedPreferences("lanu_ui_state", android.content.Context.MODE_PRIVATE)
            .edit().putString("selected_city", "Ardahan").putString("selected_district", "Merkez").apply()
        composeRule.activityRule.scenario.recreate()

        waitForTag("nav_crm").performClick()
        waitForTag("crm_tab_duplicates").performClick()
        waitForTag("crm_duplicate_screen").assertIsDisplayed()
        composeRule.onNode(hasTestTag("crm_duplicate_screen"))
            .performScrollToNode(hasTestTag("crm_merge_keep_" + targetId))
        waitForTag("crm_merge_keep_" + targetId).assertIsDisplayed().performClick()
        waitForTag("crm_duplicate_confirm").assertIsDisplayed().performClick()

        composeRule.waitUntil(30_000) {
            runBlocking {
                LocalCrmRepository(LanuCrmDatabase.getInstance(composeRule.activity))
                    .observeCustomers("Ardahan").first().none { it.id == sourceId }
            }
        }
    }

    @Test(timeout = 120_000)
    fun commercialWorkspace_catalogQuoteAcceptedOrderFlowWorksEndToEnd() {
        val context = composeRule.activity
        var customerId = ""
        runBlocking {
            customerId = LocalCrmRepository(LanuCrmDatabase.getInstance(context)).addManualCustomerPoint(
                businessName = "Ticari UI Smoke " + System.nanoTime(),
                address = "Test Ticari Adres",
                city = "İstanbul",
                district = "Kadıköy",
                neighborhood = "Caferağa",
                latitude = 40.99,
                longitude = 29.03,
            ).id
        }
        val productName = "Smoke Katalog Ürün " + System.nanoTime()
        ProductCatalogRepository(context).upsert(
            id = "ui-smoke-product-" + System.nanoTime(),
            name = productName,
            category = "Donuk",
            unit = "Koli",
            priceMinor = 32_145L,
            currency = "TRY",
            note = null,
        )

        context.getSharedPreferences("lanu_ui_state", android.content.Context.MODE_PRIVATE)
            .edit().putString("selected_city", "İstanbul Anadolu").putString("selected_district", "Kadıköy").apply()
        composeRule.activityRule.scenario.recreate()
        waitForTag("nav_crm").performClick()
        waitForTag("crm_tab_customers").performClick()
        val customer = runBlocking {
            LocalCrmRepository(LanuCrmDatabase.getInstance(context))
                .observeCustomers("İstanbul").first().first { it.id == customerId }
        }
        waitForTag("crm_open_" + customer.businessSourceId).performScrollTo().performClick()

        val commercialRepository = CommercialCrmRepository(LanuCrmDatabase.getInstance(context))
        val quoteNumber = "SMOKE-T-" + System.nanoTime()
        composeRule.onNode(hasTestTag("crm_detail_scroll")).performScrollToNode(hasTestTag("crm_commercial_section"))
        waitForTag("crm_quote_number_input").performTextInput(quoteNumber)
        waitForTag("crm_create_quote").assertIsEnabled().performClick()

        waitForStep("taslak teklif Room kaydı") {
            runBlocking { commercialRepository.observeQuotes(customerId).first().any { it.quoteNumber == quoteNumber } }
        }
        val quoteId = runBlocking {
            commercialRepository.observeQuotes(customerId).first().single { it.quoteNumber == quoteNumber }.id
        }

        waitForDetailTag("crm_quote_product_catalog_" + quoteId)
        waitForTag("crm_quote_product_catalog_" + quoteId).performClick()
        waitForText(productName + " • 321,45 TRY").performClick()
        waitForTag("crm_add_quote_line_" + quoteId).assertIsEnabled().performClick()
        waitForStep("katalog ürünü teklif satırına ekleme") {
            runBlocking { commercialRepository.observeQuoteLines(quoteId).first().isNotEmpty() }
        }

        waitForDetailTag("crm_send_quote_" + quoteId)
        composeRule.waitForIdle()
        waitForEnabledTag("crm_send_quote_" + quoteId).performScrollTo().performClick()
        waitForStep("teklifi SENT durumuna geçirme") {
            runBlocking { commercialRepository.observeQuotes(customerId).first().any { it.id == quoteId && it.status.name == "SENT" } }
        }

        waitForDetailTag("crm_accept_quote_" + quoteId)
        composeRule.waitForIdle()
        waitForEnabledTag("crm_accept_quote_" + quoteId).performScrollTo().performClick()
        waitForStep("teklifi ACCEPTED durumuna geçirme") {
            runBlocking { commercialRepository.observeQuotes(customerId).first().any { it.id == quoteId && it.status.name == "ACCEPTED" } }
        }

        val orderNumber = "SMOKE-S-" + System.nanoTime()
        waitForDetailTag("crm_order_number_input")
        waitForTag("crm_order_number_input").performTextInput(orderNumber)
        waitForEnabledTag("crm_create_order_" + quoteId).performScrollTo().performClick()
        waitForStep("kabul edilen tekliften sipariş oluşturma") {
            runBlocking { commercialRepository.observeOrders(customerId).first().any { it.orderNumber == orderNumber } }
        }
        val orderId = runBlocking {
            commercialRepository.observeOrders(customerId).first().single { it.orderNumber == orderNumber }.id
        }
        // Room kaydı, Compose semantiğine bir sonraki frame'de yansıyabilir.
        val orderAdvanceTag = "crm_order_advance_" + orderId
        waitForDetailTag(orderAdvanceTag, timeoutMs = 20_000)
        waitForEnabledTag(orderAdvanceTag, timeoutMs = 15_000).performScrollTo().performClick()
        waitForStep("siparişi CONFIRMED durumuna geçirme") {
            runBlocking { commercialRepository.observeOrders(customerId).first().any { it.id == orderId && it.status.name == "CONFIRMED" } }
        }
    }

    @Test(timeout = 30_000)
    fun routineTab_opensBeforeRouteComputation() {
        waitForTag("nav_routine").assertHasClickAction().performClick()
        waitForTag("routine_screen", timeoutMs = 5_000).assertIsDisplayed()

        composeRule.onNode(hasTestTag("routine_screen"))
            .performScrollToNode(hasTestTag("route_detail_control"))
        waitForTag("route_detail_control", timeoutMs = 15_000).assertIsDisplayed()
        waitForTag("routine_prepare_route").assertHasClickAction()
    }

}

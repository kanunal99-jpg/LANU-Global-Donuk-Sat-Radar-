package com.lanu.globaldonuksatisradari

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.work.WorkManager
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.ExperimentalTestApi
import com.lanu.globaldonuksatisradari.crm.LanuCrmDatabase
import com.lanu.globaldonuksatisradari.crm.LocalCrmRepository
import com.lanu.globaldonuksatisradari.data.DataSourceDescriptor
import com.lanu.globaldonuksatisradari.data.VerifiedBusiness
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalTestApi::class)
class MainActivitySmokeTest {

    private fun waitForText(text: String, timeoutMs: Long = 45_000): SemanticsNodeInteraction {
        val matcher = hasText(text, substring = false)
        composeRule.waitUntilAtLeastOneExists(matcher, timeoutMs)
        return composeRule.onNode(matcher)
    }

    private fun waitForTag(tag: String, timeoutMs: Long = 45_000): SemanticsNodeInteraction {
        val matcher = hasTestTag(tag)
        try {
            composeRule.waitUntil(timeoutMs) {
                runCatching {
                    composeRule.onNode(matcher, useUnmergedTree = true).assertExists()
                    true
                }.getOrDefault(false)
            }
        } catch (error: Throwable) {
            throw AssertionError("Timed out waiting for test tag: $tag", error)
        }
        return composeRule.onNode(matcher, useUnmergedTree = true)
    }

    private fun scrollMainToText(text: String) {
        composeRule.onNodeWithTag("main_scroll")
            .performScrollToNode(hasText(text, substring = false))
        composeRule.waitForIdle()
    }

    private fun scrollMainToTag(tag: String) {
        composeRule.onNodeWithTag("main_scroll")
            .performScrollToNode(hasTestTag(tag))
        composeRule.waitForIdle()
    }

    @JvmField
    @org.junit.Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test(timeout = 60_000)
    fun launch_showsCoreSalesRadarUi() {
        waitForText("LANU Global Donuk Gıda").assertIsDisplayed()
        waitForText("Satış & CRM Radarı").assertIsDisplayed()
        scrollMainToText("Seçime göre gerçek verileri getir")
        waitForTag("real_search_button").assertIsDisplayed()
    }

    @Test(timeout = 60_000)
    fun blankSearch_keepsBroadInventoryModeAvailable() {
        scrollMainToTag("inventory_filters_card")
        waitForTag("inventory_filters_card").assertIsDisplayed()
    }

    @Test(timeout = 60_000)
    fun launch_schedulesCrmSyncWork() {
        val context = composeRule.activity
        composeRule.waitUntil(30_000) {
            runCatching {
                WorkManager
                    .getInstance(context)
                    .getWorkInfosForUniqueWork("lanu_global_donuk_crm_sync")
                    .get()
                    .any { it.state.name == "ENQUEUED" || it.state.name == "RUNNING" }
            }.getOrDefault(false)
        }
        val infos = WorkManager
            .getInstance(context)
            .getWorkInfosForUniqueWork("lanu_global_donuk_crm_sync")
            .get()
        assertFalse("CRM sync work should be scheduled on Activity launch", infos.isEmpty())
        assertTrue(infos.first().state.name == "ENQUEUED" || infos.first().state.name == "RUNNING")
    }

    @Test(timeout = 60_000)
    fun launch_createsStableActivity() {
        assertNotNull(composeRule.activity)
    }

    @Test(timeout = 60_000)
    fun persistedCrmCustomer_opensRealDetailWorkflow() {
        val seededCustomer = runBlocking {
            val context = composeRule.activity
            val repository = LocalCrmRepository(LanuCrmDatabase.getInstance(context))
            val customer = repository.addBusinessAsCustomer(
                VerifiedBusiness(
                    id = "instrumentation-ui-crm-detail",
                    name = "Smoke CRM Kafe",
                    city = "İstanbul",
                    district = "Kadıköy",
                    neighborhood = "Caferağa",
                    source = DataSourceDescriptor(
                        id = "osm-nominatim",
                        name = "OpenStreetMap Nominatim",
                        publisher = "OpenStreetMap",
                        licenseOrTerms = "ODbL",
                        sourceUrl = "https://nominatim.openstreetmap.org/",
                        lastVerifiedAtEpochMs = 1L,
                    ),
                    verifiedAtEpochMs = 1L,
                )
            )

            assertTrue(
                "Seeded CRM customer must persist in Room",
                repository.observeCustomers("İstanbul").first().any { it.businessName == "Smoke CRM Kafe" },
            )
            customer
        }

        composeRule.activityRule.scenario.recreate()
        composeRule.waitForIdle()

        val crmDetailTag = "crm_open_" + seededCustomer.id
        composeRule.onNodeWithTag("main_scroll").performScrollToNode(hasTestTag(crmDetailTag))
        val crmDetailButton = waitForTag(crmDetailTag)
        crmDetailButton.performClick()
        composeRule.onNodeWithTag("crm_detail_back").assertIsDisplayed()
        composeRule.onNodeWithText("Açık takipler").assertExists()
        composeRule.onNodeWithText("Aktivite geçmişi").assertExists()
    }

    @Test(timeout = 60_000)
    fun navigationBackForwardAndNewSections_areReachable() {
        waitForText("Manuel nokta").performClick()
        waitForText("Manuel Nokta Kaydı").assertIsDisplayed()

        waitForText("← Geri").performClick()
        waitForText("Satış & CRM Radarı").assertExists()

        waitForText("İleri →").performClick()
        waitForText("Manuel Nokta Kaydı").assertExists()

        waitForText("Rutin").performClick()
        waitForText("Yakınlık Bazlı Rutin").assertIsDisplayed()
    }

    @Test(timeout = 60_000)
    fun productCatalog_canOpenAndAddManualPrice() {
        waitForText("Ürün kataloğu").performClick()
        waitForText("Ürün Kataloğu").assertIsDisplayed()
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val addButton = when {
            device.wait(Until.hasObject(By.res("product_add_button")), 5_000) ->
                device.findObject(By.res("product_add_button"))
            device.wait(Until.hasObject(By.desc("product_add_button")), 5_000) ->
                device.findObject(By.desc("product_add_button"))
            else -> throw AssertionError("Yeni ürün button must be visible to UiAutomator by resource id or content description")
        }
        addButton.click()
        assertTrue(
            "Product editor dialog must open",
            device.wait(Until.hasObject(By.res("product_editor_dialog")), 5_000),
        )
        device.findObject(By.res("product_name_input")).setText("Smoke Donuk Ürün")
        device.findObject(By.res("product_price_input")).setText("125,50")
        device.findObject(By.res("product_save_button")).click()
        waitForText("Smoke Donuk Ürün").assertExists()
        waitForText("125,50 TRY").assertExists()
    }
}    @Test(timeout = 60_000)
    fun productCatalog_canOpenAndAddManualPrice() {
        waitForText("Ürün kataloğu").performClick()
        waitForText("Ürün Kataloğu").assertIsDisplayed()

        val addButton = waitForTag("product_add_button")
        addButton.assertIsDisplayed()
        addButton.performTouchInput { click() }

        val dialog = waitForTag("product_editor_dialog")
        dialog.assertIsDisplayed()
        waitForTag("product_name_input").performTextInput("Smoke Donuk Ürün")
        waitForTag("product_price_input").performTextInput("125,50")
        waitForTag("product_save_button").performTouchInput { click() }
        waitForText("Smoke Donuk Ürün").assertExists()
        waitForText("125,50 TRY").assertExists()
    }ge com.lanu.globaldonuksatisradari

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.work.WorkManager
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.ExperimentalTestApi
import com.lanu.globaldonuksatisradari.crm.LanuCrmDatabase
import com.lanu.globaldonuksatisradari.crm.LocalCrmRepository
import com.lanu.globaldonuksatisradari.data.DataSourceDescriptor
import com.lanu.globaldonuksatisradari.data.VerifiedBusiness
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalTestApi::class)
class MainActivitySmokeTest {

    private fun waitForText(text: String, timeoutMs: Long = 45_000): SemanticsNodeInteraction {
        val matcher = hasText(text, substring = false)
        composeRule.waitUntilAtLeastOneExists(matcher, timeoutMs)
        return composeRule.onNode(matcher)
    }

    private fun waitForTag(tag: String, timeoutMs: Long = 45_000): SemanticsNodeInteraction {
        val matcher = hasTestTag(tag)
        try {
            composeRule.waitUntil(timeoutMs) {
                runCatching {
                    composeRule.onNode(matcher, useUnmergedTree = true).assertExists()
                    true
                }.getOrDefault(false)
            }
        } catch (error: Throwable) {
            throw AssertionError("Timed out waiting for test tag: $tag", error)
        }
        return composeRule.onNode(matcher, useUnmergedTree = true)
    }

    private fun scrollMainToText(text: String) {
        composeRule.onNodeWithTag("main_scroll")
            .performScrollToNode(hasText(text, substring = false))
        composeRule.waitForIdle()
    }

    private fun scrollMainToTag(tag: String) {
        composeRule.onNodeWithTag("main_scroll")
            .performScrollToNode(hasTestTag(tag))
        composeRule.waitForIdle()
    }

    @JvmField
    @org.junit.Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test(timeout = 60_000)
    fun launch_showsCoreSalesRadarUi() {
        waitForText("LANU Global Donuk Gıda").assertIsDisplayed()
        waitForText("Satış & CRM Radarı").assertIsDisplayed()
        scrollMainToText("Seçime göre gerçek verileri getir")
        waitForTag("real_search_button").assertIsDisplayed()
    }

    @Test(timeout = 60_000)
    fun blankSearch_keepsBroadInventoryModeAvailable() {
        scrollMainToTag("inventory_filters_card")
        waitForTag("inventory_filters_card").assertIsDisplayed()
    }

    @Test(timeout = 60_000)
    fun launch_schedulesCrmSyncWork() {
        val context = composeRule.activity
        composeRule.waitUntil(30_000) {
            runCatching {
                WorkManager
                    .getInstance(context)
                    .getWorkInfosForUniqueWork("lanu_global_donuk_crm_sync")
                    .get()
                    .any { it.state.name == "ENQUEUED" || it.state.name == "RUNNING" }
            }.getOrDefault(false)
        }
        val infos = WorkManager
            .getInstance(context)
            .getWorkInfosForUniqueWork("lanu_global_donuk_crm_sync")
            .get()
        assertFalse("CRM sync work should be scheduled on Activity launch", infos.isEmpty())
        assertTrue(infos.first().state.name == "ENQUEUED" || infos.first().state.name == "RUNNING")
    }

    @Test(timeout = 60_000)
    fun launch_createsStableActivity() {
        assertNotNull(composeRule.activity)
    }

    @Test(timeout = 60_000)
    fun persistedCrmCustomer_opensRealDetailWorkflow() {
        val seededCustomer = runBlocking {
            val context = composeRule.activity
            val repository = LocalCrmRepository(LanuCrmDatabase.getInstance(context))
            val customer = repository.addBusinessAsCustomer(
                VerifiedBusiness(
                    id = "instrumentation-ui-crm-detail",
                    name = "Smoke CRM Kafe",
                    city = "İstanbul",
                    district = "Kadıköy",
                    neighborhood = "Caferağa",
                    source = DataSourceDescriptor(
                        id = "osm-nominatim",
                        name = "OpenStreetMap Nominatim",
                        publisher = "OpenStreetMap",
                        licenseOrTerms = "ODbL",
                        sourceUrl = "https://nominatim.openstreetmap.org/",
                        lastVerifiedAtEpochMs = 1L,
                    ),
                    verifiedAtEpochMs = 1L,
                )
            )

            assertTrue(
                "Seeded CRM customer must persist in Room",
                repository.observeCustomers("İstanbul").first().any { it.businessName == "Smoke CRM Kafe" },
            )
            customer
        }

        composeRule.activityRule.scenario.recreate()
        composeRule.waitForIdle()

        val crmDetailTag = "crm_open_" + seededCustomer.id
        composeRule.onNodeWithTag("main_scroll").performScrollToNode(hasTestTag(crmDetailTag))
        val crmDetailButton = waitForTag(crmDetailTag)
        crmDetailButton.performClick()
        composeRule.onNodeWithTag("crm_detail_back").assertIsDisplayed()
        composeRule.onNodeWithText("Açık takipler").assertExists()
        composeRule.onNodeWithText("Aktivite geçmişi").assertExists()
    }

    @Test(timeout = 60_000)
    fun navigationBackForwardAndNewSections_areReachable() {
        waitForText("Manuel nokta").performClick()
        waitForText("Manuel Nokta Kaydı").assertIsDisplayed()

        waitForText("← Geri").performClick()
        waitForText("Satış & CRM Radarı").assertExists()

        waitForText("İleri →").performClick()
        waitForText("Manuel Nokta Kaydı").assertExists()

        waitForText("Rutin").performClick()
        waitForText("Yakınlık Bazlı Rutin").assertIsDisplayed()
    }

    @Test(timeout = 60_000)
    fun productCatalog_canOpenAndAddManualPrice() {
        waitForText("Ürün kataloğu").performClick()
        waitForText("Ürün Kataloğu").assertIsDisplayed()
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val addButton = when {
            device.wait(Until.hasObject(By.res("product_add_button")), 5_000) ->
                device.findObject(By.res("product_add_button"))
            device.wait(Until.hasObject(By.desc("product_add_button")), 5_000) ->
                device.findObject(By.desc("product_add_button"))
            else -> throw AssertionError("Yeni ürün button must be visible to UiAutomator by resource id or content description")
        }
        addButton.click()
        assertTrue(
            "Product editor dialog must open",
            device.wait(Until.hasObject(By.res("product_editor_dialog")), 5_000),
        )
        device.findObject(By.res("product_name_input")).setText("Smoke Donuk Ürün")
        device.findObject(By.res("product_price_input")).setText("125,50")
        device.findObject(By.res("product_save_button")).click()
        waitForText("Smoke Donuk Ürün").assertExists()
        waitForText("125,50 TRY").assertExists()
    }
}

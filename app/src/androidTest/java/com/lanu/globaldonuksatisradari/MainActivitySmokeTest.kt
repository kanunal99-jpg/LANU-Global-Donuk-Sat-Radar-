package com.lanu.globaldonuksatisradari

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.performSemanticsAction
import androidx.work.WorkManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lanu.globaldonuksatisradari.crm.LanuCrmDatabase
import com.lanu.globaldonuksatisradari.crm.LocalCrmRepository
import com.lanu.globaldonuksatisradari.data.DataSourceDescriptor
import com.lanu.globaldonuksatisradari.data.VerifiedBusiness
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

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

    private fun scrollMainToTag(tag: String) {
        composeRule.onNodeWithTag("main_scroll")
            .performScrollToNode(hasTestTag(tag))
        composeRule.waitForIdle()
    }

    private fun scrollMainToText(text: String) {
        composeRule.onNodeWithTag("main_scroll")
            .performScrollToNode(hasText(text, substring = false))
        composeRule.waitForIdle()
    }

    @JvmField
    @org.junit.Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test(timeout = 60_000)
    fun launch_showsCoreSalesRadarUi() {
        waitForText("Satış & CRM Radarı").assertIsDisplayed()
        scrollMainToTag("real_search_button")
        waitForTag("real_search_button").assertIsDisplayed().assertHasClickAction()
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
        runBlocking {
            val context = composeRule.activity
            val repository = LocalCrmRepository(LanuCrmDatabase.getInstance(context))
            repository.addBusinessAsCustomer(
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
        }

        composeRule.activityRule.scenario.recreate()
        composeRule.waitUntilAtLeastOneExists(hasText("Smoke CRM Kafe", substring = false), 45_000)
        scrollMainToText("Smoke CRM Kafe")
        waitForText("Smoke CRM Kafe").assertIsDisplayed()
        waitForText("Aç").assertHasClickAction().performClick()
        waitForTag("crm_detail_back").assertIsDisplayed()
        waitForText("Açık takipler").assertExists()
        waitForText("Aktivite geçmişi").assertExists()
    }

    @Test(timeout = 60_000)
    fun navigationBackForwardAndNewSections_areReachable() {
        waitForText("Nokta").performClick()
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
        waitForText("Ürünler").performClick()
        waitForText("Ürün Kataloğu").assertIsDisplayed()

        val addButton = waitForTag("product_add_button").assertIsDisplayed().assertHasClickAction()
        addButton.performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()

        waitForTag("product_editor_dialog").assertIsDisplayed()
        waitForTag("product_name_input").assertIsDisplayed().performTextInput("Smoke Donuk Ürün")
        waitForTag("product_price_input").assertIsDisplayed().performTextInput("125,50")
        waitForTag("product_save_button").assertIsDisplayed().performTouchInput { click() }

        waitForText("Smoke Donuk Ürün").assertExists()
        waitForText("125,50 TRY").assertExists()
    }
}

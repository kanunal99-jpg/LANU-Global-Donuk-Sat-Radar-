package com.lanu.globaldonuksatisradari

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.WorkManager
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

    private fun scrollMainToText(text: String, timeoutMs: Long = 45_000): SemanticsNodeInteraction {
        val matcher = hasText(text, substring = false)
        try {
            composeRule.waitUntil(timeoutMs) {
                runCatching {
                    composeRule.onNodeWithTag("main_scroll")
                        .performScrollToNode(matcher)
                    composeRule.waitForIdle()
                    true
                }.getOrDefault(false)
            }
        } catch (error: Throwable) {
            throw AssertionError("Timed out scrolling main list to text: $text", error)
        }
        return composeRule.onNode(matcher)
    }

    @JvmField
    @org.junit.Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test(timeout = 60_000)
    fun launch_showsCoreSalesRadarUi() {
        waitForText("Satış Radarı").assertIsDisplayed()
        scrollMainToTag("real_search_button")
        waitForTag("real_search_button").assertIsDisplayed().assertHasClickAction()
    }

    @Test(timeout = 60_000)
    fun istanbulRegionDistrictChain_isWiredIntoRealUi() {
        waitForTag("city_filter").assertIsDisplayed().assertHasClickAction()

        waitForTag("district_filter").performClick()
        waitForText("Kadıköy").assertExists()
        waitForText("Pendik").assertExists()
        waitForText("Kadıköy").performClick()

        waitForTag("city_filter").performClick()
        waitForText("İstanbul Avrupa").assertExists().performClick()
        waitForTag("district_filter").performClick()
        waitForText("Şişli").assertExists()
        waitForText("Bakırköy").assertExists()
    }

    @Test(timeout = 60_000)
    fun advancedFilters_areCollapsedButReachable() {
        scrollMainToTag("inventory_filters_card")
        waitForTag("inventory_filters_card").assertIsDisplayed()
        waitForTag("advanced_filters_toggle").assertHasClickAction().performClick()
        waitForText("Telefon").assertExists()
        waitForText("Web sitesi").assertExists()
        waitForText("Menü").assertExists()
        waitForText("Çalışma saati").assertExists()
        waitForText("Adres").assertExists()
        waitForText("Koordinat").assertExists()
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
                ),
            )
            assertTrue(
                "Seeded CRM customer must persist in Room",
                repository.observeCustomers("İstanbul").first().any { it.businessName == "Smoke CRM Kafe" },
            )
        }

        composeRule.activityRule.scenario.recreate()
        waitForText("CRM").assertHasClickAction().performClick()
        waitForText("CRM Çalışma Alanı").assertIsDisplayed()
        scrollMainToText("Smoke CRM Kafe").assertIsDisplayed()
        waitForText("Aç").assertHasClickAction().performClick()
        waitForTag("crm_detail_back").assertIsDisplayed()
        waitForText("Açık takipler").assertExists()
        waitForText("Aktivite geçmişi").assertExists()
    }

    @Test(timeout = 60_000)
    fun crmWorkspace_searchAndStageFilters_areReachable() {
        waitForText("CRM").performClick()
        waitForText("CRM Çalışma Alanı").assertIsDisplayed()
        scrollMainToTag("crm_customer_search")
        waitForTag("crm_customer_search").assertIsDisplayed().performTextInput("Kadıköy")
        waitForTag("crm_stage_filter").assertHasClickAction()
    }

    @Test(timeout = 60_000)
    fun navigationBackForwardAndNewSections_areReachable() {
        waitForText("CRM").performClick()
        waitForText("CRM Çalışma Alanı").assertIsDisplayed()

        waitForText("Nokta").performClick()
        waitForText("Manuel Nokta Kaydı").assertIsDisplayed()

        waitForText("← Geri").performClick()
        waitForText("CRM Çalışma Alanı").assertExists()

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
        waitForText("Kaynak doğrulanmadı").assertExists()
    }

    @Test(timeout = 60_000)
    fun productCatalog_photoSources_areReachable() {
        waitForText("Ürünler").performClick()
        waitForText("Ürün Kataloğu").assertIsDisplayed()
        waitForTag("product_add_button").assertIsDisplayed().performClick()
        waitForTag("product_editor_dialog").assertIsDisplayed()

        composeRule.onNodeWithTag("product_editor_content", useUnmergedTree = true)
            .performScrollToNode(hasTestTag("product_image_gallery_button"))
        waitForTag("product_image_gallery_button").assertIsDisplayed().assertHasClickAction()

        composeRule.onNodeWithTag("product_editor_content", useUnmergedTree = true)
            .performScrollToNode(hasTestTag("product_image_camera_button"))
        waitForTag("product_image_camera_button").assertIsDisplayed().assertHasClickAction()
        waitForTag("product_image_url_input").assertExists()
    }

    @Test(timeout = 60_000)
    fun productImageStorage_pickerAndCameraCopies_arePrivateAndPersistent() {
        val storage = ProductImageStorage(composeRule.activity)

        val pickerSource = storage.createCameraCapture()
        pickerSource.file.writeBytes(byteArrayOf(0x01, 0x02, 0x03, 0x04))
        val imported = storage.importFromPicker(pickerSource.uri)
        assertTrue("Picker copy must move into app-owned persistent storage", storage.isOwned(imported))
        storage.deleteOwned(imported)
        storage.discardCameraCapture(pickerSource)
        assertFalse("Deleted picker copy must not remain app-owned", storage.isOwned(imported))

        val cameraCapture = storage.createCameraCapture()
        cameraCapture.file.writeBytes(byteArrayOf(0x11, 0x22, 0x33, 0x44))
        val cameraImage = storage.finalizeCameraCapture(cameraCapture)
        assertTrue("Camera finalization must move into app-owned persistent storage", storage.isOwned(cameraImage))
        storage.deleteOwned(cameraImage)
        assertFalse("Deleted camera copy must not remain app-owned", storage.isOwned(cameraImage))
    }
}

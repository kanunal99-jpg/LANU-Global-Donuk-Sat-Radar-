package com.lanu.globaldonuksatisradari

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasTestTag
import androidx.work.WorkManager
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
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
class MainActivitySmokeTest {

    @JvmField
    @org.junit.Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test(timeout = 60_000)
    fun launch_showsCoreSalesRadarUi() {
        composeRule.waitForIdle()
        composeRule.onNodeWithText("LANU Global Donuk Satış Radarı").assertIsDisplayed()
        composeRule.onNodeWithText("Satış Radarı").assertIsDisplayed()
        composeRule.onNodeWithText("Gerçek kaynaktan ara").assertIsDisplayed()
    }

    @Test(timeout = 60_000)
    fun blankSearch_showsValidationMessage() {
        val mainScroll = composeRule.onNodeWithTag("main_scroll")
        mainScroll.performScrollToNode(hasText("Gerçek kaynaktan ara"))
        composeRule.onNodeWithText("Gerçek kaynaktan ara").performClick()
        mainScroll.performScrollToNode(hasText("Arama için bir işletme/HORECA terimi yazın."))
        composeRule.onNodeWithText("Arama için bir işletme/HORECA terimi yazın.").assertIsDisplayed()
    }

    @Test(timeout = 60_000)
    fun launch_schedulesCrmSyncWork() {
        val context = composeRule.activity
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
        val mainScroll = composeRule.onNodeWithTag("main_scroll")
        mainScroll.performScrollToNode(hasTestTag(crmDetailTag))
        val crmDetailButton = composeRule.onNodeWithTag(crmDetailTag)
        composeRule.waitUntil(15_000) {
            runCatching {
                crmDetailButton.assertExists()
                true
            }.getOrDefault(false)
        }
        crmDetailButton.performClick()
        composeRule.onNodeWithTag("crm_detail_back").assertIsDisplayed()
        val detailScroll = composeRule.onNodeWithTag("crm_detail_scroll")
        detailScroll.performScrollToNode(hasText("Açık takipler"))
        composeRule.onNodeWithText("Açık takipler").assertIsDisplayed()
        detailScroll.performScrollToNode(hasText("Aktivite geçmişi"))
        composeRule.onNodeWithText("Aktivite geçmişi").assertIsDisplayed()
    }

    @Test(timeout = 60_000)
    fun navigationBackForwardAndNewSections_areReachable() {
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Manuel nokta").performClick()
        composeRule.onNodeWithText("Manuel Nokta Kaydı").assertIsDisplayed()

        composeRule.onNodeWithText("← Geri").performClick()
        composeRule.onNodeWithText("Satış Radarı").assertIsDisplayed()

        composeRule.onNodeWithText("İleri →").performClick()
        composeRule.onNodeWithText("Manuel Nokta Kaydı").assertIsDisplayed()

        composeRule.onNodeWithText("Rutin").performClick()
        composeRule.onNodeWithText("Yakınlık Bazlı Rutin").assertIsDisplayed()
    }

    @Test(timeout = 60_000)
    fun productCatalog_canOpenAndAddManualPrice() {
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Ürün kataloğu").performClick()
        composeRule.onNodeWithText("Ürün Kataloğu").assertIsDisplayed()
        composeRule.onNodeWithTag("product_add_button").performClick()
        composeRule.onNodeWithTag("product_name_input").performTextInput("Smoke Donuk Ürün")
        composeRule.onNodeWithTag("product_price_input").performTextInput("125,50")
        composeRule.onNodeWithTag("product_save_button").performClick()
        composeRule.onNodeWithText("Smoke Donuk Ürün").assertIsDisplayed()
        composeRule.onNodeWithText("125,50 TRY").assertIsDisplayed()
    }
}

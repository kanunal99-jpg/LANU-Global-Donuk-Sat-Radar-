package com.lanu.globaldonuksatisradari

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasTestTag
import androidx.work.WorkManager
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
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

    private fun dumpUiOnFailure(label: String) {
        println("===== COMPOSE UI DIAGNOSTIC: " + label + " =====")
        runCatching {
            composeRule.onRoot(useUnmergedTree = true).printToLog("LANU_SMOKE")
        }.onFailure { failure -> println("UI semantics dump failed: " + failure.message) }
    }

    private fun waitForText(text: String, timeoutMs: Long = 45_000): SemanticsNodeInteraction {
        val matcher = hasText(text, substring = false)
        return runCatching {
            composeRule.waitUntilAtLeastOneExists(matcher, timeoutMs)
            composeRule.onNode(matcher)
        }.getOrElse {
            dumpUiOnFailure("text=" + text)
            throw it
        }
    }

    private fun waitForTag(tag: String, timeoutMs: Long = 45_000): SemanticsNodeInteraction {
        val matcher = hasTestTag(tag)
        return runCatching {
            composeRule.waitUntil(timeoutMs) {
                composeRule.onAllNodes(matcher, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNode(matcher, useUnmergedTree = true)
        }.getOrElse {
            dumpUiOnFailure("tag=" + tag)
            throw it
        }
    }

    @JvmField
    @org.junit.Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test(timeout = 60_000)
    fun launch_showsCoreSalesRadarUi() {
        waitForText("LANU Global Donuk Gıda").assertIsDisplayed()
        waitForText("Satış & CRM Radarı").assertIsDisplayed()
        waitForTag("real_search_button").assertIsDisplayed()
    }

    @Test(timeout = 60_000)
    fun blankSearch_keepsBroadInventoryModeAvailable() {
        waitForText("Boş bırakırsanız seçilen şehir/ilçe için OSM işletme envanteri taranır; hedefli kategori aramalarında çiğköfte, cafe, restoran, catering, PlayStation ve daha fazlası desteklenir.").assertExists()
        waitForText("İşletme envanteri filtreleri").assertExists()
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
        waitForTag("product_add_button").performClick()
        waitForTag("product_name_input").performTextInput("Smoke Donuk Ürün")
        waitForTag("product_price_input").performTextInput("125,50")
        waitForTag("product_save_button").performClick()
        waitForText("Smoke Donuk Ürün").assertExists()
        waitForText("125,50 TRY").assertExists()
    }
}

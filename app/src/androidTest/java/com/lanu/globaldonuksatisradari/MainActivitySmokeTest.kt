package com.lanu.globaldonuksatisradari

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performScrollToNode
import com.lanu.globaldonuksatisradari.crm.LanuCrmDatabase
import com.lanu.globaldonuksatisradari.crm.LocalCrmRepository
import com.lanu.globaldonuksatisradari.data.DataSourceDescriptor
import com.lanu.globaldonuksatisradari.data.VerifiedBusiness
import kotlinx.coroutines.runBlocking
import androidx.work.WorkManager
import org.junit.Assert.assertEquals
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
        composeRule.onNodeWithText("Gerçek kaynaktan ara").performClick()
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

        composeRule
            .onNodeWithTag("main_scroll")
            .performScrollToNode(hasText("Smoke CRM Kafe"))
        composeRule.onNodeWithText("Smoke CRM Kafe").assertIsDisplayed()
        composeRule.onNodeWithText("CRM detayını aç").performClick()
        composeRule.onNodeWithTag("crm_detail_back").assertIsDisplayed()
        composeRule.onNodeWithText("Açık takipler").assertIsDisplayed()
        composeRule.onNodeWithText("Aktivite geçmişi").assertIsDisplayed()
        }
    }
}

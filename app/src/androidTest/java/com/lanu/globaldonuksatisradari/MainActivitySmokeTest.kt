package com.lanu.globaldonuksatisradari

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
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

@RunWith(AndroidJUnit4::class)
class MainActivitySmokeTest {

    @get:org.junit.Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun launch_showsCoreSalesRadarUi() {
        composeRule.waitForIdle()
        composeRule.onNodeWithText("LANU Global Donuk Satış Radarı").assertIsDisplayed()
        composeRule.onNodeWithText("Satış Radarı").assertIsDisplayed()
        composeRule.onNodeWithText("Gerçek kaynaktan ara").assertIsDisplayed()
    }

    @Test
    fun blankSearch_showsValidationMessage() {
        composeRule.onNodeWithText("Gerçek kaynaktan ara").performClick()
        composeRule.onNodeWithText("Arama için bir işletme/HORECA terimi yazın.").assertIsDisplayed()
    }

    @Test
    fun launch_schedulesCrmSyncWork() {
        val context = composeRule.activity
        val infos = WorkManager
            .getInstance(context)
            .getWorkInfosForUniqueWork("lanu_global_donuk_crm_sync")
            .get()

        assertFalse("CRM sync work should be scheduled on Activity launch", infos.isEmpty())
        assertTrue(infos.first().state.name == "ENQUEUED" || infos.first().state.name == "RUNNING")
    }

    @Test
    fun launch_createsStableActivity() {
        assertNotNull(composeRule.activity)
    }

    @Test
    fun persistedCrmCustomer_opensRealDetailWorkflow() = runBlocking {
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

        composeRule.waitForIdle()
        composeRule
            .onNodeWithTag("main_scroll")
            .performScrollToNode(hasText("Smoke CRM Kafe • PROSPECT"))
        composeRule.onNodeWithText("Smoke CRM Kafe • PROSPECT").assertIsDisplayed().performClick()
        composeRule.onNodeWithText("CRM listesine dön").assertIsDisplayed()
        composeRule.onNodeWithText("Açık takipler").assertIsDisplayed()
        composeRule.onNodeWithText("Aktivite geçmişi").assertIsDisplayed()
    }
}

package com.lanu.globaldonuksatisradari

import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.WorkManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivitySmokeTest {

    @get:org.junit.Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun launch_showsCoreSalesRadarUi() {
        composeRule.onNodeWithText("LANU Global Donuk Satış Radarı").assertIsDisplayed()
        composeRule.onNodeWithText("Satış Radarı").assertIsDisplayed()
        composeRule.onNodeWithText("Gerçek kaynaktan ara").assertIsDisplayed()
        composeRule.onNodeWithText("Yerel CRM").assertExists()
        composeRule.onNodeWithText("Veri sınırı").assertExists()
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
}

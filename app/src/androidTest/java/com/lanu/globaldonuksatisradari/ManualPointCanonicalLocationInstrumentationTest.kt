package com.lanu.globaldonuksatisradari

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalTestApi::class)
class ManualPointCanonicalLocationInstrumentationTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test(timeout = 60_000)
    fun manualPointUsesCanonicalProvinceAndDistrictSelectors() {
        composeRule.activity
            .getSharedPreferences("lanu_ui_state", android.content.Context.MODE_PRIVATE)
            .edit()
            .putString("selected_city", "İstanbul Anadolu")
            .putString("selected_district", "Kadıköy")
            .apply()

        composeRule.activityRule.scenario.recreate()

        composeRule.onNode(hasTestTag("nav_more")).performClick()
        composeRule.onNode(hasTestTag("more_manual_point")).performClick()

        composeRule.waitUntil(15_000) {
            runCatching {
                composeRule.onNode(hasTestTag("manual_point_screen")).assertIsDisplayed()
                true
            }.getOrDefault(false)
        }

        composeRule.onNode(hasTestTag("manual_city_filter"))
            .assertIsDisplayed()
            .assertHasClickAction()
            .assertTextContains("İstanbul")

        composeRule.onNode(hasTestTag("manual_district_filter"))
            .assertIsDisplayed()
            .assertHasClickAction()
            .assertTextContains("Kadıköy")
    }
}

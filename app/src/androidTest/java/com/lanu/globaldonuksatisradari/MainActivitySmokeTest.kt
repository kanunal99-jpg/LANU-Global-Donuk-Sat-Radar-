package com.lanu.globaldonuksatisradari

import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.work.WorkManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lanu.globaldonuksatisradari.crm.LanuCrmDatabase
import com.lanu.globaldonuksatisradari.crm.CrmReminderRecoveryScheduler
import com.lanu.globaldonuksatisradari.crm.LocalCrmRepository
import com.lanu.globaldonuksatisradari.data.DataSourceDescriptor
import com.lanu.globaldonuksatisradari.data.VerifiedBusiness
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalTestApi::class)
class MainActivitySmokeTest {
    @Test(timeout = 60_000)
    fun mapFallback_rendersWithoutNetworkReadiness() {
        val source = DataSourceDescriptor(
            id = "map-fallback-smoke",
            name = "Smoke Source",
            publisher = "LANU",
            licenseOrTerms = "Test",
            sourceUrl = "https://example.com/",
            lastVerifiedAtEpochMs = 1L,
        )
        val business = VerifiedBusiness(
            id = "map-fallback-point",
            name = "Fallback Nokta",
            city = "İstanbul",
            district = "Kadıköy",
            neighborhood = "Moda",
            source = source,
            verifiedAtEpochMs = 1L,
            latitude = 40.987,
            longitude = 29.028,
        )

        composeRule.activity.runOnUiThread {
            composeRule.activity.setContent {
                MaterialTheme {
                    BusinessMapPreview(listOf(business))
                }
            }
        }
        composeRule.waitForIdle()
        waitForTag("business_map_container").assertIsDisplayed()
        waitForTag("business_map_fallback").assertIsDisplayed()
    }

    @Test(timeout = 60_000)
    fun launch_schedulesCrmReminderRecovery() {
        val context = composeRule.activity
        composeRule.waitUntil(30_000) {
            runCatching {
                WorkManager.getInstance(context)
                    .getWorkInfosForUniqueWork(CrmReminderRecoveryScheduler.uniqueWorkName())
                    .get()
                    .isNotEmpty()
            }.getOrDefault(false)
        }
        assertTrue(
            WorkManager.getInstance(context)
                .getWorkInfosForUniqueWork(CrmReminderRecoveryScheduler.uniqueWorkName())
                .get()
                .isNotEmpty(),
        )
    }



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
                    // LazyColumn items that are off-screen are not part of the active semantics tree.
                    // Retry the lazy-list scroll itself while Room/Flow state is being collected instead
                    // of waiting for an off-screen node to exist before scrolling to it.
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
        waitForTag("nav_crm").assertHasClickAction().performClick()
        waitForTag("crm_tab_customers").assertHasClickAction().performClick()
        waitForText("Smoke CRM Kafe").assertIsDisplayed()
        waitForTag("crm_open_instrumentation-ui-crm-detail")
            .assertIsDisplayed()
            .assertHasClickAction()
            .performClick()
        waitForTag("crm_detail_back").assertIsDisplayed()
        waitForText("Açık takipler").assertExists()
        waitForText("Aktivite geçmişi").assertExists()
    }

    @Test(timeout = 60_000)
    fun navigationBackForwardAndNewSections_areReachable() {
        waitForTag("nav_map").assertHasClickAction().performClick()
        waitForText("CRM Haritası").assertIsDisplayed()

        waitForText("← Geri").performClick()
        waitForText("Satış & CRM Radarı").assertExists()

        waitForText("İleri →").performClick()
        waitForText("CRM Haritası").assertExists()

        waitForTag("nav_crm").assertHasClickAction().performClick()
        waitForTag("crm_today_screen").assertIsDisplayed()

        waitForText("Rutin").performClick()
        waitForText("Yakınlık Bazlı Rutin").assertIsDisplayed()
    }

    @Test(timeout = 60_000)
    fun istanbulSideSelectors_exposeOnlyTheirOwnAcceptanceDistricts() {
        val prefs = composeRule.activity.getSharedPreferences(
            "lanu_ui_state",
            android.content.Context.MODE_PRIVATE,
        )

        prefs.edit()
            .putString("selected_city", "İstanbul Avrupa")
            .putString("selected_district", "Tümü")
            .apply()
        composeRule.activityRule.scenario.recreate()
        waitForTag("city_filter").assertIsDisplayed()
        waitForText("İstanbul Avrupa").assertIsDisplayed()
        waitForTag("district_filter").assertHasClickAction().performClick()
        waitForText("Şişli").assertExists()
        waitForText("Bakırköy").assertExists()
        composeRule.onNodeWithText("Kadıköy", useUnmergedTree = true).assertDoesNotExist()
        waitForText("Şişli").performClick()
        waitForText("Şişli").assertIsDisplayed()

        prefs.edit()
            .putString("selected_city", "İstanbul Anadolu")
            .putString("selected_district", "Tümü")
            .apply()
        composeRule.activityRule.scenario.recreate()
        waitForTag("city_filter").assertIsDisplayed()
        waitForText("İstanbul Anadolu").assertIsDisplayed()
        waitForTag("district_filter").assertHasClickAction().performClick()
        waitForText("Kadıköy").assertExists()
        waitForText("Pendik").assertExists()
        composeRule.onNodeWithText("Şişli", useUnmergedTree = true).assertDoesNotExist()
        waitForText("Kadıköy").performClick()
        waitForText("Kadıköy").assertIsDisplayed()
        waitForTag("neighborhood_filter").assertIsDisplayed()
    }

    @Test(timeout = 60_000)
    fun duplicateReview_mergesSelectedRecordFromUi() {
        var targetId = ""
        var sourceId = ""
        runBlocking {
            val context = composeRule.activity
            val repository = LocalCrmRepository(LanuCrmDatabase.getInstance(context))
            val suffix = System.nanoTime().toString()
            targetId = repository.addManualCustomerPoint(
                businessName = "UI Mükerrer Market $suffix",
                address = "UI Hedef $suffix",
                city = "İstanbul",
                district = "Kadıköy",
                neighborhood = "Caferağa",
                latitude = 40.9870,
                longitude = 29.0280,
                phone = "05321112233",
            ).id
            sourceId = repository.addManualCustomerPoint(
                businessName = "UI MÜKERRER MARKET $suffix",
                address = "UI Kaynak $suffix",
                city = "İstanbul",
                district = "Kadıköy",
                neighborhood = "Caferağa",
                latitude = 40.9871,
                longitude = 29.0281,
                phone = "+90 532 111 22 33",
            ).id
        }

        composeRule.activity.getSharedPreferences(
            "lanu_ui_state",
            android.content.Context.MODE_PRIVATE,
        ).edit()
            .putString("selected_city", "İstanbul Anadolu")
            .putString("selected_district", "Kadıköy")
            .apply()
        composeRule.activityRule.scenario.recreate()

        waitForTag("nav_crm").assertHasClickAction().performClick()
        waitForTag("crm_tab_duplicates").assertHasClickAction().performClick()
        waitForTag("crm_duplicate_screen").assertIsDisplayed()
        waitForTag("crm_merge_keep_" + targetId)
            .performScrollTo()
            .assertHasClickAction()
            .performClick()
        waitForTag("crm_duplicate_confirm")
            .assertIsDisplayed()
            .assertHasClickAction()
            .performClick()

        composeRule.waitUntil(30_000) {
            runBlocking {
                LocalCrmRepository(LanuCrmDatabase.getInstance(composeRule.activity))
                    .observeCustomers("İstanbul")
                    .first()
                    .none { it.id == sourceId }
            }
        }
        assertTrue(
            LocalCrmRepository(LanuCrmDatabase.getInstance(composeRule.activity))
                .observeCustomers("İstanbul")
                .first()
                .any { it.id == targetId },
        )
    }

    @Test(timeout = 60_000)
    fun manualPoint_rejectsMissingRequiredFieldsWithoutWriting() {
        waitForTag("nav_more").assertHasClickAction().performClick()
        waitForTag("more_manual_point").assertHasClickAction().performClick()
        waitForText("Manuel Nokta Kaydı").assertIsDisplayed()

        waitForTag("manual_point_save")
            .performScrollTo()
            .assertIsDisplayed()
            .assertHasClickAction()
            .performClick()
        composeRule.waitForIdle()

        waitForText("Ad, adres, il ve ilçe zorunludur.")
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test(timeout = 60_000)
    fun bulkCrmSave_deduplicatesSourceRecords() {
        runBlocking {
            val context = composeRule.activity
            val repository = LocalCrmRepository(LanuCrmDatabase.getInstance(context))
            val source = DataSourceDescriptor(
                id = "osm-overpass",
                name = "OpenStreetMap Overpass",
                publisher = "OpenStreetMap",
                licenseOrTerms = "ODbL",
                sourceUrl = "https://overpass-api.de/api/interpreter",
                lastVerifiedAtEpochMs = 1L,
            )
            val business = VerifiedBusiness(
                id = "bulk-smoke-1",
                name = "Toplu CRM Smoke",
                city = "İstanbul",
                district = "Kadıköy",
                neighborhood = "Caferağa",
                source = source,
                verifiedAtEpochMs = 1L,
                phone = "05550000000",
                category = "Kafe",
            )

            val first = repository.addBusinessesAsCustomers(listOf(business, business))
            val second = repository.addBusinessesAsCustomers(listOf(business))

            assertTrue(first.inserted == 1)
            assertTrue(second.alreadyExisting == 1)
            val saved = repository.observeCustomers("İstanbul").first().first { it.businessSourceId == "bulk-smoke-1" }
            assertTrue(saved.phone == "05550000000")
            assertTrue(saved.businessType == "Kafe")
        }
    }

    @Test(timeout = 60_000)
    fun crmMap_showsPersistedCoordinatePointAndOpensDetail() {
        var customerId = ""
        runBlocking {
            val context = composeRule.activity
            val repository = LocalCrmRepository(LanuCrmDatabase.getInstance(context))
            customerId = repository.addManualCustomerPoint(
                businessName = "Harita Smoke Nokta",
                address = "Moda Test Adres",
                city = "İstanbul",
                district = "Kadıköy",
                neighborhood = "Caferağa",
                latitude = 40.987,
                longitude = 29.028,
                signboardName = "Harita Smoke Tabela",
            ).id
        }

        composeRule.activity.getSharedPreferences("lanu_ui_state", android.content.Context.MODE_PRIVATE)
            .edit()
            .putString("selected_city", "İstanbul Anadolu")
            .putString("selected_district", "Tümü")
            .apply()
        composeRule.activityRule.scenario.recreate()
        waitForTag("nav_map").assertHasClickAction().performClick()
        waitForTag("crm_map_screen").assertIsDisplayed()
        composeRule.onNodeWithTag("crm_map_screen")
            .performScrollToNode(hasTestTag("crm_map_open_" + customerId))
        composeRule.waitForIdle()
        waitForText("Harita Smoke Nokta").assertIsDisplayed()
        waitForTag("crm_map_open_" + customerId)
            .assertIsDisplayed()
            .assertHasClickAction()
            .performClick()
        waitForTag("crm_detail_back").assertIsDisplayed()
    }

    @Test(timeout = 60_000)
    fun crmExcelActions_areAvailableForPersistedPoints() {
        runBlocking {
            val context = composeRule.activity
            val repository = LocalCrmRepository(LanuCrmDatabase.getInstance(context))
            repository.addManualCustomerPoint(
                businessName = "Excel Smoke Nokta",
                address = "Test adres",
                city = "İstanbul",
                district = "Kadıköy",
                neighborhood = "Caferağa",
                latitude = 40.99,
                longitude = 29.03,
                contactName = "Test Kullanıcı",
                businessType = "Restoran",
                taxOrNationalId = "1234567890",
                phone = "05551112233",
            )
        }

        composeRule.activityRule.scenario.recreate()
        waitForTag("nav_crm").assertHasClickAction().performClick()
        waitForTag("crm_tab_customers").assertHasClickAction().performClick()
        waitForTag("crm_excel_save").performScrollTo().assertIsDisplayed().assertHasClickAction()
        waitForTag("crm_excel_share").assertIsDisplayed().assertHasClickAction()
    }

    @Test(timeout = 60_000)
    fun routineMonthlyExcelActions_areAvailableForRoutablePoints() {
        runBlocking {
            val context = composeRule.activity
            val repository = LocalCrmRepository(LanuCrmDatabase.getInstance(context))
            repository.addManualCustomerPoint(
                businessName = "Rutin Excel Smoke",
                address = "Test adres",
                city = "İstanbul",
                district = "Kadıköy",
                neighborhood = "Caferağa",
                latitude = 40.99,
                longitude = 29.03,
                contactName = "Rutin Test",
                businessType = "Restoran",
                taxOrNationalId = "1111111111",
                phone = "05551112233",
            )
        }

        composeRule.activityRule.scenario.recreate()
        waitForText("Rutin").performClick()
        waitForText("Otomatik Aylık Ziyaret Planı").assertIsDisplayed()
        waitForTag("routine_excel_save")
            .performScrollTo()
            .assertIsDisplayed()
            .assertHasClickAction()
        waitForTag("routine_excel_share")
            .performScrollTo()
            .assertIsDisplayed()
            .assertHasClickAction()
    }

    @Test(timeout = 60_000)
    fun aiAssistant_localFallbackWorksWithoutApiKey() {
        waitForTag("nav_more").assertHasClickAction().performClick()
        waitForTag("more_ai").assertHasClickAction().performClick()
        waitForTag("ai_screen").assertIsDisplayed()
        waitForTag("ai_local_summary")
            .performScrollTo()
            .assertIsDisplayed()
            .assertHasClickAction()
            .performClick()
        composeRule.waitForIdle()
        waitForTag("ai_answer")
            .performScrollTo()
            .assertIsDisplayed()
        waitForText("Ücretsiz yerel mod")
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test(timeout = 60_000)
    fun productCatalog_canOpenAndAddManualPrice() {
        waitForTag("nav_more").assertHasClickAction().performClick()
        waitForTag("more_products").assertHasClickAction().performClick()
        waitForText("Ürün Kataloğu").assertIsDisplayed()

        val addButton = waitForTag("product_add_button").assertIsDisplayed().assertHasClickAction()
        addButton.performClick()
        composeRule.waitForIdle()

        waitForTag("product_editor_dialog").assertIsDisplayed()
        waitForTag("product_name_input").assertIsDisplayed().performTextInput("Smoke Donuk Ürün")
        waitForTag("product_price_input").assertIsDisplayed().performTextInput("125,50")
        waitForTag("product_image_url_input").performScrollTo().assertIsDisplayed()
        waitForTag("product_image_gallery").performScrollTo().assertIsDisplayed().assertHasClickAction()
        waitForTag("product_image_camera").performScrollTo().assertIsDisplayed().assertHasClickAction()
        waitForTag("product_save_button").assertIsDisplayed().performTouchInput { click() }

        waitForText("Smoke Donuk Ürün").assertExists()
        waitForText("125,50 TRY").assertExists()

        val saved = ProductCatalogRepository(composeRule.activity)
            .products.value
            .first { it.name == "Smoke Donuk Ürün" }
        assertNull("Manual product save must not claim verified source provenance", saved.sourceVerifiedAtEpochMs)
    }
}

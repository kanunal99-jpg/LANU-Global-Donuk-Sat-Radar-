package com.lanu.globaldonuksatisradari

import android.content.Context
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.lanu.globaldonuksatisradari.crm.CommercialCrmRepository
import com.lanu.globaldonuksatisradari.crm.CrmActivityType
import com.lanu.globaldonuksatisradari.crm.CrmDashboardMetrics
import com.lanu.globaldonuksatisradari.crm.CrmNextActionType
import com.lanu.globaldonuksatisradari.crm.CrmOpportunityStatus
import com.lanu.globaldonuksatisradari.crm.CrmStage
import com.lanu.globaldonuksatisradari.crm.CrmSyncScheduler
import com.lanu.globaldonuksatisradari.crm.CrmNextActionReminderScheduler
import com.lanu.globaldonuksatisradari.crm.CrmReminderRecoveryScheduler
import com.lanu.globaldonuksatisradari.crm.SupabaseAuthClient
import com.lanu.globaldonuksatisradari.crm.LanuCrmDatabase
import com.lanu.globaldonuksatisradari.crm.LocalCrmRepository
import com.lanu.globaldonuksatisradari.data.BusinessCategoryLabels
import com.lanu.globaldonuksatisradari.data.BusinessEntityEligibility
import com.lanu.globaldonuksatisradari.data.BusinessDeduplication
import com.lanu.globaldonuksatisradari.data.BusinessQualityEvaluator
import com.lanu.globaldonuksatisradari.data.DistrictCatalogRepository
import com.lanu.globaldonuksatisradari.data.NeighborhoodCatalogRepository
import com.lanu.globaldonuksatisradari.data.OfficialRegistryStore
import com.lanu.globaldonuksatisradari.data.CoverageBusinessRepository
import com.lanu.globaldonuksatisradari.data.VerifiedBusiness
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout


data class City(
    val name: String,
    val districts: List<String>,
    val label: String = name,
)

enum class AppSection {
    RADAR,
    MAP,
    CRM,
    ROUTINE,
    MORE,
    PRODUCT_CATALOG,
    MANUAL_POINT,
    AI_ASSISTANT,
}

private val cities = TurkeyCityCatalog.ALL.flatMap { entry ->
    if (entry.name == "İstanbul") {
        listOf(
            City("İstanbul", IstanbulDistricts.ANATOLIAN, "İstanbul Anadolu"),
            City("İstanbul", IstanbulDistricts.EUROPEAN, "İstanbul Avrupa"),
        )
    } else {
        listOf(City(entry.name, entry.fallbackDistricts))
    }
}
private fun matchesInventoryPresence(value: String?, filter: String): Boolean = when (filter) {
    "Tümü" -> true
    "Var" -> !value.isNullOrBlank()
    "Yok" -> value.isNullOrBlank()
    else -> true
}

private fun normalizeNeighborhoodLabel(value: String?): String {
    var normalized = BusinessDeduplication.normalizeForComparison(value.orEmpty())
    listOf(" mahallesi", " mahallesi.", " mah.", " mah").forEach { suffix ->
        if (normalized.endsWith(suffix)) {
            normalized = normalized.removeSuffix(suffix).trim()
        }
    }
    return normalized
}

internal fun scopeCrmCustomersForCitySelection(
    customers: List<com.lanu.globaldonuksatisradari.crm.CrmCustomer>,
    city: City,
    selectedDistrict: String,
): List<com.lanu.globaldonuksatisradari.crm.CrmCustomer> {
    val cityScoped = customers.filter { it.city.equals(city.name, ignoreCase = true) }
    if (selectedDistrict != "Tümü") {
        if (city.name == "İstanbul" &&
            city.districts.none { it.equals(selectedDistrict, ignoreCase = true) }
        ) {
            return emptyList()
        }
        return cityScoped.filter { it.district.equals(selectedDistrict, ignoreCase = true) }
    }
    if (city.name == "İstanbul") {
        val allowed = city.districts
            .map { BusinessDeduplication.normalizeForComparison(it) }
            .toSet()
        return cityScoped.filter {
            BusinessDeduplication.normalizeForComparison(it.district) in allowed
        }
    }
    return cityScoped
}

internal fun businessMatchesCitySelection(
    business: VerifiedBusiness,
    city: City,
    selectedDistrict: String,
): Boolean {
    if (!business.city.equals(city.name, ignoreCase = true)) return false
    if (selectedDistrict != "Tümü") {
        if (city.name == "İstanbul" &&
            city.districts.none { it.equals(selectedDistrict, ignoreCase = true) }
        ) {
            return false
        }
        return business.district.equals(selectedDistrict, ignoreCase = true)
    }
    if (city.name != "İstanbul") return true
    val districtKey = BusinessDeduplication.normalizeForComparison(business.district)
    return city.districts.any {
        BusinessDeduplication.normalizeForComparison(it) == districtKey
    }
}

class MainActivity : ComponentActivity() {
    private fun isInstrumentationTest(): Boolean = runCatching { Class.forName("androidx.test.platform.app.InstrumentationRegistry") }.isSuccess
    private lateinit var auth: SupabaseAuthClient
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        auth = SupabaseAuthClient(this)
        val cloudAuth = if (isInstrumentationTest()) null else auth
        setContent { SalesRadarApp(cloudAuth) }
        lifecycleScope.launch(Dispatchers.IO) {
            runCatching { CrmSyncScheduler.schedule(applicationContext) }
            runCatching { CrmReminderRecoveryScheduler.schedule(applicationContext) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SalesRadarApp(auth: SupabaseAuthClient? = null) {
    val context = LocalContext.current
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }
    fun ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    val uiPreferences = remember(context) {
        context.getSharedPreferences("lanu_ui_state", Context.MODE_PRIVATE)
    }
    val initialCity = remember {
        val savedCity = uiPreferences.getString("selected_city", null)
        cities.firstOrNull { it.label.equals(savedCity, ignoreCase = true) }
            ?: cities.firstOrNull { it.label == "İstanbul Anadolu" }
            ?: cities.first()
    }
    var selectedCity by remember { mutableStateOf(initialCity) }
    var section by remember { mutableStateOf(AppSection.RADAR) }
    val backStack = remember { mutableStateListOf<AppSection>() }
    val forwardStack = remember { mutableStateListOf<AppSection>() }
    fun navigateTo(target: AppSection) { if (target != section) { backStack.add(section); section = target; forwardStack.clear() } }
    fun goBack() { backStack.removeLastOrNull()?.let { forwardStack.add(section); section = it } }
    fun goForward() { forwardStack.removeLastOrNull()?.let { backStack.add(section); section = it } }

    var cityMenu by remember { mutableStateOf(false) }
    var districtMenu by remember { mutableStateOf(false) }
    var availableDistricts by remember { mutableStateOf(selectedCity.districts) }
    var districtLoading by remember { mutableStateOf(false) }
    var availableNeighborhoods by remember { mutableStateOf<List<String>>(emptyList()) }
    var neighborhoodLoading by remember { mutableStateOf(false) }
    var neighborhoodMenu by remember { mutableStateOf(false) }
    var selectedDistrict by remember {
        mutableStateOf(
            uiPreferences.getString("selected_district", "Tümü")
                ?.takeIf(String::isNotBlank)
                ?: "Tümü",
        )
    }
    var selectedNeighborhood by remember { mutableStateOf("Tümü") }
    var categoryFilter by remember { mutableStateOf("Tümü") }
    var phoneFilter by remember { mutableStateOf("Tümü") }
    var websiteFilter by remember { mutableStateOf("Tümü") }
    var addressFilter by remember { mutableStateOf("Tümü") }
    var coordinateFilter by remember { mutableStateOf("Tümü") }
    var menuFilter by remember { mutableStateOf("Tümü") }
    var openingHoursFilter by remember { mutableStateOf("Tümü") }
    var showFilterDetails by rememberSaveable { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<VerifiedBusiness>>(emptyList()) }
    var selectedBusiness by remember { mutableStateOf<VerifiedBusiness?>(null) }
    var selectedCustomerId by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var bulkSaving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var crmMessage by remember { mutableStateOf<String?>(null) }
    var scanDelta by remember { mutableStateOf<RadarScanDelta?>(null) }
    var newBusinessKeys by remember { mutableStateOf<Set<String>>(emptySet()) }
    var searchRequestId by remember { mutableLongStateOf(0L) }
    fun invalidateSearch() {
        searchRequestId++
        loading = false
        scanDelta = null
        newBusinessKeys = emptySet()
    }

    BackHandler(enabled = selectedCustomerId != null || backStack.isNotEmpty()) { if (selectedCustomerId != null) selectedCustomerId = null else goBack() }
    val scope = rememberCoroutineScope()
    val repository = remember(context) { CoverageBusinessRepository(context) }
    val districtRepository = remember(context) { DistrictCatalogRepository(context) }
    val neighborhoodRepository = remember(context) { NeighborhoodCatalogRepository(context) }
    val scanHistoryRepository = remember(context) { RadarScanHistoryRepository(context) }
    LaunchedEffect(selectedCity.label) {
        districtLoading = true
        availableDistricts = runCatching {
            val fetched = districtRepository.getDistricts(selectedCity.name, selectedCity.districts)
            if (selectedCity.name == "İstanbul" && selectedCity.districts.isNotEmpty()) {
                fetched.filter { district ->
                    selectedCity.districts.any { it.equals(district, ignoreCase = true) }
                }.ifEmpty { selectedCity.districts }
            } else {
                fetched
            }
        }.getOrElse { throwable ->
            if (throwable is CancellationException) throw throwable
            Log.w("LanuRadar", "İlçe kataloğu yenilenemedi; yerel liste kullanılıyor.", throwable)
            selectedCity.districts
        }
        if (selectedDistrict != "Tümü" &&
            availableDistricts.none { it.equals(selectedDistrict, ignoreCase = true) }
        ) {
            selectedDistrict = "Tümü"
        }
        districtLoading = false
    }
    LaunchedEffect(selectedCity.label, selectedDistrict) {
        selectedNeighborhood = "Tümü"
        neighborhoodMenu = false
        availableNeighborhoods = emptyList()
        if (selectedDistrict == "Tümü") {
            neighborhoodLoading = false
            return@LaunchedEffect
        }
        neighborhoodLoading = true
        availableNeighborhoods = runCatching {
            neighborhoodRepository.getNeighborhoods(selectedCity.name, selectedDistrict)
        }.getOrElse { throwable ->
            if (throwable is CancellationException) throw throwable
            Log.w("LanuRadar", "Mahalle kataloğu yenilenemedi: ${selectedCity.name}/$selectedDistrict", throwable)
            emptyList()
        }
        neighborhoodLoading = false
    }

    LaunchedEffect(selectedCity.name, selectedDistrict) {
        uiPreferences.edit()
            .putString("selected_city", selectedCity.label)
            .putString("selected_district", selectedDistrict)
            .apply()
    }

    val crmDatabase = remember(context) { LanuCrmDatabase.getInstance(context) }
    val localCrmRepository = remember(crmDatabase) { LocalCrmRepository(crmDatabase) }
    val productCatalogRepository = remember(context) { ProductCatalogRepository(context) }
    val officialRegistryStore = remember(context) { OfficialRegistryStore(context) }

    val cloudSessionState = auth?.session?.collectAsState()
    val activeOwnerUserId = cloudSessionState?.value?.userId
    val commercialCrmRepository = remember(crmDatabase, activeOwnerUserId) {
        CommercialCrmRepository(
            database = crmDatabase,
            ownerUserId = activeOwnerUserId,
        )
    }
    val allCrmCustomers by localCrmRepository.observeCustomers(null).collectAsState(initial = emptyList())
    val crmCustomers = remember(allCrmCustomers, activeOwnerUserId) {
        val ownerScoped = if (activeOwnerUserId == null) {
            allCrmCustomers.filter { it.ownerUserId == null }
        } else {
            allCrmCustomers.filter { it.ownerUserId == activeOwnerUserId }
        }
        ownerScoped.filter { customer ->
            customer.dataQuality != DataQuality.OBSERVED ||
                BusinessEntityEligibility.keepCategory(customer.businessType)
        }
    }
    val pendingSyncFlow = remember(localCrmRepository, activeOwnerUserId) {
        localCrmRepository.observePendingSyncCount(activeOwnerUserId)
    }
    val pendingSyncCount by pendingSyncFlow.collectAsState(initial = 0)
    val crmRegistryCities = remember(crmCustomers) {
        crmCustomers.map { it.city.trim() }
            .filter(String::isNotBlank)
            .distinct()
            .sorted()
    }
    LaunchedEffect(crmRegistryCities, activeOwnerUserId) {
        if (crmRegistryCities.isEmpty()) return@LaunchedEffect
        runCatching {
            val records = withContext(Dispatchers.IO) {
                crmRegistryCities.flatMap { city -> officialRegistryStore.recordsFor(city, null) }
            }
            if (records.isEmpty()) return@runCatching null
            localCrmRepository.enrichCustomersFromOfficialRegistryForOwner(
                records = records,
                ownerUserId = activeOwnerUserId,
            )
        }.onSuccess { enriched ->
            if (enriched != null && enriched.updated > 0) {
                crmMessage = "Mevcut resmî sicil kayıtları CRM'e uygulandı: " +
                    "${enriched.updated} müşteri adres/telefon kaydı güncellendi."
            }
        }.onFailure { error ->
            Log.w("LanuRegistry", "Başlangıç resmî sicil zenginleştirmesi tamamlanamadı.", error)
        }
    }
    val filteredCrmCustomers = remember(crmCustomers, selectedCity.label, selectedDistrict) {
        scopeCrmCustomersForCitySelection(crmCustomers, selectedCity, selectedDistrict)
    }
    val selectedCrmCustomer = selectedCustomerId?.let { id -> crmCustomers.firstOrNull { it.id == id } }
    val selectedCustomerKey = selectedCustomerId.orEmpty()
    val selectedCustomerActivities by remember(selectedCustomerKey) { localCrmRepository.observeActivities(selectedCustomerKey) }.collectAsState(initial = emptyList())
    val selectedCustomerNextActions by remember(selectedCustomerKey) { localCrmRepository.observeNextActions(selectedCustomerKey) }.collectAsState(initial = emptyList())
    val selectedCustomerTransitions by remember(selectedCustomerKey) { localCrmRepository.observeStageTransitions(selectedCustomerKey) }.collectAsState(initial = emptyList())
    val selectedCustomerOpportunities by remember(selectedCustomerKey) { localCrmRepository.observeOpportunities(selectedCustomerKey) }.collectAsState(initial = emptyList())

    val regionKey = "${selectedCity.name}|$selectedDistrict"
    val regionDistrict = selectedDistrict.takeUnless { it == "Tümü" }
    val allRegionActivities by remember(regionKey) {
        localCrmRepository.observeActivitiesForRegion(selectedCity.name, regionDistrict)
    }.collectAsState(initial = emptyList())
    val allRegionNextActions by remember(regionKey) {
        localCrmRepository.observeOpenNextActionsForRegion(selectedCity.name, regionDistrict)
    }.collectAsState(initial = emptyList())
    val allRegionOpportunities by remember(regionKey) {
        localCrmRepository.observeOpportunitiesForRegion(selectedCity.name, regionDistrict)
    }.collectAsState(initial = emptyList())
    val regionCustomerIds = remember(filteredCrmCustomers) {
        filteredCrmCustomers.mapTo(mutableSetOf()) { it.id }
    }
    val regionActivities = remember(allRegionActivities, regionCustomerIds) {
        allRegionActivities.filter { it.customerId in regionCustomerIds }
    }
    val regionNextActions = remember(allRegionNextActions, regionCustomerIds) {
        allRegionNextActions.filter { it.customerId in regionCustomerIds }
    }
    val regionOpportunities = remember(allRegionOpportunities, regionCustomerIds) {
        allRegionOpportunities.filter { it.customerId in regionCustomerIds }
    }
    val presenceOptions = listOf("Tümü", "Var", "Yok")
    val categoryOptions = remember {
        listOf("Tümü") + BusinessCategoryLabels.searchLabels
    }
    val visibleResults = remember(
        results,
        selectedCity.label,
        selectedDistrict,
        selectedNeighborhood,
        categoryFilter,
        phoneFilter,
        websiteFilter,
        addressFilter,
        coordinateFilter,
        menuFilter,
        openingHoursFilter,
    ) {
        results.filter { business ->
            businessMatchesCitySelection(business, selectedCity, selectedDistrict) &&
                (
                    selectedNeighborhood == "Tümü" ||
                        normalizeNeighborhoodLabel(business.neighborhood) ==
                            normalizeNeighborhoodLabel(selectedNeighborhood)
                ) &&
                matchesInventoryPresence(business.phone, phoneFilter) &&
                matchesInventoryPresence(business.website, websiteFilter) &&
                matchesInventoryPresence(business.address, addressFilter) &&
                matchesInventoryPresence(business.openingHours, openingHoursFilter) &&
                matchesInventoryPresence(
                    business.menuUrl?.takeIf(String::isNotBlank) ?: business.menuText,
                    menuFilter,
                ) &&
                when (coordinateFilter) {
                    "Var" -> business.latitude != null && business.longitude != null
                    "Yok" -> business.latitude == null || business.longitude == null
                    else -> true
                }
        }
    }
    val qualitySummary = remember(visibleResults) {
        val scores = visibleResults.map { BusinessQualityEvaluator.evaluate(it).score }
        val sources = visibleResults.flatMap { business ->
            listOf(business.source.name) + listOfNotNull(business.officialRegistryEvidence?.source?.name)
        }.distinct().sorted()
        Triple(if (scores.isEmpty()) 0 else scores.sum() / scores.size, scores.count { it < 65 }, sources)
    }
    val dashboardMetrics = remember(filteredCrmCustomers, regionActivities, regionNextActions, regionOpportunities) {
        CrmDashboardMetrics.from(filteredCrmCustomers, regionActivities, regionNextActions, regionOpportunities)
    }
    val salesAiContext = remember(
        selectedCity.name,
        selectedDistrict,
        crmCustomers,
        visibleResults,
        scanDelta,
    ) {
        SalesAiContext(
            city = selectedCity.name,
            district = selectedDistrict,
            crmCount = filteredCrmCustomers.size,
            prospectCount = filteredCrmCustomers.count { it.stage == CrmStage.PROSPECT },
            activeCustomerCount = filteredCrmCustomers.count { it.stage == CrmStage.ACTIVE_CUSTOMER },
            radarResultCount = visibleResults.size,
            newBusinessCount = scanDelta?.newCount ?: 0,
            sampleBusinessNames = visibleResults.take(8).map { it.name },
        )
    }
    fun resetFilters() {
        selectedNeighborhood = "Tümü"
        categoryFilter = "Tümü"
        phoneFilter = "Tümü"
        websiteFilter = "Tümü"
        addressFilter = "Tümü"
        coordinateFilter = "Tümü"
        menuFilter = "Tümü"
        openingHoursFilter = "Tümü"
        query = ""
    }

    LanuGlobalTheme {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = { LanuBrandLockup(compact = true) },
                    navigationIcon = {
                        if (selectedCrmCustomer != null || backStack.isNotEmpty()) {
                            TextButton(
                                onClick = {
                                    if (selectedCrmCustomer != null) selectedCustomerId = null else goBack()
                                },
                            ) { Text("← Geri") }
                        }
                    },
                    actions = {
                        if (forwardStack.isNotEmpty()) {
                            TextButton(onClick = { goForward() }) { Text("İleri →") }
                        }
                    },
                )
            },
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(
                        selected = section == AppSection.RADAR && selectedCrmCustomer == null,
                        onClick = { selectedCustomerId = null; navigateTo(AppSection.RADAR) },
                        icon = { Text("⌖") },
                        label = { Text("Radar") },
                        modifier = Modifier.testTag("nav_radar"),
                    )
                    NavigationBarItem(
                        selected = section == AppSection.MAP && selectedCrmCustomer == null,
                        onClick = { selectedCustomerId = null; navigateTo(AppSection.MAP) },
                        icon = { Text("◉") },
                        label = { Text("Harita") },
                        modifier = Modifier.testTag("nav_map"),
                    )
                    NavigationBarItem(
                        selected = section == AppSection.CRM && selectedCrmCustomer == null,
                        onClick = { selectedCustomerId = null; navigateTo(AppSection.CRM) },
                        icon = { Text("CRM") },
                        label = { Text("CRM") },
                        modifier = Modifier.testTag("nav_crm"),
                    )
                    NavigationBarItem(
                        selected = section == AppSection.ROUTINE && selectedCrmCustomer == null,
                        onClick = { selectedCustomerId = null; navigateTo(AppSection.ROUTINE) },
                        icon = { Text("↗") },
                        label = { Text("Rutin") },
                        modifier = Modifier.testTag("nav_routine"),
                    )
                    NavigationBarItem(
                        selected = section in setOf(
                            AppSection.MORE,
                            AppSection.PRODUCT_CATALOG,
                            AppSection.MANUAL_POINT,
                            AppSection.AI_ASSISTANT,
                        ) && selectedCrmCustomer == null,
                        onClick = { selectedCustomerId = null; navigateTo(AppSection.MORE) },
                        icon = { Text("•••") },
                        label = { Text("Daha") },
                        modifier = Modifier.testTag("nav_more"),
                    )
                }
            },
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                if (selectedCrmCustomer == null) {
                    when (section) {
                    AppSection.RADAR -> LazyColumn(
                        modifier = Modifier.testTag("main_scroll").padding(horizontal = 16.dp),
                        contentPadding = PaddingValues(vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        item {
                            LanuHeroHeader()
                            Text("Satış & CRM Radarı", style = MaterialTheme.typography.headlineSmall)
                            Text("Gerçek işletmeleri bulun, kaliteyi kontrol edin ve CRM'e aktarın.", style = MaterialTheme.typography.bodyMedium)
                        }
                        item {
                            OutlinedTextField(
                                value = query,
                                onValueChange = { value ->
                                    query = value
                                    if (value.isNotBlank() && categoryFilter != "Tümü") {
                                        invalidateSearch()
                                        categoryFilter = "Tümü"
                                        results = emptyList()
                                        selectedBusiness = null
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("İşletme adı veya kategori ara") },
                                supportingText = {
                                    if (query.isNotBlank()) {
                                        Text("Serbest arama kullanılırken kategori filtresi Tümü olarak uygulanır.")
                                    }
                                },
                                singleLine = true,
                            )
                        }
                        item {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                Box(Modifier.weight(1f)) {
                                    OutlinedButton(onClick = { cityMenu = true }, modifier = Modifier.fillMaxWidth().testTag("city_filter")) { Text(selectedCity.label) }
                                    DropdownMenu(cityMenu, { cityMenu = false }) { cities.forEach { city -> DropdownMenuItem({ Text(city.label) }, onClick = { invalidateSearch(); selectedCity = city; selectedDistrict = "Tümü"; results = emptyList(); resetFilters(); cityMenu = false }) } }
                                }
                                Box(Modifier.weight(1f)) {
                                    OutlinedButton(onClick = { districtMenu = true }, modifier = Modifier.fillMaxWidth().testTag("district_filter")) { Text(if (districtLoading) "Yükleniyor…" else selectedDistrict) }
                                    DropdownMenu(districtMenu, { districtMenu = false }) {
                                        DropdownMenuItem({ Text("Tümü") }, onClick = { invalidateSearch(); selectedDistrict = "Tümü"; results = emptyList(); resetFilters(); districtMenu = false })
                                        availableDistricts.forEach { district -> DropdownMenuItem({ Text(district) }, onClick = { invalidateSearch(); selectedDistrict = district; results = emptyList(); resetFilters(); districtMenu = false }) }
                                    }
                                }
                            }
                        }
                        item {
                            val neighborhoods = remember(availableNeighborhoods) {
                                listOf("Tümü") +
                                    availableNeighborhoods
                                        .filter { it.isNotBlank() }
                                        .distinct()
                                        .sortedWith(String.CASE_INSENSITIVE_ORDER)
                            }
                            val districtSelected = selectedDistrict != "Tümü"
                            Box {
                                OutlinedButton(
                                    onClick = { if (districtSelected && neighborhoods.size > 1) neighborhoodMenu = true },
                                    enabled = districtSelected && (!neighborhoodLoading || neighborhoods.size > 1),
                                    modifier = Modifier.fillMaxWidth().testTag("neighborhood_filter"),
                                ) {
                                    Text(
                                        when {
                                            !districtSelected -> "Mahalle: Önce ilçe seçin"
                                            neighborhoodLoading && neighborhoods.size <= 1 -> "Mahalleler yükleniyor…"
                                            else -> "Mahalle: $selectedNeighborhood"
                                        },
                                    )
                                }
                                DropdownMenu(
                                    expanded = neighborhoodMenu && neighborhoods.size > 1,
                                    onDismissRequest = { neighborhoodMenu = false },
                                ) {
                                    neighborhoods.forEach { neighborhood ->
                                        DropdownMenuItem(
                                            text = { Text(neighborhood) },
                                            onClick = {
                                                invalidateSearch()
                                                selectedNeighborhood = neighborhood
                                                results = emptyList()
                                                selectedBusiness = null
                                                scanDelta = null
                                                newBusinessKeys = emptySet()
                                                neighborhoodMenu = false
                                            },
                                        )
                                    }
                                }
                            }
                        }
                        item {
                            Card(modifier = Modifier.fillMaxWidth().testTag("inventory_filters_card")) {
                                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Column(Modifier.weight(1f)) {
                                            Text("Filtreler", style = MaterialTheme.typography.titleMedium)
                                            Text(
                                                "81 il • 973 ilçe • çoklu işletme kaynağı",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                        Row {
                                            TextButton(onClick = { showFilterDetails = !showFilterDetails }) {
                                                Text(if (showFilterDetails) "Detayı gizle" else "Kapsam")
                                            }
                                            TextButton(
                                                onClick = {
                                                    invalidateSearch()
                                                    resetFilters()
                                                    results = emptyList()
                                                    selectedBusiness = null
                                                    scanDelta = null
                                                    newBusinessKeys = emptySet()
                                                },
                                            ) { Text("Temizle") }
                                        }
                                    }
                                    if (showFilterDetails) {
                                        Text(
                                            "İl / ilçe / mahalle doğrudan kaynak taramasına uygulanır. Kategori Tümü iken sektör sınırlaması yapılmaz; " +
                                                "Overture, OpenStreetMap ve içe aktarılan resmî sicil verileri birlikte değerlendirilir. " +
                                                "Telefon, web, menü ve çalışma saati yalnız kaynakta mevcutsa gösterilir.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    InventoryFilterMenu(
                                        "Kategori",
                                        categoryFilter,
                                        categoryOptions,
                                        {
                                            if (categoryFilter != it) {
                                                invalidateSearch()
                                                categoryFilter = it
                                                query = ""
                                                results = emptyList()
                                                selectedBusiness = null
                                                scanDelta = null
                                                newBusinessKeys = emptySet()
                                            }
                                        },
                                    )
                                    InventoryFilterMenu("Telefon", phoneFilter, presenceOptions, { phoneFilter = it })
                                    InventoryFilterMenu("Web sitesi", websiteFilter, presenceOptions, { websiteFilter = it })
                                    InventoryFilterMenu("Adres", addressFilter, presenceOptions, { addressFilter = it })
                                    InventoryFilterMenu("Koordinat", coordinateFilter, presenceOptions, { coordinateFilter = it })
                                    InventoryFilterMenu("Menü", menuFilter, presenceOptions, { menuFilter = it })
                                    InventoryFilterMenu("Çalışma saati", openingHoursFilter, presenceOptions, { openingHoursFilter = it })
                                    Text("${visibleResults.size} sonuç", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                        item {
                            Button(
                                modifier = Modifier.fillMaxWidth().testTag("real_search_button"),
                                onClick = {
                                    error = null; selectedBusiness = null; crmMessage = null; loading = true
                                    val requestId = ++searchRequestId
                                    val requestCity = selectedCity.name
                                    val requestDistrict = selectedDistrict.takeUnless { it == "Tümü" }
                                    val requestNeighborhood = selectedNeighborhood.takeUnless {
                                        it == "Tümü" || requestDistrict == null
                                    }
                                    val requestQuery = query.trim().ifBlank {
                                        BusinessCategoryLabels.searchQueryForLabel(categoryFilter).orEmpty()
                                    }
                                    scope.launch {
                                        val searchTimeoutMs = if (requestDistrict == null) {
                                            CITY_WIDE_SEARCH_TIMEOUT_MS
                                        } else {
                                            SEARCH_TIMEOUT_MS
                                        }
                                        runCatching {
                                            withTimeout(searchTimeoutMs) {
                                                repository.searchScoped(
                                                    query = requestQuery,
                                                    city = requestCity,
                                                    district = requestDistrict,
                                                    neighborhood = requestNeighborhood,
                                                    districtScopeOverride = if (
                                                        requestCity == "İstanbul" &&
                                                        requestDistrict == null
                                                    ) {
                                                        selectedCity.districts
                                                    } else {
                                                        null
                                                    },
                                                )
                                            }
                                        }
                                            .onSuccess { records ->
                                                if (requestId == searchRequestId) {
                                                    val delta = scanHistoryRepository.compareAndRecord(
                                                        city = requestCity,
                                                        district = requestDistrict,
                                                        neighborhood = requestNeighborhood,
                                                        query = requestQuery,
                                                        records = records,
                                                    )
                                                    results = records
                                                    scanDelta = delta
                                                    newBusinessKeys = delta.newBusinessKeys
                                                    if (records.isEmpty()) {
                                                        error = "Seçilen kapsamda kayıt bulunamadı."
                                                    } else {
                                                        crmMessage = when {
                                                            delta.isFirstScan ->
                                                                "${records.size} işletme bulundu. Bu tarama sonraki karşılaştırmalar için baz olarak kaydedildi."
                                                            delta.newCount > 0 ->
                                                                "${records.size} işletme bulundu. Son taramadan beri ${delta.newCount} yeni işletme bulundu."
                                                            else ->
                                                                "${records.size} işletme bulundu. Son taramadan beri yeni işletme yok."
                                                        }
                                                    }
                                                }
                                            }
                                            .onFailure { throwable ->
                                                Log.w(
                                                    "LanuRadar",
                                                    "İşletme taraması tamamlanamadı: $requestCity/$requestDistrict/$requestNeighborhood",
                                                    throwable,
                                                )
                                                if (requestId == searchRequestId) {
                                                    error = if (throwable is TimeoutCancellationException) {
                                                        "Tarama zaman aşımına uğradı. İlçe/mahalle seçerek kapsamı daraltıp tekrar deneyin."
                                                    } else if (results.isNotEmpty()) {
                                                        "Yeni tarama başarısız; önceki sonuçlar korunuyor."
                                                    } else {
                                                        "Veri kaynağına erişilemedi. Bağlantınızı kontrol edip tekrar deneyin."
                                                    }
                                                }
                                            }
                                        if (requestId == searchRequestId) loading = false
                                    }
                                },
                                enabled = !loading,
                            ) {
                                Text(
                                    if (loading) "İşletmeler aranıyor…"
                                    else if (categoryFilter == "Tümü") "Tüm işletmeleri getir"
                                    else "$categoryFilter işletmelerini getir",
                                )
                            }
                        }
                        if (results.isNotEmpty()) {
                            item {
                                Button(
                                    modifier = Modifier.fillMaxWidth().testTag("crm_save_all_results"),
                                    enabled = !bulkSaving,
                                    onClick = {
                                        bulkSaving = true
                                        scope.launch {
                                            runCatching {
                                                localCrmRepository.addBusinessesAsCustomers(
                                                    visibleResults,
                                                    ownerUserId = activeOwnerUserId,
                                                )
                                            }
                                                .onSuccess { saved ->
                                                    crmMessage = if (saved.alreadyExisting > 0) {
                                                        "${saved.inserted} yeni nokta CRM'e kaydedildi; ${saved.alreadyExisting} nokta zaten kayıtlıydı."
                                                    } else {
                                                        "${saved.inserted} noktanın tamamı CRM'e kaydedildi."
                                                    }
                                                }
                                                .onFailure { throwable ->
                                                    Log.e("LanuCrm", "Toplu CRM kaydı başarısız oldu.", throwable)
                                                    crmMessage = "Toplu CRM kaydı tamamlanamadı. Lütfen tekrar deneyin."
                                                }
                                            bulkSaving = false
                                        }
                                    },
                                ) {
                                    Text(if (bulkSaving) "CRM'e kaydediliyor…" else "Görünen Sonuçları CRM'e Kaydet (${visibleResults.size})")
                                }
                            }
                        }
                        error?.let { message -> item { Card { Text(message, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error) } } }
                        crmMessage?.let { message -> item { Card { Text(message, Modifier.padding(16.dp)) } } }
                        scanDelta?.let { delta ->
                            item {
                                Card(Modifier.fillMaxWidth().testTag("new_business_scan_summary")) {
                                    Column(
                                        Modifier.padding(16.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp),
                                    ) {
                                        Text("Tarama karşılaştırması", style = MaterialTheme.typography.titleMedium)
                                        Text(
                                            if (delta.isFirstScan) {
                                                "İlk tarama baz olarak kaydedildi. Sonraki taramalarda yeni açılan/kaynağa yeni eklenen işletmeler ayrıca gösterilecek."
                                            } else {
                                                "Önceki tarama: ${delta.previousCount} • Şimdi: ${delta.currentCount} • Yeni: ${delta.newCount}"
                                            },
                                        )
                                    }
                                }
                            }
                        }
                        selectedBusiness?.let { business -> item { BusinessDetailCard(business, { selectedBusiness = null }) } }
                        if (visibleResults.isNotEmpty()) {
                            item {
                                Card(Modifier.fillMaxWidth()) {
                                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text("Sonuç özeti", style = MaterialTheme.typography.titleMedium)
                                        Text("${visibleResults.size} işletme • Ortalama kalite ${qualitySummary.first}/100")
                                        Text("Kaynaklar: ${qualitySummary.third.joinToString(", ")}", style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }
                        items(visibleResults, key = { it.id }) { business ->
                            BusinessResultCard(
                                business = business,
                                isNew = RadarScanHistoryRepository.businessKey(business) in newBusinessKeys,
                                onClick = { selectedBusiness = business },
                            ) {
                                scope.launch {
                                    runCatching {
                                        localCrmRepository.addBusinessAsCustomer(
                                            business,
                                            ownerUserId = activeOwnerUserId,
                                        )
                                    }
                                        .onSuccess { crmMessage = "${it.businessName} CRM'e kaydedildi." }
                                        .onFailure {
                                            Log.e("LanuCrm", "CRM kaydı başarısız oldu.", it)
                                            crmMessage = "CRM kaydı yapılamadı. Lütfen tekrar deneyin."
                                        }
                                }
                            }
                        }
                    }
                    AppSection.MAP -> CrmMapScreen(
                        customers = filteredCrmCustomers,
                        radarBusinesses = visibleResults,
                        selectedCity = selectedCity.name,
                        selectedDistrict = selectedDistrict,
                        onOpenCustomer = { selectedCustomerId = it },
                    )
                    AppSection.CRM -> CrmWorkspaceScreen(
                        customers = filteredCrmCustomers,
                        openActions = regionNextActions,
                        metrics = dashboardMetrics,
                        selectedCity = selectedCity.name,
                        selectedDistrict = selectedDistrict,
                        pendingSyncCount = pendingSyncCount,
                        onOpenCustomer = { selectedCustomerId = it },
                        onCompleteAction = { actionId ->
                            scope.launch {
                                runCatching {
                                    localCrmRepository.completeNextAction(
                                        actionId,
                                        completedByUserId = activeOwnerUserId,
                                    )
                                }.onSuccess {
                                    CrmNextActionReminderScheduler.cancel(context, actionId)
                                    crmMessage = "Takip tamamlandı."
                                }.onFailure {
                                    crmMessage = "Takip tamamlanamadı: ${it.message.orEmpty()}"
                                }
                            }
                        },
                        onMergeCustomers = { targetId, sourceId ->
                            scope.launch {
                                runCatching { localCrmRepository.mergeCustomers(targetId, sourceId) }
                                    .onSuccess { merged ->
                                        crmMessage = "Mükerrer kayıt birleştirildi: " +
                                            (merged.movedActivities + merged.movedNextActions + merged.movedOpportunities +
                                                merged.movedContacts + merged.movedQuotes + merged.movedOrders) +
                                            " alt kayıt ana müşteriye taşındı."
                                    }
                                    .onFailure { error ->
                                        crmMessage = "Mükerrer kayıt birleştirilemedi: " + error.message.orEmpty()
                                    }
                            }
                        },
                    )
                    AppSection.ROUTINE -> RoutineScreen(filteredCrmCustomers, selectedCity.name, selectedDistrict)
                    AppSection.MORE -> LazyColumn(
                        modifier = Modifier
                            .testTag("more_screen")
                            .padding(horizontal = 16.dp),
                        contentPadding = PaddingValues(vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        item {
                            MoreMenuCard(
                                onProducts = { navigateTo(AppSection.PRODUCT_CATALOG) },
                                onManualPoint = { navigateTo(AppSection.MANUAL_POINT) },
                                onAi = { navigateTo(AppSection.AI_ASSISTANT) },
                            )
                        }
                        item {
                            OfficialRegistryImportCard(
                                defaultCity = selectedCity.name,
                                contextCustomers = crmCustomers,
                                contextBusinesses = results,
                            ) { summary, records ->
                                scope.launch {
                                    runCatching {
                                        localCrmRepository.enrichCustomersFromOfficialRegistryForOwner(
                                            records = records,
                                            ownerUserId = activeOwnerUserId,
                                        )
                                    }.onSuccess { enriched ->
                                        crmMessage = buildString {
                                            append(summary.source.name)
                                            append(": ")
                                            append(summary.importedCount)
                                            append(" kayıt içe alındı • sicil kimliği doğrulanan ")
                                            append(summary.verifiedIdentityCount)
                                            append(" • CRM eşleşmesi ")
                                            append(enriched.matched)
                                            append(" • güncellenen ")
                                            append(enriched.updated)
                                            if (enriched.inactiveMatches > 0) {
                                                append(" • aktif olmayan eşleşme ")
                                                append(enriched.inactiveMatches)
                                            }
                                        }
                                    }.onFailure { error ->
                                        Log.e("LanuRegistry", "Resmî sicil CRM zenginleştirmesi başarısız.", error)
                                        crmMessage = "Resmî kayıt içe aktarıldı; CRM zenginleştirmesi tamamlanamadı: " +
                                            error.message.orEmpty()
                                    }
                                }
                            }
                        }
                        crmMessage?.let { message ->
                            item {
                                Card(Modifier.fillMaxWidth()) {
                                    Text(message, Modifier.padding(16.dp))
                                }
                            }
                        }
                        auth?.let { cloudAuth ->
                            item { SupabaseSessionCard(cloudAuth) }
                        }
                    }
                    AppSection.PRODUCT_CATALOG -> ProductCatalogScreen(productCatalogRepository)
                    AppSection.MANUAL_POINT -> ManualPointScreen(
                        repository = localCrmRepository,
                        defaultCity = selectedCity.name,
                        cityLabel = selectedCity.label,
                        districtOptions = availableDistricts,
                        ownerUserId = activeOwnerUserId,
                    ) { navigateTo(AppSection.ROUTINE) }
                    AppSection.AI_ASSISTANT -> SalesAiScreen(salesAiContext)
                    }
                } else {
                    selectedCrmCustomer?.let { customer ->
                    CrmCustomerDetailScreen(
                        customer = customer,
                        activities = selectedCustomerActivities,
                        nextActions = selectedCustomerNextActions,
                        transitions = selectedCustomerTransitions,
                        opportunities = selectedCustomerOpportunities,
                        onBack = { selectedCustomerId = null },
                        onStageChange = { target, note -> scope.launch { runCatching { localCrmRepository.transitionStage(customer.id, target, activeOwnerUserId, note) }.onSuccess { crmMessage = "Aşama güncellendi." }.onFailure { crmMessage = "Aşama değiştirilemedi: ${it.message.orEmpty()}" } } },
                        onRecordActivity = { type, note -> scope.launch { runCatching { localCrmRepository.recordActivity(customer.id, type, note = note, createdByUserId = activeOwnerUserId) }.onSuccess { crmMessage = "Aktivite kaydedildi." }.onFailure { crmMessage = "Aktivite kaydedilemedi: ${it.message.orEmpty()}" } } },
                        onCreateNextAction = { type, dueAt, note ->
                            ensureNotificationPermission()
                            scope.launch {
                                runCatching {
                                    localCrmRepository.createNextAction(
                                        customer.id,
                                        type,
                                        dueAt,
                                        note,
                                        createdByUserId = activeOwnerUserId,
                                    )
                                }.onSuccess { action ->
                                    CrmNextActionReminderScheduler.schedule(context, action, customer.businessName)
                                    crmMessage = "Takip planlandı ve hatırlatma oluşturuldu."
                                }.onFailure { error ->
                                    crmMessage = "Takip planlanamadı: " + error.message.orEmpty()
                                }
                            }
                        },
                        onCompleteNextAction = { actionId ->
                            scope.launch {
                                runCatching {
                                    localCrmRepository.completeNextAction(
                                        actionId,
                                        completedByUserId = activeOwnerUserId,
                                    )
                                }.onSuccess {
                                    CrmNextActionReminderScheduler.cancel(context, actionId)
                                    crmMessage = "Takip tamamlandı."
                                }.onFailure { error ->
                                    crmMessage = "Takip tamamlanamadı: " + error.message.orEmpty()
                                }
                            }
                        },
                        onCreateOpportunity = { title, notes, estimatedValueMinor, currency -> scope.launch { runCatching { localCrmRepository.createOpportunity(
                                        customer.id,
                                        title,
                                        notes,
                                        estimatedValueMinor,
                                        currency,
                                        if (estimatedValueMinor == null) com.lanu.globaldonuksatisradari.crm.CrmValueOrigin.UNKNOWN else com.lanu.globaldonuksatisradari.crm.CrmValueOrigin.USER_ENTERED,
                                        createdByUserId = activeOwnerUserId,
                                    ) }.onSuccess { crmMessage = "Satış fırsatı kaydedildi." }.onFailure { crmMessage = "Fırsat kaydedilemedi: ${it.message.orEmpty()}" } } },
                        onTransitionOpportunity = { opportunityId, status -> scope.launch { runCatching { localCrmRepository.transitionOpportunity(opportunityId, status) }.onSuccess { crmMessage = "Fırsat durumu güncellendi." }.onFailure { crmMessage = "Fırsat durumu güncellenemedi: ${it.message.orEmpty()}" } } },
                        onSaveNotes = { notes -> scope.launch { runCatching { localCrmRepository.updateCustomerNotes(customer.id, notes) }.onSuccess { crmMessage = "Müşteri notu kaydedildi." }.onFailure { crmMessage = "Müşteri notu kaydedilemedi: " + it.message.orEmpty() } } },
                        onSaveTags = { tags ->
                            scope.launch {
                                runCatching { localCrmRepository.updateCustomerTags(customer.id, tags) }
                                    .onSuccess { crmMessage = "Etiketler kaydedildi." }
                                    .onFailure { error -> crmMessage = "Etiketler kaydedilemedi: " + error.message.orEmpty() }
                            }
                        },
                        onWorkspaceMessage = { crmMessage = it },
                        commercialContent = {
                            CrmCommercialWorkspace(
                                customerId = customer.id,
                                repository = commercialCrmRepository,
                                productRepository = productCatalogRepository,
                                onMessage = { crmMessage = it },
                            )
                        },
                        message = crmMessage,
                    )
                    }
                }
            }
        }
    }
}

private const val SEARCH_TIMEOUT_MS = 120_000L
private const val CITY_WIDE_SEARCH_TIMEOUT_MS = 600_000L

@Composable
private fun BusinessResultCard(
    business: VerifiedBusiness,
    isNew: Boolean,
    onClick: () -> Unit,
    onSaveToCrm: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text(business.name, style = MaterialTheme.typography.titleMedium)
                    Text("${business.city} • ${business.district}${business.neighborhood?.let { " • $it" } ?: ""}", style = MaterialTheme.typography.bodySmall)
                }
                Column(horizontalAlignment = androidx.compose.ui.Alignment.End) {
                    if (isNew) {
                        Text(
                            "YENİ",
                            modifier = Modifier.testTag("new_business_badge"),
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                    val registryEvidence = business.officialRegistryEvidence
                    Text(
                        when {
                            registryEvidence?.explicitlyActive == true -> "DURUM • AKTİF"
                            registryEvidence?.explicitlyInactive == true -> "DURUM • PASİF"
                            else -> "DURUM • DOĞRULANMADI"
                        },
                        modifier = Modifier.testTag("official_registry_badge"),
                        color = if (registryEvidence?.explicitlyInactive == true) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Text(
                        "${BusinessQualityEvaluator.evaluate(business).score}/100",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
            business.category?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            business.address?.let { Text("Adres: $it", style = MaterialTheme.typography.bodySmall) }
            business.phone?.let { Text("Telefon: $it", style = MaterialTheme.typography.bodySmall) }
            business.website?.let { Text("Web: $it", style = MaterialTheme.typography.bodySmall) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onClick) { Text("Detay") }
                Button(onClick = onSaveToCrm) { Text("CRM'e kaydet") }
            }
        }
    }
}

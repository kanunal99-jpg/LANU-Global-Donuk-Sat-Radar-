package com.lanu.globaldonuksatisradari

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.lanu.globaldonuksatisradari.crm.CrmActivityType
import com.lanu.globaldonuksatisradari.crm.CrmDashboardMetrics
import com.lanu.globaldonuksatisradari.crm.CrmNextActionType
import com.lanu.globaldonuksatisradari.crm.CrmOpportunityStatus
import com.lanu.globaldonuksatisradari.crm.CrmStage
import com.lanu.globaldonuksatisradari.crm.CrmSyncScheduler
import com.lanu.globaldonuksatisradari.crm.SupabaseAuthClient
import com.lanu.globaldonuksatisradari.crm.LanuCrmDatabase
import com.lanu.globaldonuksatisradari.crm.LocalCrmRepository
import com.lanu.globaldonuksatisradari.data.BusinessQualityEvaluator
import com.lanu.globaldonuksatisradari.data.DistrictCatalogRepository
import com.lanu.globaldonuksatisradari.data.NeighborhoodCatalogRepository
import com.lanu.globaldonuksatisradari.data.CoverageBusinessRepository
import com.lanu.globaldonuksatisradari.data.VerifiedBusiness
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout


data class City(val name: String, val districts: List<String>)

enum class AppSection { RADAR, PRODUCT_CATALOG, MANUAL_POINT, ROUTINE, AI_ASSISTANT }

private val cities = TurkeyCityCatalog.ALL.map { entry -> City(entry.name, entry.fallbackDistricts) }
private fun matchesInventoryPresence(value: String?, filter: String): Boolean = when (filter) {
    "Tümü" -> true
    "Var" -> !value.isNullOrBlank()
    "Yok" -> value.isNullOrBlank()
    else -> true
}

class MainActivity : ComponentActivity() {
    private fun isInstrumentationTest(): Boolean = runCatching { Class.forName("androidx.test.platform.app.InstrumentationRegistry") }.isSuccess
    private lateinit var auth: SupabaseAuthClient
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        auth = SupabaseAuthClient(this)
        val cloudAuth = if (isInstrumentationTest()) null else auth
        setContent { SalesRadarApp(cloudAuth) }
        lifecycleScope.launch(Dispatchers.IO) { runCatching { CrmSyncScheduler.schedule(applicationContext) } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SalesRadarApp(auth: SupabaseAuthClient? = null) {
    var selectedCity by remember { mutableStateOf(cities.first()) }
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
    var selectedDistrict by remember { mutableStateOf("Tümü") }
    var selectedNeighborhood by remember { mutableStateOf("Tümü") }
    var categoryFilter by remember { mutableStateOf("Tümü") }
    var phoneFilter by remember { mutableStateOf("Tümü") }
    var websiteFilter by remember { mutableStateOf("Tümü") }
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
    val context = LocalContext.current
    val repository = remember(context) { CoverageBusinessRepository(context) }
    val districtRepository = remember(context) { DistrictCatalogRepository(context) }
    val neighborhoodRepository = remember(context) { NeighborhoodCatalogRepository(context) }
    val scanHistoryRepository = remember(context) { RadarScanHistoryRepository(context) }
    LaunchedEffect(selectedCity.name) {
        districtLoading = true
        availableDistricts = runCatching {
            districtRepository.getDistricts(selectedCity.name, selectedCity.districts)
        }.getOrElse { throwable ->
            if (throwable is CancellationException) throw throwable
            Log.w("LanuRadar", "İlçe kataloğu yenilenemedi; yerel liste kullanılıyor.", throwable)
            selectedCity.districts
        }
        districtLoading = false
    }
    LaunchedEffect(selectedCity.name, selectedDistrict) {
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
    val localCrmRepository = remember(context) { LocalCrmRepository(LanuCrmDatabase.getInstance(context)) }
    val productCatalogRepository = remember(context) { ProductCatalogRepository(context) }
    val crmCustomers by localCrmRepository.observeCustomers(null).collectAsState(initial = emptyList())
    val pendingSyncCount by localCrmRepository.observePendingSyncCount().collectAsState(initial = 0)
    val filteredCrmCustomers = crmCustomers
    val selectedCrmCustomer = selectedCustomerId?.let { id -> crmCustomers.firstOrNull { it.id == id } }
    val selectedCustomerKey = selectedCustomerId.orEmpty()
    val selectedCustomerActivities by remember(selectedCustomerKey) { localCrmRepository.observeActivities(selectedCustomerKey) }.collectAsState(initial = emptyList())
    val selectedCustomerNextActions by remember(selectedCustomerKey) { localCrmRepository.observeNextActions(selectedCustomerKey) }.collectAsState(initial = emptyList())
    val selectedCustomerTransitions by remember(selectedCustomerKey) { localCrmRepository.observeStageTransitions(selectedCustomerKey) }.collectAsState(initial = emptyList())
    val selectedCustomerOpportunities by remember(selectedCustomerKey) { localCrmRepository.observeOpportunities(selectedCustomerKey) }.collectAsState(initial = emptyList())

    val regionKey = "${selectedCity.name}|$selectedDistrict"
    val regionDistrict = selectedDistrict.takeUnless { it == "Tümü" }
    val regionActivities by remember(regionKey) { localCrmRepository.observeActivitiesForRegion(selectedCity.name, regionDistrict) }.collectAsState(initial = emptyList())
    val regionNextActions by remember(regionKey) { localCrmRepository.observeOpenNextActionsForRegion(selectedCity.name, regionDistrict) }.collectAsState(initial = emptyList())
    val regionOpportunities by remember(regionKey) { localCrmRepository.observeOpportunitiesForRegion(selectedCity.name, regionDistrict) }.collectAsState(initial = emptyList())
    val presenceOptions = listOf("Tümü", "Var", "Yok")
    val categoryOptions = remember(results) { listOf("Tümü") + results.mapNotNull { it.category?.trim()?.takeIf(String::isNotBlank) }.distinct().sorted() }
    val visibleResults = remember(results, selectedDistrict, selectedNeighborhood, categoryFilter, phoneFilter, websiteFilter) {
        results.filter { business ->
            (selectedDistrict == "Tümü" || business.district.equals(selectedDistrict, true)) &&
                (selectedNeighborhood == "Tümü" || business.neighborhood?.equals(selectedNeighborhood, true) == true) &&
                (categoryFilter == "Tümü" || business.category.equals(categoryFilter, true)) &&
                matchesInventoryPresence(business.phone, phoneFilter) &&
                matchesInventoryPresence(business.website, websiteFilter)
        }
    }
    val qualitySummary = remember(visibleResults) {
        val scores = visibleResults.map { BusinessQualityEvaluator.evaluate(it).score }
        Triple(if (scores.isEmpty()) 0 else scores.sum() / scores.size, scores.count { it < 65 }, visibleResults.map { it.source.name }.distinct().sorted())
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
            crmCount = crmCustomers.size,
            prospectCount = crmCustomers.count { it.stage == CrmStage.PROSPECT },
            activeCustomerCount = crmCustomers.count { it.stage == CrmStage.ACTIVE_CUSTOMER },
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
        query = ""
    }

    LanuGlobalTheme {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { LanuBrandLockup(compact = true) },
                    navigationIcon = { TextButton(onClick = { if (selectedCrmCustomer != null) selectedCustomerId = null else goBack() }, enabled = selectedCrmCustomer != null || backStack.isNotEmpty()) { Text("← Geri") } },
                    actions = { TextButton(onClick = { goForward() }, enabled = forwardStack.isNotEmpty()) { Text("İleri →") } },
                )
            },
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(section == AppSection.RADAR && selectedCrmCustomer == null, { selectedCustomerId = null; navigateTo(AppSection.RADAR) }, { Text("⌖") }, label = { Text("Radar") })
                    NavigationBarItem(section == AppSection.PRODUCT_CATALOG && selectedCrmCustomer == null, { selectedCustomerId = null; navigateTo(AppSection.PRODUCT_CATALOG) }, { Text("₺") }, label = { Text("Ürünler") })
                    NavigationBarItem(section == AppSection.MANUAL_POINT && selectedCrmCustomer == null, { selectedCustomerId = null; navigateTo(AppSection.MANUAL_POINT) }, { Text("+") }, label = { Text("Nokta") })
                    NavigationBarItem(section == AppSection.ROUTINE && selectedCrmCustomer == null, { selectedCustomerId = null; navigateTo(AppSection.ROUTINE) }, { Text("↗") }, label = { Text("Rutin") })
                    NavigationBarItem(
                        selected = section == AppSection.AI_ASSISTANT && selectedCrmCustomer == null,
                        onClick = { selectedCustomerId = null; navigateTo(AppSection.AI_ASSISTANT) },
                        icon = { Text("AI") },
                        label = { Text("Asistan") },
                        modifier = Modifier.testTag("nav_ai"),
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
                            OutlinedTextField(value = query, onValueChange = { query = it }, modifier = Modifier.fillMaxWidth(), label = { Text("İşletme veya HORECA ara") }, singleLine = true)
                        }
                        item {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                Box(Modifier.weight(1f)) {
                                    OutlinedButton(onClick = { cityMenu = true }, modifier = Modifier.fillMaxWidth()) { Text(selectedCity.name) }
                                    DropdownMenu(cityMenu, { cityMenu = false }) { cities.forEach { city -> DropdownMenuItem({ Text(city.name) }, onClick = { invalidateSearch(); selectedCity = city; selectedDistrict = "Tümü"; results = emptyList(); resetFilters(); cityMenu = false }) } }
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
                            val neighborhoods = remember(availableNeighborhoods, results) {
                                listOf("Tümü") +
                                    (availableNeighborhoods + results.mapNotNull { it.neighborhood })
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
                                                selectedNeighborhood = neighborhood
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
                                            Text("Hızlı filtreler", style = MaterialTheme.typography.titleMedium)
                                            Text("Sadece karar vermede kullanılan alanlar.", style = MaterialTheme.typography.bodySmall)
                                        }
                                        TextButton(onClick = { resetFilters() }) { Text("Temizle") }
                                    }
                                    InventoryFilterMenu("Kategori", categoryFilter, categoryOptions, { categoryFilter = it })
                                    InventoryFilterMenu("Telefon", phoneFilter, presenceOptions, { phoneFilter = it })
                                    InventoryFilterMenu("Web sitesi", websiteFilter, presenceOptions, { websiteFilter = it })
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
                                    val requestQuery = query.trim()
                                    scope.launch {
                                        val searchTimeoutMs = if (requestDistrict == null) {
                                            CITY_WIDE_SEARCH_TIMEOUT_MS
                                        } else {
                                            SEARCH_TIMEOUT_MS
                                        }
                                        runCatching { withTimeout(searchTimeoutMs) { repository.search(requestQuery, requestCity, requestDistrict) } }
                                            .onSuccess { records ->
                                                if (requestId == searchRequestId) {
                                                    val delta = scanHistoryRepository.compareAndRecord(
                                                        city = requestCity,
                                                        district = requestDistrict,
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
                                                Log.w("LanuRadar", "İşletme taraması tamamlanamadı: $requestCity/$requestDistrict", throwable)
                                                if (requestId == searchRequestId) {
                                                    error = if (throwable is TimeoutCancellationException) {
                                                        "Tarama zaman aşımına uğradı. Daha dar bir ilçe veya arama terimiyle tekrar deneyin."
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
                            ) { Text(if (loading) "İşletmeler aranıyor…" else "İşletmeleri getir") }
                        }
                        if (results.isNotEmpty()) {
                            item {
                                Button(
                                    modifier = Modifier.fillMaxWidth().testTag("crm_save_all_results"),
                                    enabled = !bulkSaving,
                                    onClick = {
                                        bulkSaving = true
                                        scope.launch {
                                            runCatching { localCrmRepository.addBusinessesAsCustomers(results) }
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
                                    Text(if (bulkSaving) "CRM'e kaydediliyor…" else "Tüm Sonuçları CRM'e Kaydet (${results.size})")
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
                        item { SalesDashboard(selectedCity.name, selectedDistrict, availableDistricts, dashboardMetrics) { invalidateSearch(); selectedDistrict = it; results = emptyList(); selectedBusiness = null; selectedCustomerId = null } }
                        item {
                            Card(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("CRM ve Excel", style = MaterialTheme.typography.titleMedium)
                                    Text("${crmCustomers.size} CRM noktası")
                                    Text(if (pendingSyncCount == 0) "Tüm yerel değişiklikler işlendi." else "$pendingSyncCount değişiklik bağlantı bekliyor.")
                                    CrmExportActions(crmCustomers)
                                }
                            }
                        }
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
                            item { BusinessMapPreview(visibleResults) }
                        }
                        items(visibleResults, key = { it.id }) { business ->
                            BusinessResultCard(
                                business = business,
                                isNew = RadarScanHistoryRepository.businessKey(business) in newBusinessKeys,
                                onClick = { selectedBusiness = business },
                            ) {
                                scope.launch {
                                    runCatching { localCrmRepository.addBusinessAsCustomer(business) }
                                        .onSuccess { crmMessage = "${it.businessName} CRM'e kaydedildi." }
                                        .onFailure {
                                            Log.e("LanuCrm", "CRM kaydı başarısız oldu.", it)
                                            crmMessage = "CRM kaydı yapılamadı. Lütfen tekrar deneyin."
                                        }
                                }
                            }
                        }
                        if (filteredCrmCustomers.isNotEmpty()) {
                            item { Text("CRM müşterileri", style = MaterialTheme.typography.titleMedium) }
                            items(filteredCrmCustomers.take(25), key = { it.id }) { customer ->
                                Card(Modifier.fillMaxWidth()) {
                                    Row(Modifier.padding(14.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Column(Modifier.weight(1f)) { Text(customer.businessName, style = MaterialTheme.typography.titleMedium); Text("${customer.city} • ${customer.district} • ${customer.stage.name}", style = MaterialTheme.typography.bodySmall) }
                                        OutlinedButton(
                                            onClick = { selectedCustomerId = customer.id },
                                            modifier = Modifier.testTag("crm_open_${customer.businessSourceId}"),
                                        ) { Text("Aç") }
                                    }
                                }
                            }
                        }
                        auth?.let { cloudAuth -> item { SupabaseSessionCard(cloudAuth) } }
                    }
                    AppSection.PRODUCT_CATALOG -> ProductCatalogScreen(productCatalogRepository)
                    AppSection.MANUAL_POINT -> ManualPointScreen(localCrmRepository, selectedCity.name) { navigateTo(AppSection.ROUTINE) }
                    AppSection.ROUTINE -> RoutineScreen(crmCustomers, selectedCity.name, selectedDistrict)
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
                        onStageChange = { target, note -> scope.launch { runCatching { localCrmRepository.transitionStage(customer.id, target, null, note) }.onSuccess { crmMessage = "Aşama güncellendi." }.onFailure { crmMessage = "Aşama değiştirilemedi: ${it.message.orEmpty()}" } } },
                        onRecordActivity = { type, note -> scope.launch { runCatching { localCrmRepository.recordActivity(customer.id, type, note) }.onSuccess { crmMessage = "Aktivite kaydedildi." }.onFailure { crmMessage = "Aktivite kaydedilemedi: ${it.message.orEmpty()}" } } },
                        onCreateNextAction = { type, dueAt, note -> scope.launch { runCatching { localCrmRepository.createNextAction(customer.id, type, dueAt, note) }.onSuccess { crmMessage = "Takip planlandı." }.onFailure { crmMessage = "Takip planlanamadı: ${it.message.orEmpty()}" } } },
                        onCompleteNextAction = { actionId -> scope.launch { runCatching { localCrmRepository.completeNextAction(actionId) }.onSuccess { crmMessage = "Takip tamamlandı." }.onFailure { crmMessage = "Takip tamamlanamadı: ${it.message.orEmpty()}" } } },
                        onCreateOpportunity = { title, notes, estimatedValueMinor, currency -> scope.launch { runCatching { localCrmRepository.createOpportunity(customer.id, title, notes, estimatedValueMinor, currency, if (estimatedValueMinor == null) com.lanu.globaldonuksatisradari.crm.CrmValueOrigin.UNKNOWN else com.lanu.globaldonuksatisradari.crm.CrmValueOrigin.USER_ENTERED) }.onSuccess { crmMessage = "Satış fırsatı kaydedildi." }.onFailure { crmMessage = "Fırsat kaydedilemedi: ${it.message.orEmpty()}" } } },
                        onTransitionOpportunity = { opportunityId, status -> scope.launch { runCatching { localCrmRepository.transitionOpportunity(opportunityId, status) }.onSuccess { crmMessage = "Fırsat durumu güncellendi." }.onFailure { crmMessage = "Fırsat durumu güncellenemedi: ${it.message.orEmpty()}" } } },
                        onSaveNotes = { notes -> scope.launch { runCatching { localCrmRepository.updateCustomerNotes(customer.id, notes) }.onSuccess { crmMessage = "Müşteri notu kaydedildi." }.onFailure { crmMessage = "Müşteri notu kaydedilemedi: ${it.message.orEmpty()}" } } },
                        message = crmMessage,
                    )
                    }
                }
            }
        }
    }
}

private const val SEARCH_TIMEOUT_MS = 120_000L
private const val CITY_WIDE_SEARCH_TIMEOUT_MS = 240_000L

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
                    Text(
                        "${BusinessQualityEvaluator.evaluate(business).score}/100",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
            business.category?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            business.phone?.let { Text("Telefon: $it", style = MaterialTheme.typography.bodySmall) }
            business.website?.let { Text("Web: $it", style = MaterialTheme.typography.bodySmall) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onClick) { Text("Detay") }
                Button(onClick = onSaveToCrm) { Text("CRM'e kaydet") }
            }
        }
    }
}

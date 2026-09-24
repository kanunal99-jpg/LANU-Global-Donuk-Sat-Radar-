package com.lanu.globaldonuksatisradari

import android.os.Bundle
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
import com.lanu.globaldonuksatisradari.data.CoverageBusinessRepository
import com.lanu.globaldonuksatisradari.data.VerifiedBusiness
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch


data class City(val name: String, val districts: List<String>)

enum class AppSection { RADAR, PRODUCT_CATALOG, MANUAL_POINT, ROUTINE }

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
    var error by remember { mutableStateOf<String?>(null) }
    var crmMessage by remember { mutableStateOf<String?>(null) }

    BackHandler(enabled = selectedCustomerId != null || backStack.isNotEmpty()) { if (selectedCustomerId != null) selectedCustomerId = null else goBack() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val repository = remember(context) { CoverageBusinessRepository(context) }
    val districtRepository = remember(context) { DistrictCatalogRepository(context) }
    LaunchedEffect(selectedCity.name) {
        districtLoading = true
        availableDistricts = districtRepository.getDistricts(selectedCity.name, selectedCity.districts)
        districtLoading = false
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
                }
            },
        ) { padding ->
            if (selectedCrmCustomer == null) {
                when (section) {
                    AppSection.RADAR -> LazyColumn(
                        modifier = Modifier.testTag("main_scroll").padding(padding).padding(horizontal = 16.dp),
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
                                    DropdownMenu(cityMenu, { cityMenu = false }) { cities.forEach { city -> DropdownMenuItem({ Text(city.name) }, onClick = { selectedCity = city; selectedDistrict = "Tümü"; results = emptyList(); resetFilters(); cityMenu = false }) } }
                                }
                                Box(Modifier.weight(1f)) {
                                    OutlinedButton(onClick = { districtMenu = true }, modifier = Modifier.fillMaxWidth().testTag("district_filter")) { Text(if (districtLoading) "Yükleniyor…" else selectedDistrict) }
                                    DropdownMenu(districtMenu, { districtMenu = false }) {
                                        DropdownMenuItem({ Text("Tümü") }, onClick = { selectedDistrict = "Tümü"; resetFilters(); districtMenu = false })
                                        availableDistricts.forEach { district -> DropdownMenuItem({ Text(district) }, onClick = { selectedDistrict = district; resetFilters(); districtMenu = false }) }
                                    }
                                }
                            }
                        }
                        item {
                            val neighborhoods = listOf("Tümü") + results.mapNotNull { it.neighborhood }.distinct().sorted()
                            Box {
                                OutlinedButton(onClick = { if (neighborhoods.size > 1) neighborhoodMenu = true }, modifier = Modifier.fillMaxWidth()) { Text("Mahalle: $selectedNeighborhood") }
                                DropdownMenu(neighborhoodMenu && neighborhoods.size > 1, { neighborhoodMenu = false }) { neighborhoods.forEach { n -> DropdownMenuItem({ Text(n) }, onClick = { selectedNeighborhood = n; neighborhoodMenu = false }) } }
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
                                    scope.launch {
                                        runCatching { repository.search(query.trim(), selectedCity.name, selectedDistrict.takeUnless { it == "Tümü" }) }
                                            .onSuccess { records -> results = records; if (records.isEmpty()) error = "Seçilen kapsamda kayıt bulunamadı." else crmMessage = "${records.size} gerçek işletme kaydı getirildi." }
                                            .onFailure { throwable -> error = if (results.isNotEmpty()) "Yeni tarama başarısız; önceki sonuçlar korunuyor. ${throwable.message.orEmpty()}" else "Veri kaynağına erişilemedi. ${throwable.message.orEmpty()}" }
                                        loading = false
                                    }
                                },
                                enabled = !loading,
                            ) { Text(if (loading) "İşletmeler aranıyor…" else "İşletmeleri getir") }
                        }
                        error?.let { message -> item { Card { Text(message, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error) } } }
                        crmMessage?.let { message -> item { Card { Text(message, Modifier.padding(16.dp)) } } }
                        selectedBusiness?.let { business -> item { BusinessDetailCard(business, { selectedBusiness = null }) } }
                        item { SalesDashboard(selectedCity.name, selectedDistrict, availableDistricts, dashboardMetrics) { selectedDistrict = it; selectedBusiness = null; selectedCustomerId = null } }
                        item {
                            Card(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text("Senkronizasyon", style = MaterialTheme.typography.titleMedium)
                                    Text(if (pendingSyncCount == 0) "Tüm yerel değişiklikler işlendi." else "$pendingSyncCount değişiklik bağlantı bekliyor.")
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
                            BusinessResultCard(business, { selectedBusiness = business }) {
                                scope.launch { runCatching { localCrmRepository.addBusinessAsCustomer(business) }.onSuccess { crmMessage = "${it.businessName} CRM'e kaydedildi." }.onFailure { crmMessage = "CRM kaydı yapılamadı: ${it.message.orEmpty()}" } }
                            }
                        }
                        if (filteredCrmCustomers.isNotEmpty()) {
                            item { Text("CRM müşterileri", style = MaterialTheme.typography.titleMedium) }
                            items(filteredCrmCustomers.take(25), key = { it.id }) { customer ->
                                Card(Modifier.fillMaxWidth()) {
                                    Row(Modifier.padding(14.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Column(Modifier.weight(1f)) { Text(customer.businessName, style = MaterialTheme.typography.titleMedium); Text("${customer.city} • ${customer.district} • ${customer.stage.name}", style = MaterialTheme.typography.bodySmall) }
                                        OutlinedButton(onClick = { selectedCustomerId = customer.id }) { Text("Aç") }
                                    }
                                }
                            }
                        }
                        auth?.let { cloudAuth -> item { SupabaseSessionCard(cloudAuth) } }
                    }
                    AppSection.PRODUCT_CATALOG -> ProductCatalogScreen(productCatalogRepository)
                    AppSection.MANUAL_POINT -> ManualPointScreen(localCrmRepository, selectedCity.name) { navigateTo(AppSection.ROUTINE) }
                    AppSection.ROUTINE -> RoutineScreen(crmCustomers, selectedCity.name, selectedDistrict)
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

@Composable
private fun BusinessResultCard(business: VerifiedBusiness, onClick: () -> Unit, onSaveToCrm: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text(business.name, style = MaterialTheme.typography.titleMedium)
                    Text("${business.city} • ${business.district}${business.neighborhood?.let { " • $it" } ?: ""}", style = MaterialTheme.typography.bodySmall)
                }
                Text("${BusinessQualityEvaluator.evaluate(business).score}/100", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
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

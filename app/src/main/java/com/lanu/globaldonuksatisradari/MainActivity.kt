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
import com.lanu.globaldonuksatisradari.data.MultiSourceBusinessRepository
import com.lanu.globaldonuksatisradari.data.VerifiedBusiness
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch


data class City(val name: String, val districts: List<String>)

enum class AppSection { RADAR, PRODUCT_CATALOG, MANUAL_POINT, ROUTINE }

private val cities = listOf(
    City("İstanbul", IstanbulDistricts.ALL),
    City("Ankara", listOf("Çankaya", "Keçiören", "Yenimahalle")),
    City("İzmir", listOf("Konak", "Karşıyaka", "Bornova")),
    City("Bursa", listOf("Nilüfer", "Osmangazi")),
    City("Antalya", listOf("Muratpaşa", "Konyaaltı")),
)

private fun matchesInventoryPresence(value: String?, filter: String): Boolean =
    when (filter) {
        "Tümü" -> true
        "Var" -> !value.isNullOrBlank()
        "Yok" -> value.isNullOrBlank()
        else -> true
}

class MainActivity : ComponentActivity() {

    private fun isInstrumentationTest(): Boolean = runCatching {
        Class.forName("androidx.test.platform.app.InstrumentationRegistry")
    }.isSuccess
    private lateinit var auth: SupabaseAuthClient
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        auth = SupabaseAuthClient(this)
        val cloudAuth = if (isInstrumentationTest()) null else auth
        setContent { SalesRadarApp(cloudAuth) }
        // Keep the first Compose frame independent from cold-start WorkManager initialization.
        lifecycleScope.launch(Dispatchers.IO) {
            runCatching { CrmSyncScheduler.schedule(applicationContext) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SalesRadarApp(auth: SupabaseAuthClient? = null) {
    var selectedCity by remember { mutableStateOf(cities.first()) }

    var section by remember { mutableStateOf(AppSection.RADAR) }
    val backStack = remember { mutableStateListOf<AppSection>() }
    val forwardStack = remember { mutableStateListOf<AppSection>() }
    fun navigateTo(target: AppSection) {
        if (target == section) return
        backStack.add(section)
        section = target
        forwardStack.clear()
    }
    fun goBack() {
        val previous = backStack.removeLastOrNull() ?: return
        forwardStack.add(section)
        section = previous
    }
    fun goForward() {
        val next = forwardStack.removeLastOrNull() ?: return
        backStack.add(section)
        section = next
    }

    var cityMenu by remember { mutableStateOf(false) }
    var districtMenu by remember { mutableStateOf(false) }
    var neighborhoodMenu by remember { mutableStateOf(false) }
    var selectedDistrict by remember { mutableStateOf("Tümü") }
    var selectedNeighborhood by remember { mutableStateOf("Tümü") }
    var categoryFilter by remember { mutableStateOf("Tümü") }
    var phoneFilter by remember { mutableStateOf("Tümü") }
    var menuFilter by remember { mutableStateOf("Tümü") }
    var websiteFilter by remember { mutableStateOf("Tümü") }
    var openingHoursFilter by remember { mutableStateOf("Tümü") }
    var addressFilter by remember { mutableStateOf("Tümü") }
    var coordinatesFilter by remember { mutableStateOf("Tümü") }
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<VerifiedBusiness>>(emptyList()) }
    var selectedBusiness by remember { mutableStateOf<VerifiedBusiness?>(null) }
    var selectedCustomerId by remember { mutableStateOf<String?>(null) }

    BackHandler(enabled = selectedCustomerId != null || backStack.isNotEmpty()) {
        if (selectedCustomerId != null) {
            selectedCustomerId = null
        } else {
            goBack()
        }
    }

    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var crmMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val repository = remember(context) {
        MultiSourceBusinessRepository(context)
    }
    val localCrmRepository = remember(context) {
        LocalCrmRepository(LanuCrmDatabase.getInstance(context))
    }
    val productCatalogRepository = remember(context) {
        ProductCatalogRepository(context)
    }
    val crmCustomers by localCrmRepository
        .observeCustomers(null)
        .collectAsState(initial = emptyList())
    val pendingSyncCount by localCrmRepository
        .observePendingSyncCount()
        .collectAsState(initial = 0)
    // CRM kayıtları arama/ilçe filtresinden bağımsız kalıcıdır.
    val filteredCrmCustomers = crmCustomers
    val selectedCrmCustomer = selectedCustomerId?.let { id -> crmCustomers.firstOrNull { it.id == id } }
    val selectedCustomerKey = selectedCustomerId.orEmpty()
    val selectedCustomerActivitiesFlow = remember(selectedCustomerKey) {
        localCrmRepository.observeActivities(selectedCustomerKey)
    }
    val selectedCustomerNextActionsFlow = remember(selectedCustomerKey) {
        localCrmRepository.observeNextActions(selectedCustomerKey)
    }
    val selectedCustomerTransitionsFlow = remember(selectedCustomerKey) {
        localCrmRepository.observeStageTransitions(selectedCustomerKey)
    }
    val selectedCustomerOpportunitiesFlow = remember(selectedCustomerKey) {
        localCrmRepository.observeOpportunities(selectedCustomerKey)
    }
    val selectedCustomerActivities by selectedCustomerActivitiesFlow.collectAsState(initial = emptyList())
    val selectedCustomerNextActions by selectedCustomerNextActionsFlow.collectAsState(initial = emptyList())
    val selectedCustomerTransitions by selectedCustomerTransitionsFlow.collectAsState(initial = emptyList())
    val selectedCustomerOpportunities by selectedCustomerOpportunitiesFlow.collectAsState(initial = emptyList())

    val regionKey = "${selectedCity.name}|${selectedDistrict}"
    val regionDistrict = selectedDistrict.takeUnless { it == "Tümü" }
    val regionActivitiesFlow = remember(regionKey) {
        localCrmRepository.observeActivitiesForRegion(
            city = selectedCity.name,
            district = regionDistrict,
        )
    }
    val regionNextActionsFlow = remember(regionKey) {
        localCrmRepository.observeOpenNextActionsForRegion(
            city = selectedCity.name,
            district = regionDistrict,
        )
    }
    val regionOpportunitiesFlow = remember(regionKey) {
        localCrmRepository.observeOpportunitiesForRegion(
            city = selectedCity.name,
            district = regionDistrict,
        )
    }
    val regionActivities by regionActivitiesFlow.collectAsState(initial = emptyList())
    val regionNextActions by regionNextActionsFlow.collectAsState(initial = emptyList())
    val regionOpportunities by regionOpportunitiesFlow.collectAsState(initial = emptyList())
    val presenceOptions = listOf("Tümü", "Var", "Yok")
    val categoryOptions = remember(results) {
        listOf("Tümü") + results.mapNotNull { it.category?.trim()?.takeIf(String::isNotBlank) }.distinct().sorted()
    }
    val visibleResults = remember(
        results,
        selectedDistrict,
        selectedNeighborhood,
        categoryFilter,
        phoneFilter,
        menuFilter,
        websiteFilter,
        openingHoursFilter,
        addressFilter,
        coordinatesFilter,
    ) {
        results.filter { business ->
            val hasCoordinates = business.latitude != null && business.longitude != null
            (selectedDistrict == "Tümü" || business.district.equals(selectedDistrict, ignoreCase = true)) &&
                (selectedNeighborhood == "Tümü" || business.neighborhood?.equals(selectedNeighborhood, ignoreCase = true) == true) &&
                (categoryFilter == "Tümü" || business.category.equals(categoryFilter, ignoreCase = true)) &&
                matchesInventoryPresence(business.phone, phoneFilter) &&
                matchesInventoryPresence(business.menuUrl ?: business.menuText, menuFilter) &&
                matchesInventoryPresence(business.website, websiteFilter) &&
                matchesInventoryPresence(business.openingHours, openingHoursFilter) &&
                matchesInventoryPresence(business.address, addressFilter) &&
                matchesInventoryPresence(if (hasCoordinates) "1" else null, coordinatesFilter)
        }
    }

    val dashboardMetrics = remember(
        filteredCrmCustomers,
        regionActivities,
        regionNextActions,
        regionOpportunities,
    ) {
        CrmDashboardMetrics.from(
            customers = filteredCrmCustomers,
            activities = regionActivities,
            openNextActions = regionNextActions,
            opportunities = regionOpportunities,
        )
    }

    LanuGlobalTheme {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { LanuBrandLockup(compact = true) },
                    navigationIcon = {
                        TextButton(
                            onClick = {
                                if (selectedCrmCustomer != null) selectedCustomerId = null else goBack()
                            },
                            enabled = selectedCrmCustomer != null || backStack.isNotEmpty(),
                        ) { Text("← Geri") }
                    },
                    actions = {
                        TextButton(
                            onClick = { goForward() },
                            enabled = forwardStack.isNotEmpty(),
                        ) { Text("İleri →") }
                    },
                )
            },
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(
                        selected = section == AppSection.RADAR && selectedCrmCustomer == null,
                        onClick = { selectedCustomerId = null; navigateTo(AppSection.RADAR) },
                        icon = { Text("R") },
                        label = { Text("Radar") },
                    )
                    NavigationBarItem(
                        selected = section == AppSection.PRODUCT_CATALOG && selectedCrmCustomer == null,
                        onClick = { selectedCustomerId = null; navigateTo(AppSection.PRODUCT_CATALOG) },
                        icon = { Text("₺") },
                        label = { Text("Ürünler") },
                    )
                    NavigationBarItem(
                        selected = section == AppSection.MANUAL_POINT && selectedCrmCustomer == null,
                        onClick = { selectedCustomerId = null; navigateTo(AppSection.MANUAL_POINT) },
                        icon = { Text("+") },
                        label = { Text("Nokta") },
                    )
                    NavigationBarItem(
                        selected = section == AppSection.ROUTINE && selectedCrmCustomer == null,
                        onClick = { selectedCustomerId = null; navigateTo(AppSection.ROUTINE) },
                        icon = { Text("↗") },
                        label = { Text("Rutin") },
                    )
                }
            },
        ) { padding ->
            if (selectedCrmCustomer == null) {
                when (section) {
                    AppSection.RADAR -> {
                LazyColumn(
                    modifier = Modifier
                        .testTag("main_scroll")
                        .padding(padding)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item {
                        LanuHeroHeader()
                        Text("LANU Global Donuk Gıda", style = MaterialTheme.typography.titleLarge)
                        Text("Satış & CRM Radarı", style = MaterialTheme.typography.headlineSmall)
                        Text("Şehir → ilçe → mahalle → gerçek işletme keşfi", style = MaterialTheme.typography.bodyMedium)
                        if (selectedCity.name == "İstanbul") {
                            Text(
                                "İstanbul’da 39 ilçe filtresi aktif.",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(onClick = { navigateTo(AppSection.MANUAL_POINT) }, modifier = Modifier.weight(1f)) {
                                Text("Manuel nokta")
                            }
                            OutlinedButton(onClick = { navigateTo(AppSection.ROUTINE) }, modifier = Modifier.weight(1f)) {
                                Text("Rutin oluştur")
                            }
                            OutlinedButton(onClick = { navigateTo(AppSection.PRODUCT_CATALOG) }, modifier = Modifier.weight(1f)) {
                                Text("Ürün kataloğu")
                            }
                        }
                    }
                    item {
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("İşletme / HORECA araması (isteğe bağlı)") },
                            supportingText = {
                                Text("Boş bırakırsanız seçilen şehir/ilçe için OSM işletme envanteri taranır; hedefli kategori aramalarında çiğköfte, cafe, restoran, catering, PlayStation ve daha fazlası desteklenir.")
                            },
                            singleLine = true,
                        )
                    }
                    item {
                        Box {
                            OutlinedButton(onClick = { cityMenu = true }, modifier = Modifier.fillMaxWidth()) { Text("Şehir: ${selectedCity.name}") }
                            DropdownMenu(expanded = cityMenu, onDismissRequest = { cityMenu = false }) {
                                cities.forEach { city ->
                                    DropdownMenuItem(text = { Text(city.name) }, onClick = {
                                        selectedCity = city
                                        selectedDistrict = "Tümü"
                                        selectedNeighborhood = "Tümü"
                                        cityMenu = false
                                        results = emptyList()
                                        selectedNeighborhood = "Tümü"
                                        categoryFilter = "Tümü"
                                        phoneFilter = "Tümü"
                                        menuFilter = "Tümü"
                                        websiteFilter = "Tümü"
                                        openingHoursFilter = "Tümü"
                                        addressFilter = "Tümü"
                                        coordinatesFilter = "Tümü"
                                        selectedBusiness = null
                                        selectedCustomerId = null
                                        crmMessage = null
                                    })
                                }
                            }
                        }
                    }
                    item {
                        Box {
                            OutlinedButton(
                                onClick = { districtMenu = true },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("district_filter"),
                            ) { Text("İlçe: $selectedDistrict") }
                            DropdownMenu(
                                expanded = districtMenu,
                                onDismissRequest = { districtMenu = false },
                            ) {
                                LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                                    items(listOf("Tümü") + selectedCity.districts) { district ->
                                        DropdownMenuItem(text = { Text(district) }, onClick = {
                                            selectedDistrict = district
                                            selectedNeighborhood = "Tümü"
                                            categoryFilter = "Tümü"
                                            phoneFilter = "Tümü"
                                            menuFilter = "Tümü"
                                            websiteFilter = "Tümü"
                                            openingHoursFilter = "Tümü"
                                            addressFilter = "Tümü"
                                            coordinatesFilter = "Tümü"
                                            districtMenu = false
                                            selectedBusiness = null
                                            selectedCustomerId = null
                                            crmMessage = null
                                        })
                                    }
                                }
                            }
                        }
                    }
                    item {
                        val neighborhoods = listOf("Tümü") + results.mapNotNull { it.neighborhood }.distinct().sorted()
                        Box {
                            OutlinedButton(
                                onClick = { if (neighborhoods.size > 1) neighborhoodMenu = true },
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text("Mahalle: $selectedNeighborhood") }
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
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text("İşletme envanteri filtreleri", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "Konum + kategori + telefon + menü + web + çalışma saati + adres + koordinat alanlarının tamamı filtrelenebilir.",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                InventoryFilterMenu(
                                    label = "Kategori",
                                    selected = categoryFilter,
                                    options = categoryOptions,
                                    onSelected = { categoryFilter = it },
                                )
                                InventoryFilterMenu(
                                    label = "Telefon",
                                    selected = phoneFilter,
                                    options = presenceOptions,
                                    onSelected = { phoneFilter = it },
                                )
                                InventoryFilterMenu(
                                    label = "Menü",
                                    selected = menuFilter,
                                    options = presenceOptions,
                                    onSelected = { menuFilter = it },
                                )
                                InventoryFilterMenu(
                                    label = "Web",
                                    selected = websiteFilter,
                                    options = presenceOptions,
                                    onSelected = { websiteFilter = it },
                                )
                                InventoryFilterMenu(
                                    label = "Çalışma saati",
                                    selected = openingHoursFilter,
                                    options = presenceOptions,
                                    onSelected = { openingHoursFilter = it },
                                )
                                InventoryFilterMenu(
                                    label = "Adres",
                                    selected = addressFilter,
                                    options = presenceOptions,
                                    onSelected = { addressFilter = it },
                                )
                                InventoryFilterMenu(
                                    label = "Koordinat",
                                    selected = coordinatesFilter,
                                    options = presenceOptions,
                                    onSelected = { coordinatesFilter = it },
                                )
                                Text(
                                    "Aktif sonuç: ${visibleResults.size}",
                                    style = MaterialTheme.typography.labelLarge,
                                )
                            }
                        }
                    }
                    item {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text("Gerçek veri filtresi", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    selectedCity.name + " • " + selectedDistrict + " • " + selectedNeighborhood,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                                )
                                Button(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("real_search_button"),
                                    onClick = {
                                        error = null
                                        selectedBusiness = null
                                        crmMessage = null
                                        loading = true
                                        scope.launch {
                                            val district = selectedDistrict.takeUnless { it == "Tümü" }
                                            runCatching {
                                                repository.search(
                                                    query = query.trim(),
                                                    city = selectedCity.name,
                                                    district = district,
                                                )
                                            }.onSuccess { records ->
                                                results = records
                                                if (records.isEmpty()) {
                                                    error = "Seçilen kapsamda kayıt bulunamadı; önce ilçe/kategori seçerek daha hedefli arama yapabilirsiniz."
                                                } else {
                                                    crmMessage = "§{records.size} gerçek/envanter kaydı getirildi. Daha önce bulunan kayıtlar cihazdaki yerel envanter önbelleğinde korunur."
                                                }
                                            }.onFailure { throwable ->
                                                val fallbackText = throwable.message ?: "bilinmeyen kaynak hatası"
                                                error = if (results.isNotEmpty()) {
                                                    "Yeni kaynak taraması başarısız oldu; önceki yerel envanter korunuyor. $fallbackText"
                                                } else {
                                                    "Gerçek kaynak erişilemedi. $fallbackText"
                                                }
                                            }
                                            loading = false
                                        }
                                    },
                                    enabled = !loading,
                                ) {
                                    Text(
                                        if (loading) "Gerçek veriler getiriliyor…" else "Seçime göre gerçek verileri getir",
                                    )
                                }
                                Text(
                                    "Veri ağı: ANA OSM Overpass bölgesel envanter → ALTERNATİF Nominatim hedefli arama → FALLBACK cihaz yerel önbelleği. TOBB/oda, Google Maps/Places ve sosyal medya kaynakları için lisans/API erişimi ayrıca bağlanabilir; maliyet oluşturan servis otomatik etkinleştirilmez.",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                    item {
                        Text(
                            "OSM katkıları: Overpass/OSM bölgesel veri + hedefli Nominatim araması. Daha önce toplanan kayıtlar yeni aramalarda kaybolmaz.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Text("© OpenStreetMap contributors · ODbL", style = MaterialTheme.typography.bodySmall)
                    }
                    item {
                        if (filteredCrmCustomers.isNotEmpty()) {
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Column(
                                    Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Text("CRM hızlı erişim", style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        "Son kayıtlar arama ve ilçe değişimlerinden bağımsız burada kalır.",
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                    filteredCrmCustomers.take(5).forEach { customer ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                        ) {
                                            Column(Modifier.weight(1f)) {
                                                Text(customer.businessName)
                                                Text(
                                                    customer.stage.name,
                                                    style = MaterialTheme.typography.bodySmall,
                                                )
                                            }
                                            OutlinedButton(
                                                onClick = { selectedCustomerId = customer.id },
                                                modifier = Modifier.testTag("crm_open_" + customer.id),
                                            ) {
                                                Text("CRM detayını aç")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    error?.let { message -> item { Card(modifier = Modifier.fillMaxWidth()) { Text(message, modifier = Modifier.padding(16.dp)) } } }
                    crmMessage?.let { message -> item { Card(modifier = Modifier.fillMaxWidth()) { Text(message, modifier = Modifier.padding(16.dp)) } } }
                    selectedBusiness?.let { business -> item { BusinessDetailCard(business = business, onClose = { selectedBusiness = null }) } }
                    item {
                        SalesDashboard(
                            selectedCity = selectedCity.name,
                            selectedDistrict = selectedDistrict,
                            availableDistricts = selectedCity.districts,
                            metrics = dashboardMetrics,
                            onDistrictSelected = {
                                selectedDistrict = it
                                selectedBusiness = null
                                selectedCustomerId = null
                            },
                        )
                    }
                    item {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("Senkronizasyon durumu", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    if (pendingSyncCount == 0) {
                                        "Bekleyen yerel değişiklik yok."
                                    } else {
                                        "$pendingSyncCount değişiklik yerelde güvenle bekliyor."
                                    },
                                )
                                Text(
                                    "İnternet veya oturum yoksa kayıtlar cihazda bekler; bağlantı sağlanınca WorkManager güvenli senkronizasyonu dener.",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                    item {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text("Yerel CRM", style = MaterialTheme.typography.titleMedium)
                                Text("${filteredCrmCustomers.size} CRM kaydı kalıcı olarak saklanıyor.")
                                Text(
                                    "Arama sonuçları otomatik müşteriye dönüşmez; kaydetme kullanıcı eylemidir.",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                    items(
                        items = filteredCrmCustomers.take(25),
                        key = { it.id },
                    ) { customer ->
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text(customer.businessName, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "${customer.city} • ${customer.district}" +
                                        (customer.neighborhood?.let { " • ${it}" } ?: ""),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                Text("Aşama: " + customer.stage.name, style = MaterialTheme.typography.bodySmall)
                                OutlinedButton(
                                    onClick = { selectedCustomerId = customer.id },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text("CRM detayını aç")
                                }
                            }
                        }
                    }
                    item {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp)) {
                                Text("Bulunan gerçek kayıtlar", style = MaterialTheme.typography.titleMedium)
                                Text(visibleResults.size.toString() + " kayıt")
                                if (visibleResults.isNotEmpty()) Text("Raporu açmak için bir işletme kaydına dokunun.", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    if (visibleResults.isNotEmpty()) {
                        item {
                            Text("Harita", style = MaterialTheme.typography.titleMedium)
                            Text("Harita yalnızca bu kullanıcı aramasından dönen gerçek koordinatları gösterir; toplu şehir taraması yapmaz.", style = MaterialTheme.typography.bodySmall)
                        }
                        item { BusinessMapPreview(businesses = visibleResults) }
                        item { Text("© OpenStreetMap contributors · ODbL", style = MaterialTheme.typography.bodySmall) }
                    }
                    items(visibleResults, key = { it.id }) { business ->
                        BusinessResultCard(
                            business = business,
                            onClick = { selectedBusiness = business },
                            onSaveToCrm = {
                                scope.launch {
                                    runCatching { localCrmRepository.addBusinessAsCustomer(business) }
                                        .onSuccess { customer -> crmMessage = "CRM: ${customer.businessName} kaydı kalıcı yerel CRM'e alındı / zaten kayıtlı." }
                                        .onFailure { throwable -> crmMessage = "CRM kaydı yapılamadı: ${throwable.message ?: "bilinmeyen hata"}" }
                                }
                            },
                        )
                    }
                    item {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp)) {
                                Text("Veri sınırı", style = MaterialTheme.typography.titleMedium)
                                Text("OSM kaydı bulunan işletmeler gösterilir. Çalışan sayısı, satış potansiyeli ve benzeri alanlar kaynakta yoksa uygulama bunları uydurmaz.")
                            }
                        }
                    }

                    auth?.let { cloudAuth ->
                        item { SupabaseSessionCard(cloudAuth) }
                    }
                }
                    }
                    AppSection.PRODUCT_CATALOG -> {
                        ProductCatalogScreen(repository = productCatalogRepository)
                    }
                    AppSection.MANUAL_POINT -> {
                        ManualPointScreen(
                            repository = localCrmRepository,
                            defaultCity = selectedCity.name,
                            onSaved = { navigateTo(AppSection.ROUTINE) },
                        )
                    }
                    AppSection.ROUTINE -> {
                        RoutineScreen(
                            customers = crmCustomers,
                            selectedCity = selectedCity.name,
                            selectedDistrict = selectedDistrict,
                        )
                    }
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
                        onStageChange = { target, note ->
                            scope.launch {
                                runCatching {
                                    localCrmRepository.transitionStage(
                                        customerId = customer.id,
                                        to = target,
                                        changedByUserId = null,
                                        note = note,
                                    )
                                }.onSuccess {
                                    crmMessage = "Aşama güncellendi: " + customer.businessName + " → " + target.name
                                }.onFailure {
                                    crmMessage = "Aşama değiştirilemedi: " + (it.message ?: "bilinmeyen hata")
                                }
                            }
                        },
                        onRecordActivity = { type, note ->
                            scope.launch {
                                runCatching {
                                    localCrmRepository.recordActivity(
                                        customerId = customer.id,
                                        type = type,
                                        note = note,
                                    )
                                }.onSuccess {
                                    crmMessage = "Aktivite kaydedildi."
                                }.onFailure {
                                    crmMessage = "Aktivite kaydedilemedi: " + (it.message ?: "bilinmeyen hata")
                                }
                            }
                        },
                        onCreateNextAction = { type, dueAt, note ->
                            scope.launch {
                                runCatching {
                                    localCrmRepository.createNextAction(
                                        customerId = customer.id,
                                        type = type,
                                        dueAtEpochMs = dueAt,
                                        note = note,
                                    )
                                }.onSuccess {
                                    crmMessage = "Takip planlandı."
                                }.onFailure {
                                    crmMessage = "Takip planlanamadı: " + (it.message ?: "bilinmeyen hata")
                                }
                            }
                        },
                        onCompleteNextAction = { actionId ->
                            scope.launch {
                                runCatching {
                                    localCrmRepository.completeNextAction(actionId)
                                }.onSuccess {
                                    crmMessage = "Takip tamamlandı ve aktivite geçmişine işlendi."
                                }.onFailure {
                                    crmMessage = "Takip tamamlanamadı: " + (it.message ?: "bilinmeyen hata")
                                }
                            }
                        },
                        onCreateOpportunity = { title, notes, estimatedValueMinor, currency ->
                            scope.launch {
                                runCatching {
                                    localCrmRepository.createOpportunity(
                                        customerId = customer.id,
                                        title = title,
                                        notes = notes,
                                        estimatedValueMinor = estimatedValueMinor,
                                        currency = currency,
                                        valueOrigin = if (estimatedValueMinor == null) {
                                            com.lanu.globaldonuksatisradari.crm.CrmValueOrigin.UNKNOWN
                                        } else {
                                            com.lanu.globaldonuksatisradari.crm.CrmValueOrigin.USER_ENTERED
                                        },
                                    )
                                }.onSuccess {
                                    crmMessage = "Satış fırsatı kaydedildi."
                                }.onFailure {
                                    crmMessage = "Satış fırsatı kaydedilemedi: " + (it.message ?: "bilinmeyen hata")
                                }
                            }
                        },
                        onTransitionOpportunity = { opportunityId, status ->
                            scope.launch {
                                runCatching {
                                    localCrmRepository.transitionOpportunity(
                                        opportunityId = opportunityId,
                                        status = status,
                                    )
                                }.onSuccess {
                                    crmMessage = "Fırsat durumu güncellendi."
                                }.onFailure {
                                    crmMessage = "Fırsat durumu güncellenemedi: " + (it.message ?: "bilinmeyen hata")
                                }
                            }
                        },
                        onSaveNotes = { notes ->
                            scope.launch {
                                runCatching {
                                    localCrmRepository.updateCustomerNotes(customer.id, notes)
                                }.onSuccess {
                                    crmMessage = "Müşteri notu kaydedildi."
                                }.onFailure {
                                    crmMessage = "Müşteri notu kaydedilemedi: " + (it.message ?: "bilinmeyen hata")
                                }
                            }
                        },
                        message = crmMessage,
                    )

                }
            }
        }
    }
}

@Composable
private fun BusinessResultCard(business: VerifiedBusiness, onClick: () -> Unit, onSaveToCrm: () -> Unit) {
    val context = LocalContext.current
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(business.name, style = MaterialTheme.typography.titleMedium)
            Text("${business.city} • ${business.district}${business.neighborhood?.let { " • $it" } ?: ""}")
            business.category?.let { Text("Kategori: $it") }
            business.address?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            business.phone?.let { Text("Telefon: $it", style = MaterialTheme.typography.bodySmall) }
            business.website?.let { Text("Web: $it", style = MaterialTheme.typography.bodySmall) }
            business.openingHours?.let { Text("Saatler: $it", style = MaterialTheme.typography.bodySmall) }
            business.menuUrl?.let { Text("Menü: $it", style = MaterialTheme.typography.bodySmall) }
            business.menuText?.let { Text("Menü bilgisi: $it", style = MaterialTheme.typography.bodySmall) }
            business.latitude?.let { lat -> business.longitude?.let { lon -> Text("Koordinat: $lat, $lon", style = MaterialTheme.typography.bodySmall) } }
            Text("Kaynak: ${business.source.name}", style = MaterialTheme.typography.bodySmall)
            Text("Telefon ve menü yalnızca gerçek kaynakta mevcutsa gösterilir.", style = MaterialTheme.typography.bodySmall)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                business.phone?.let { phone ->
                    OutlinedButton(
                        onClick = { context.startActivity(android.content.Intent(android.content.Intent.ACTION_DIAL, android.net.Uri.parse("tel:${android.net.Uri.encode(phone)}"))) },
                        modifier = Modifier.weight(1f),
                    ) { Text("Ara") }
                }
                business.menuUrl?.let { menu ->
                    OutlinedButton(
                        onClick = { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(menu))) },
                        modifier = Modifier.weight(1f),
                    ) { Text("Menü") }
                }
            }
            OutlinedButton(onClick = onSaveToCrm, modifier = Modifier.fillMaxWidth()) { Text("CRM'e kaydet") }
        }
    }
}
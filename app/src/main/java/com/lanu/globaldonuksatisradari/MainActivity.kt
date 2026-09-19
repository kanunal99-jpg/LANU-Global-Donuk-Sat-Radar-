package com.lanu.globaldonuksatisradari

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import com.lanu.globaldonuksatisradari.crm.LanuCrmDatabase
import com.lanu.globaldonuksatisradari.crm.LocalCrmRepository
import com.lanu.globaldonuksatisradari.data.BusinessRepositoryFactory
import com.lanu.globaldonuksatisradari.data.NominatimBusinessSource
import com.lanu.globaldonuksatisradari.data.NominatimBusinessSourceAdapter
import com.lanu.globaldonuksatisradari.data.VerifiedBusiness
import kotlinx.coroutines.launch


data class City(val name: String, val districts: List<String>)

private val cities = listOf(
    City("İstanbul", listOf("Kadıköy", "Beşiktaş", "Şişli", "Bakırköy", "Ataşehir")),
    City("Ankara", listOf("Çankaya", "Keçiören", "Yenimahalle")),
    City("İzmir", listOf("Konak", "Karşıyaka", "Bornova")),
    City("Bursa", listOf("Nilüfer", "Osmangazi")),
    City("Antalya", listOf("Muratpaşa", "Konyaaltı"))
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CrmSyncScheduler.schedule(this)
        setContent { SalesRadarApp() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SalesRadarApp() {
    var selectedCity by remember { mutableStateOf(cities.first()) }
    var cityMenu by remember { mutableStateOf(false) }
    var districtMenu by remember { mutableStateOf(false) }
    var selectedDistrict by remember { mutableStateOf("Tümü") }
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<VerifiedBusiness>>(emptyList()) }
    var selectedBusiness by remember { mutableStateOf<VerifiedBusiness?>(null) }
    var selectedCustomerId by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var crmMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val repository = remember {
        BusinessRepositoryFactory.create(
            NominatimBusinessSource.contract,
            NominatimBusinessSourceAdapter(),
        )
    }
    val localCrmRepository = remember(context) {
        LocalCrmRepository(LanuCrmDatabase.getInstance(context))
    }
    val crmCustomers by localCrmRepository
        .observeCustomers(selectedCity.name)
        .collectAsState(initial = emptyList())
    val pendingSyncCount by localCrmRepository
        .observePendingSyncCount()
        .collectAsState(initial = 0)
    val filteredCrmCustomers = remember(crmCustomers, selectedDistrict) {
        crmCustomers.filter { selectedDistrict == "Tümü" || it.district == selectedDistrict }
    }
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

    MaterialTheme {
        Scaffold(topBar = { TopAppBar(title = { Text("LANU Global Donuk Satış Radarı") }) }) { padding ->
            if (selectedCrmCustomer == null) {
                LazyColumn(
                    modifier = Modifier
                        .testTag("main_scroll")
                        .padding(padding)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item {
                        Text("Satış Radarı", style = MaterialTheme.typography.headlineSmall)
                        Text("Gerçek kaynaklı verilerle şehir → ilçe → işletme keşfi")
                    }
                    item {
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Örn. restoran, kafe, fırın") },
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
                                        cityMenu = false
                                        results = emptyList()
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
                            OutlinedButton(onClick = { districtMenu = true }, modifier = Modifier.fillMaxWidth()) { Text("İlçe: $selectedDistrict") }
                            DropdownMenu(expanded = districtMenu, onDismissRequest = { districtMenu = false }) {
                                (listOf("Tümü") + selectedCity.districts).forEach { district ->
                                    DropdownMenuItem(text = { Text(district) }, onClick = {
                                        selectedDistrict = district
                                        districtMenu = false
                                        results = emptyList()
                                        selectedBusiness = null
                                        selectedCustomerId = null
                                        crmMessage = null
                                    })
                                }
                            }
                        }
                    }
                    item {
                        Button(
                            onClick = {
                                error = null
                                selectedBusiness = null
                                selectedCustomerId = null
                                crmMessage = null
                                if (query.isBlank()) {
                                    results = emptyList()
                                    error = "Arama için bir işletme/HORECA terimi yazın."
                                } else {
                                    loading = true
                                    scope.launch {
                                        val outcome = runCatching {
                                            repository.search(query = query, city = selectedCity.name, district = selectedDistrict.takeUnless { it == "Tümü" })
                                        }
                                        results = outcome.getOrDefault(emptyList())
                                        outcome.exceptionOrNull()?.let { error = "Kaynak erişim hatası: ${it.message ?: "bilinmeyen hata"}" }
                                        loading = false
                                    }
                                }
                            },
                            enabled = !loading,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(if (loading) "Gerçek kaynak aranıyor…" else "Gerçek kaynaktan ara") }
                    }
                    item {
                        Text("Kaynak: OpenStreetMap Nominatim • Kullanıcı tetiklemeli arama • Eksiksiz İstanbul işletme listesi değildir.", style = MaterialTheme.typography.bodySmall)
                        Text("© OpenStreetMap contributors", style = MaterialTheme.typography.bodySmall)
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
                                        "Son kayıtlar doğrudan buradan açılabilir.",
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
                                    "Backend yapılandırılana kadar kayıtlar yalnızca cihazda tutulur; uygulama bunları senkronlandı olarak işaretlemez.",
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
                                Text("${filteredCrmCustomers.size} kayıt bu filtrede kalıcı olarak saklanıyor.")
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
                                Text("${results.size} kayıt")
                                if (results.isNotEmpty()) Text("Raporu açmak için bir işletme kaydına dokunun.", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    if (results.isNotEmpty()) {
                        item {
                            Text("Harita", style = MaterialTheme.typography.titleMedium)
                            Text("Harita yalnızca bu kullanıcı aramasından dönen gerçek koordinatları gösterir; toplu şehir taraması yapmaz.", style = MaterialTheme.typography.bodySmall)
                        }
                        item { BusinessMapPreview(businesses = results) }
                        item { Text("© OpenStreetMap contributors · ODbL", style = MaterialTheme.typography.bodySmall) }
                    }
                    items(results, key = { it.id }) { business ->
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
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(business.name, style = MaterialTheme.typography.titleMedium)
            Text("${business.city} • ${business.district}${business.neighborhood?.let { " • $it" } ?: ""}")
            business.category?.let { Text("Kategori: $it") }
            business.address?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            business.phone?.let { Text("Telefon: $it", style = MaterialTheme.typography.bodySmall) }
            business.website?.let { Text("Web: $it", style = MaterialTheme.typography.bodySmall) }
            business.openingHours?.let { Text("Saatler: $it", style = MaterialTheme.typography.bodySmall) }
            business.latitude?.let { lat -> business.longitude?.let { lon -> Text("Koordinat: $lat, $lon", style = MaterialTheme.typography.bodySmall) } }
            Text("Kaynak: ${business.source.name}", style = MaterialTheme.typography.bodySmall)
            Text("Detaylı satış raporunu açmak için dokunun.", style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = onSaveToCrm, modifier = Modifier.fillMaxWidth()) { Text("CRM'e kaydet") }
        }
    }
}

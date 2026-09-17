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
import androidx.compose.ui.unit.dp
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
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val repository = remember {
        BusinessRepositoryFactory.create(
            NominatimBusinessSource.contract,
            NominatimBusinessSourceAdapter(),
        )
    }

    MaterialTheme {
        Scaffold(topBar = { TopAppBar(title = { Text("LANU Global Donuk Satış Radarı") }) }) { padding ->
            LazyColumn(
                modifier = Modifier
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
                        OutlinedButton(
                            onClick = { cityMenu = true },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Şehir: ${selectedCity.name}") }
                        DropdownMenu(
                            expanded = cityMenu,
                            onDismissRequest = { cityMenu = false },
                        ) {
                            cities.forEach { city ->
                                DropdownMenuItem(
                                    text = { Text(city.name) },
                                    onClick = {
                                        selectedCity = city
                                        selectedDistrict = "Tümü"
                                        cityMenu = false
                                        results = emptyList()
                                        selectedBusiness = null
                                    },
                                )
                            }
                        }
                    }
                }
                item {
                    Box {
                        OutlinedButton(
                            onClick = { districtMenu = true },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("İlçe: $selectedDistrict") }
                        DropdownMenu(
                            expanded = districtMenu,
                            onDismissRequest = { districtMenu = false },
                        ) {
                            (listOf("Tümü") + selectedCity.districts).forEach { district ->
                                DropdownMenuItem(
                                    text = { Text(district) },
                                    onClick = {
                                        selectedDistrict = district
                                        districtMenu = false
                                        results = emptyList()
                                        selectedBusiness = null
                                    },
                                )
                            }
                        }
                    }
                }
                item {
                    Button(
                        onClick = {
                            error = null
                            selectedBusiness = null
                            if (query.isBlank()) {
                                results = emptyList()
                                error = "Arama için bir işletme/HORECA terimi yazın."
                            } else {
                                loading = true
                                scope.launch {
                                    val outcome = runCatching {
                                        repository.search(
                                            query = query,
                                            city = selectedCity.name,
                                            district = selectedDistrict.takeUnless { it == "Tümü" },
                                        )
                                    }
                                    results = outcome.getOrDefault(emptyList())
                                    outcome.exceptionOrNull()?.let {
                                        error = "Kaynak erişim hatası: ${it.message ?: "bilinmeyen hata"}"
                                    }
                                    loading = false
                                }
                            }
                        },
                        enabled = !loading,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(if (loading) "Gerçek kaynak aranıyor…" else "Gerçek kaynaktan ara") }
                }
                item {
                    Text(
                        "Kaynak: OpenStreetMap Nominatim • Kullanıcı tetiklemeli arama • Eksiksiz İstanbul işletme listesi değildir.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text("© OpenStreetMap contributors", style = MaterialTheme.typography.bodySmall)
                }
                error?.let { message ->
                    item {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Text(message, modifier = Modifier.padding(16.dp))
                        }
                    }
                }
                selectedBusiness?.let { business ->
                    item {
                        BusinessDetailCard(
                            business = business,
                            onClose = { selectedBusiness = null },
                        )
                    }
                }
                item {
                    SalesDashboard(
                        selectedCity = selectedCity.name,
                        selectedDistrict = selectedDistrict,
                        availableDistricts = selectedCity.districts,
                        onDistrictSelected = {
                            selectedDistrict = it
                            selectedBusiness = null
                        },
                    )
                }
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text("Bulunan gerçek kayıtlar", style = MaterialTheme.typography.titleMedium)
                            Text("${results.size} kayıt")
                            if (results.isNotEmpty()) {
                                Text("Raporu açmak için bir işletme kaydına dokunun.", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
                if (results.isNotEmpty()) {
                    item {
                        Text("Harita", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Harita yalnızca bu kullanıcı aramasından dönen gerçek koordinatları gösterir; toplu şehir taraması yapmaz.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    item {
                        BusinessMapPreview(businesses = results)
                    }
                    item {
                        Text("© OpenStreetMap contributors · ODbL", style = MaterialTheme.typography.bodySmall)
                    }
                }
                items(results, key = { it.id }) { business ->
                    BusinessResultCard(
                        business = business,
                        onClick = { selectedBusiness = business },
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
        }
    }
}

@Composable
private fun BusinessResultCard(
    business: VerifiedBusiness,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(business.name, style = MaterialTheme.typography.titleMedium)
            Text("${business.city} • ${business.district}${business.neighborhood?.let { " • $it" } ?: ""}")
            business.category?.let { Text("Kategori: $it") }
            business.address?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            business.phone?.let { Text("Telefon: $it", style = MaterialTheme.typography.bodySmall) }
            business.website?.let { Text("Web: $it", style = MaterialTheme.typography.bodySmall) }
            business.openingHours?.let { Text("Saatler: $it", style = MaterialTheme.typography.bodySmall) }
            business.latitude?.let { lat ->
                business.longitude?.let { lon ->
                    Text("Koordinat: $lat, $lon", style = MaterialTheme.typography.bodySmall)
                }
            }
            Text("Kaynak: ${business.source.name}", style = MaterialTheme.typography.bodySmall)
            Text("Detaylı satış raporunu açmak için dokunun.", style = MaterialTheme.typography.bodySmall)
        }
    }
}

package com.lanu.globaldonuksatisradari

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

data class City(val name: String, val districts: List<String>)
data class Business(val name: String, val type: String, val district: String, val potential: Int)

private val cities = listOf(
    City("İstanbul", listOf("Kadıköy", "Beşiktaş", "Şişli", "Bakırköy", "Ataşehir")),
    City("Ankara", listOf("Çankaya", "Keçiören", "Yenimahalle")),
    City("İzmir", listOf("Konak", "Karşıyaka", "Bornova")),
    City("Bursa", listOf("Nilüfer", "Osmangazi")),
    City("Antalya", listOf("Muratpaşa", "Konyaaltı"))
)

private val demoBusinesses = listOf(
    Business("Örnek HORECA İşletmesi", "Restoran", "Kadıköy", 82),
    Business("Örnek Cafe", "Kafe", "Kadıköy", 64),
    Business("Örnek Mutfak", "Catering", "Beşiktaş", 91)
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { SalesRadarApp() }
    }
}

@Composable
fun SalesRadarApp() {
    var selectedCity by remember { mutableStateOf(cities.first()) }
    var cityMenu by remember { mutableStateOf(false) }
    var selectedDistrict by remember { mutableStateOf("Tümü") }
    var query by remember { mutableStateOf("") }

    MaterialTheme {
        Scaffold(topBar = { TopAppBar(title = { Text("LANU Global Donuk Satış Radarı") }) }) { padding ->
            LazyColumn(modifier = Modifier.padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    Text("Satış Radarı", style = MaterialTheme.typography.headlineSmall)
                    Text("Gerçek kaynaklı verilerle şehir → ilçe → mahalle → işletme keşfi")
                }
                item {
                    OutlinedTextField(value = query, onValueChange = { query = it }, modifier = Modifier.fillMaxWidth(), label = { Text("İşletme / şehir / ilçe ara") })
                }
                item {
                    Box {
                        OutlinedButton(onClick = { cityMenu = true }, modifier = Modifier.fillMaxWidth()) { Text("Şehir: ${selectedCity.name}") }
                        DropdownMenu(expanded = cityMenu, onDismissRequest = { cityMenu = false }) {
                            cities.forEach { city ->
                                DropdownMenuItem(text = { Text(city.name) }, onClick = { selectedCity = city; selectedDistrict = "Tümü"; cityMenu = false })
                            }
                        }
                    }
                }
                item {
                    ScrollableTabRow(selectedTabIndex = 0) {
                        listOf("Tümü" to "Tümü") + selectedCity.districts.map { it to it }.forEach { (label, value) ->
                            Tab(selected = selectedDistrict == value, onClick = { selectedDistrict = value }, text = { Text(label) })
                        }
                    }
                }
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text("Saha özeti", style = MaterialTheme.typography.titleMedium)
                            Text("Seçili şehir: ${selectedCity.name}")
                            Text("İlçe: $selectedDistrict")
                            Text("Veri durumu: başlangıç / kaynaklandırılacak")
                        }
                    }
                }
                items(demoBusinesses.filter { selectedDistrict == "Tümü" || it.district == selectedDistrict }.filter { query.isBlank() || it.name.contains(query, true) || it.type.contains(query, true) }) { business ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text(business.name, style = MaterialTheme.typography.titleMedium)
                            Text("${business.type} • ${business.district}")
                            Text("Potansiyel: ${business.potential}/100 — DEMO VERİ")
                            Spacer(Modifier.height(8.dp))
                            Button(onClick = { }) { Text("A–Z satış raporunu aç") }
                        }
                    }
                }
            }
        }
    }
}

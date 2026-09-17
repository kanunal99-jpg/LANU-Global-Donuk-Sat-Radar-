package com.lanu.globaldonuksatisradari

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

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
    var selectedDistrict by remember { mutableStateOf("Tümü") }
    var query by remember { mutableStateOf("") }

    MaterialTheme {
        Scaffold(topBar = { TopAppBar(title = { Text("LANU Global Donuk Satış Radarı") }) }) { padding ->
            LazyColumn(
                modifier = Modifier.padding(padding).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Text("Satış Radarı", style = MaterialTheme.typography.headlineSmall)
                    Text("Gerçek kaynaklı verilerle şehir → ilçe → mahalle → işletme keşfi")
                }
                item {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("İşletme / şehir / ilçe ara") }
                    )
                }
                item {
                    Box {
                        OutlinedButton(
                            onClick = { cityMenu = true },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Şehir: ${selectedCity.name}") }
                        DropdownMenu(
                            expanded = cityMenu,
                            onDismissRequest = { cityMenu = false }
                        ) {
                            cities.forEach { city ->
                                DropdownMenuItem(
                                    text = { Text(city.name) },
                                    onClick = {
                                        selectedCity = city
                                        selectedDistrict = "Tümü"
                                        cityMenu = false
                                    }
                                )
                            }
                        }
                    }
                }
                item {
                    SalesDashboard(
                        selectedCity = selectedCity.name,
                        selectedDistrict = selectedDistrict,
                        onDistrictSelected = { selectedDistrict = it }
                    )
                }
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text("Arama durumu", style = MaterialTheme.typography.titleMedium)
                            Text("Arama: ${query.ifBlank { "tümü" }}")
                            Text("İlçe: $selectedDistrict")
                        }
                    }
                }
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text("Veri güvenliği", style = MaterialTheme.typography.titleMedium)
                            Text("Henüz doğrulanmış işletme verisi yok. Uygulama bilinmeyen işletmeleri, çalışan sayılarını veya satış rakamlarını gerçekmiş gibi göstermeyecek.")
                        }
                    }
                }
            }
        }
    }
}

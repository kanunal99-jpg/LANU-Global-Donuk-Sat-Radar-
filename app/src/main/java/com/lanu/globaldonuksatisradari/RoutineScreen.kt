package com.lanu.globaldonuksatisradari

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lanu.globaldonuksatisradari.crm.CrmCustomer
import com.lanu.globaldonuksatisradari.crm.CrmRoutePlanner
import com.lanu.globaldonuksatisradari.crm.RouteStop

@Composable
fun RoutineScreen(
    customers: List<CrmCustomer>,
    selectedCity: String,
    selectedDistrict: String,
) {
    var startId by remember(customers) { mutableStateOf<String?>(null) }
    val scopedCustomers = remember(customers, selectedCity, selectedDistrict) {
        customers.filter {
            it.city == selectedCity &&
                (selectedDistrict == "Tümü" || it.district == selectedDistrict)
        }
    }
    val routable = remember(scopedCustomers) {
        scopedCustomers.filter { it.latitude != null && it.longitude != null }
    }
    val route = remember(routable, startId) {
        CrmRoutePlanner.plan(routable, startId)
    }
    val missingCoordinates = scopedCustomers.size - routable.size
    val startName = route.firstOrNull()?.customer?.businessName ?: "Otomatik başlangıç"

    LazyColumn(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text("Yakınlık Bazlı Rutin", style = MaterialTheme.typography.headlineSmall)
            Text("Aynı şehir/ilçe içindeki koordinatlı müşteriler, bir önceki noktaya en yakın sıraya göre dizilir.")
        }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Alan: " + selectedCity + " / " + selectedDistrict)
                    Text("Rota noktası: " + routable.size + " • Koordinatsız: " + missingCoordinates)
                    Text("Başlangıç: " + startName)
                    if (startId != null) {
                        OutlinedButton(onClick = { startId = null }, modifier = Modifier.fillMaxWidth()) {
                            Text("Başlangıcı otomatiğe al")
                        }
                    }
                }
            }
        }
        if (routable.size >= 2) {
            item { Text("Başlangıç seçmek için aşağıdaki sıradaki müşteriyi kullanabilirsiniz.") }
        }
        items(route, key = { it.customer.id }) { stop ->
            RouteStopCard(stop = stop, onChooseStart = { startId = stop.customer.id })
        }
        if (route.isNotEmpty()) {
            item {
                val total = route.last().cumulativeDistanceKm
                Text(
                    "Tahmini toplam kuş uçuşu rota: " + "%.2f".format(total) + " km",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text("Mesafe, iki koordinat arasındaki Haversine mesafesidir; trafik ve yol ağı süresi hesaplanmaz.")
            }
        } else {
            item { Text("Rutin oluşturmak için en az bir müşterinin geçerli X/Y koordinatı olmalı.") }
        }
    }
}

@Composable
private fun RouteStopCard(stop: RouteStop, onChooseStart: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(stop.order.toString() + ". " + stop.customer.businessName, style = MaterialTheme.typography.titleMedium)
            Text(
                stop.customer.city + " / " + stop.customer.district +
                    (stop.customer.neighborhood?.let { " / " + it } ?: ""),
            )
            stop.customer.address?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            Text(
                "Önceki noktaya: " + "%.2f".format(stop.distanceFromPreviousKm) +
                    " km • Kümülatif: " + "%.2f".format(stop.cumulativeDistanceKm) + " km",
            )
            Text(
                "X " + "%.6f".format(stop.customer.longitude) +
                    " • Y " + "%.6f".format(stop.customer.latitude),
            )
            OutlinedButton(onClick = onChooseStart, modifier = Modifier.fillMaxWidth()) {
                Text("Rutine bu noktadan başla")
            }
        }
    }
}

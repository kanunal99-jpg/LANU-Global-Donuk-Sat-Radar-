package com.lanu.globaldonuksatisradari

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun SalesDashboard(
    selectedCity: String,
    selectedDistrict: String,
    onDistrictSelected: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Satış Dashboard", style = MaterialTheme.typography.headlineSmall)
        Text("Gerçek CRM verisi bağlandığında otomatik dolacak Power BI benzeri saha özeti.")

        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            DashboardCard("Müşteriler", "—", "Henüz veri yok")
            DashboardCard("Potansiyeller", "—", "Henüz veri yok")
            DashboardCard("Ziyaretler", "—", "Henüz veri yok")
            DashboardCard("Teklifler", "—", "Henüz veri yok")
            DashboardCard("Siparişler", "—", "Henüz veri yok")
            DashboardCard("Satış", "—", "Henüz veri yok")
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Filtreler", style = MaterialTheme.typography.titleMedium)
                Text("Şehir: $selectedCity")
                Text("İlçe: $selectedDistrict")
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedDistrict == "Tümü",
                        onClick = { onDistrictSelected("Tümü") },
                        label = { Text("Tümü") }
                    )
                    listOf("Kadıköy", "Beşiktaş", "Şişli", "Bakırköy", "Ataşehir").forEach { district ->
                        FilterChip(
                            selected = selectedDistrict == district,
                            onClick = { onDistrictSelected(district) },
                            label = { Text(district) }
                        )
                    }
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Satış Hunisi", style = MaterialTheme.typography.titleMedium)
                FunnelRow("Potansiyel", "—")
                FunnelRow("Ziyaret", "—")
                FunnelRow("Görüşme", "—")
                FunnelRow("Teklif", "—")
                FunnelRow("Numune", "—")
                FunnelRow("Sipariş", "—")
                FunnelRow("Aktif müşteri", "—")
                Text(
                    "Veri kaynağı bağlanmadan sayı gösterilmez. Bu ekran sahte KPI üretmez.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Bölge Analizi", style = MaterialTheme.typography.titleMedium)
                Text("Şehir → ilçe → mahalle → işletme kırılımı gerçek veri kaynağı geldiğinde burada gösterilecek.")
                Text("Seçili şehir: $selectedCity")
                Spacer(Modifier.height(2.dp))
                Text("Harita katmanı: veri kaynağı ve lisans doğrulamasından sonra etkinleştirilecek.")
            }
        }
    }
}

@Composable
private fun DashboardCard(title: String, value: String, subtitle: String) {
    Card(modifier = Modifier.width(150.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge)
            Text(value, style = MaterialTheme.typography.headlineSmall)
            Text(subtitle, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun FunnelRow(stage: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(stage)
        Text(value, style = MaterialTheme.typography.titleMedium)
    }
}

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
import com.lanu.globaldonuksatisradari.crm.CrmDashboardMetrics

@Composable
fun SalesDashboard(
    selectedCity: String,
    selectedDistrict: String,
    availableDistricts: List<String>,
    metrics: CrmDashboardMetrics,
    onDistrictSelected: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Satış Dashboard", style = MaterialTheme.typography.headlineSmall)
        Text("Cihazda kalıcı CRM verisinden hesaplanan saha özeti.")

        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            DashboardCard("Müşteriler", metrics.customers.toString(), "Yerel CRM")
            DashboardCard("Potansiyeller", metrics.prospects.toString(), "Prospect")
            DashboardCard("Gerçek ziyaret", metrics.visitActivities.toString(), "Aktivite")
            DashboardCard("Arama", metrics.callActivities.toString(), "Aktivite")
            DashboardCard("Teklif aşaması", metrics.proposals.toString(), "Pipeline")
            DashboardCard("Açık takip", metrics.openNextActions.toString(), "Next Action")
            DashboardCard("Geciken takip", metrics.overdueNextActions.toString(), "Next Action")
            DashboardCard("Açık fırsat", metrics.openOpportunities.toString(), "CRM Fırsatı")
            DashboardCard("Kazanılan fırsat", metrics.wonOpportunities.toString(), "CRM Fırsatı")
            DashboardCard("Kayıp fırsat", metrics.lostOpportunities.toString(), "CRM Fırsatı")
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
                    availableDistricts.forEach { district ->
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
                Text("Gerçekleşen faaliyetler", style = MaterialTheme.typography.titleMedium)
                Text("Ziyaret: ${metrics.visitActivities}")
                Text("Arama: ${metrics.callActivities}")
                Text("Görüşme: ${metrics.meetingActivities}")
                Text("Numune: ${metrics.sampleActivities}")
                Text("Teklif: ${metrics.proposalActivities}")
                Text("Sipariş: ${metrics.orderActivities}")
                Text("Açık fırsat: " + metrics.openOpportunities)
                Text("Kazanılan fırsat: " + metrics.wonOpportunities)
                Text("Kayıp fırsat: " + metrics.lostOpportunities)
                Text(
                    "Faaliyet sayıları yalnızca kalıcı CRM aktivite kayıtlarından hesaplanır.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Satış Hunisi", style = MaterialTheme.typography.titleMedium)
                FunnelRow("Potansiyel", metrics.prospects.toString())
                FunnelRow("Ziyaret", metrics.visits.toString())
                FunnelRow("Görüşme", metrics.meetings.toString())
                FunnelRow("Teklif", metrics.proposals.toString())
                FunnelRow("Numune", metrics.samples.toString())
                FunnelRow("Sipariş", metrics.orders.toString())
                FunnelRow("Aktif müşteri", metrics.activeCustomers.toString())
                Text(
                    "Bu sayılar yalnızca cihazdaki kaydedilmiş CRM kayıtlarından hesaplanır; olmayan satış tutarı veya müşteri sayısı uydurulmaz.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Bölge Analizi", style = MaterialTheme.typography.titleMedium)
                Text("Şehir → ilçe → müşteri kırılımı yerel CRM kayıtlarından genişletilebilir.")
                Text("Seçili şehir: $selectedCity")
                Spacer(Modifier.height(2.dp))
                Text("Kaynak araması ile CRM kayıtları birbirinden ayrı tutulur.")
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

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lanu.globaldonuksatisradari.crm.CrmDashboardMetrics

@Composable
fun SalesDashboard(
    selectedCity: String,
    selectedDistrict: String,
    metrics: CrmDashboardMetrics,
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
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Aktif kapsam", style = MaterialTheme.typography.titleMedium)
                Text("Şehir: $selectedCity")
                Text("İlçe: $selectedDistrict")
                Text(
                    "Kapsam Radar seçiminden gelir; Dashboard ikinci bir filtre üretmez.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Gerçekleşen faaliyetler", style = MaterialTheme.typography.titleMedium)
                DashboardMetricGrid(
                    items = listOf(
                        "Ziyaret" to metrics.visitActivities,
                        "Arama" to metrics.callActivities,
                        "Görüşme" to metrics.meetingActivities,
                        "Numune" to metrics.sampleActivities,
                        "Teklif" to metrics.proposalActivities,
                        "Sipariş" to metrics.orderActivities,
                        "Açık fırsat" to metrics.openOpportunities,
                        "Kazanılan" to metrics.wonOpportunities,
                        "Kayıp" to metrics.lostOpportunities,
                    ),
                )
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


@Composable
private fun DashboardMetricGrid(items: List<Pair<String, Int>>) {
    items.chunked(2).forEach { rowItems ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            rowItems.forEach { (label, value) ->
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = MaterialTheme.shapes.medium,
                    tonalElevation = 1.dp,
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(label, style = MaterialTheme.typography.labelMedium)
                        Text(value.toString(), style = MaterialTheme.typography.titleLarge)
                    }
                }
            }
            if (rowItems.size == 1) {
                Spacer(Modifier.weight(1f))
            }
        }
    }
}

package com.lanu.globaldonuksatisradari

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Operational dashboard. It deliberately does not invent employee counts,
 * turnover or sales potential; those remain unavailable unless a verified
 * source supplies them.
 */
@Composable
fun SalesDashboard(
    selectedCity: String,
    selectedDistrict: String,
    onDistrictSelected: (String) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("Satış Radarı Durumu", style = MaterialTheme.typography.titleMedium)
            Text("Bölge: $selectedCity • $selectedDistrict")
            Text("Önceliklendirme: gerçek kaynak kaydı geldikten sonra yapılır.")
            Text("Çalışan sayısı, ciro ve satış potansiyeli kaynakta yoksa tahmin olarak gösterilmez.")
        }
    }
}

package com.lanu.globaldonuksatisradari

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

@Composable
fun MoreMenuCard(
    onProducts: () -> Unit,
    onManualPoint: () -> Unit,
    onAi: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("more_menu_card"),
    ) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Daha Fazla", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Ürün yönetimi, manuel nokta, AI, resmî sicil ve bulut senkronizasyon araçları.",
                style = MaterialTheme.typography.bodyMedium,
            )
            OutlinedButton(
                onClick = onProducts,
                modifier = Modifier.fillMaxWidth().testTag("more_products"),
            ) {
                Text("Ürün Kataloğu")
            }
            OutlinedButton(
                onClick = onManualPoint,
                modifier = Modifier.fillMaxWidth().testTag("more_manual_point"),
            ) {
                Text("Manuel Nokta")
            }
            OutlinedButton(
                onClick = onAi,
                modifier = Modifier.fillMaxWidth().testTag("more_ai"),
            ) {
                Text("AI Satış Asistanı")
            }
        }
    }
}

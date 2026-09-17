package com.lanu.globaldonuksatisradari

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lanu.globaldonuksatisradari.data.VerifiedBusiness

@Composable
fun BusinessDetailCard(
    business: VerifiedBusiness,
    onClose: () -> Unit,
) {
    val opportunity = buildSalesOpportunity(business.category)

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("İşletme Raporu", style = MaterialTheme.typography.headlineSmall)
                OutlinedButton(onClick = onClose) { Text("Listeye dön") }
            }

            Text(business.name, style = MaterialTheme.typography.titleLarge)
            Text("${business.city} • ${business.district}${business.neighborhood?.let { " • $it" } ?: ""}")
            business.category?.let { Text("Kategori: $it") }
            business.address?.let { Text("Adres: $it") }
            if (business.latitude != null && business.longitude != null) {
                Text("Koordinat: ${business.latitude}, ${business.longitude}")
            }

            Text("Kaynak doğrulaması", style = MaterialTheme.typography.titleMedium)
            Text("${business.source.name} • ${business.source.publisher}")
            Text("Kaynak kullanım şartı: ${business.source.licenseOrTerms}")
            Text("Kaynak: ${business.source.sourceUrl}", style = MaterialTheme.typography.bodySmall)
            Text("Uygulama kaydının doğrulama zamanı: ${business.verifiedAtEpochMs}", style = MaterialTheme.typography.bodySmall)

            Text("Ticari değerlendirme", style = MaterialTheme.typography.titleMedium)
            Text(opportunity.focus)
            Text("Çalışan sayısı: Kaynakta yok — saha/işletme doğrulaması gerekli.")
            Text("Satış potansiyeli: Hesaplanmadı — günlük porsiyon, çalışma günü, fiyat/marj ve gerçek ihtiyaç verisi gerekli.")

            Text("Saha keşif soruları", style = MaterialTheme.typography.titleMedium)
            opportunity.discoveryQuestions.forEachIndexed { index, question ->
                Text("${index + 1}. $question")
            }

            Text("Önerilen görüşme çerçevesi", style = MaterialTheme.typography.titleMedium)
            Text(opportunity.conversation)

            Text("Satış potansiyeli hesaplama girdileri", style = MaterialTheme.typography.titleMedium)
            Text("Günlük hedef porsiyon × çalışma günü × doğrulanmış net birim katkı = dönemsel katkı tahmini. Değerler işletmeden veya doğrulanmış ticari kaynaktan alınmadan hesap yapılmaz.")

            Text("Bu rapor yalnızca doğrulanmış kaynak alanlarını gerçek kabul eder. Eksik alanlar tahmin olarak saklanmaz.", style = MaterialTheme.typography.bodySmall)

            Button(onClick = onClose, modifier = Modifier.fillMaxWidth()) {
                Text("Raporu kapat")
            }
        }
    }
}

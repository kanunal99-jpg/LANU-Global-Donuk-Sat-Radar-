package com.lanu.globaldonuksatisradari

import android.content.Intent
import android.net.Uri
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.lanu.globaldonuksatisradari.data.VerifiedBusiness

@Composable
fun BusinessDetailCard(
    business: VerifiedBusiness,
    onClose: () -> Unit,
) {
    val opportunity = buildSalesOpportunity(business.category)
    val context = LocalContext.current

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

            Text("Veri kimliği ve kaynak kanıtı", style = MaterialTheme.typography.titleMedium)
            Text("Operasyonel durum: ${business.operationalStatus}")
            Text("NACE: ${business.naceCode ?: "Doğrulanmış kaynakta yok"}")
            Text("Hukuki unvan: ${business.legalName ?: "Doğrulanmış kaynakta yok"}")
            Text("Ticaret sicil no: ${business.tradeRegistryNumber ?: "Doğrulanmış kaynakta yok"}")
            val evidence = business.effectiveEvidence()
            Text("Alan kanıtı: ${evidence.size} doğrulanmış alan", style = MaterialTheme.typography.bodySmall)
            evidence.take(8).forEach { item ->
                Text("• ${item.field} ← ${item.source.name}", style = MaterialTheme.typography.bodySmall)
            }
            Text("Kaynak doğrulaması", style = MaterialTheme.typography.titleMedium)
            Text("${business.source.name} • ${business.source.publisher}")
            Text("Kaynak kullanım şartı: ${business.source.licenseOrTerms}")
            Text("Kaynak: ${business.source.sourceUrl}", style = MaterialTheme.typography.bodySmall)
            Text("Uygulama kaydının doğrulama zamanı: ${business.verifiedAtEpochMs}", style = MaterialTheme.typography.bodySmall)

            Text("Kaynakta bulunan iletişim", style = MaterialTheme.typography.titleMedium)
            Text("Telefon: ${business.phone ?: "Kaynakta yok"}")
            Text("Web: ${business.website ?: "Kaynakta yok"}")
            Text("Çalışma saatleri: ${business.openingHours ?: "Kaynakta yok"}")
            Text("Menü: ${business.menuUrl ?: business.menuText ?: "Kaynakta yok"}")

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                business.phone?.let { phone ->
                    OutlinedButton(
                        onClick = {
                            context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${Uri.encode(phone)}")))
                        },
                        modifier = Modifier.weight(1f),
                    ) { Text("Ara") }
                }
                business.website?.let { website ->
                    OutlinedButton(
                        onClick = {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(website)))
                        },
                        modifier = Modifier.weight(1f),
                    ) { Text("Web") }
                }
                business.menuUrl?.let { menu ->
                    OutlinedButton(
                        onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(menu))) },
                        modifier = Modifier.weight(1f),
                    ) { Text("Menü") }
                }
            }

            if (business.latitude != null && business.longitude != null) {
                OutlinedButton(
                    onClick = {
                        val label = Uri.encode(business.name)
                        val uri = Uri.parse("geo:${business.latitude},${business.longitude}?q=${business.latitude},${business.longitude}($label)")
                        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Haritada / navigasyonda aç") }
            }

            Text("Global Donuk — doğrulanmış değer önerisi", style = MaterialTheme.typography.titleMedium)
            Text(
                "Kaynak: " + opportunity.sourceUrl,
                style = MaterialTheme.typography.bodySmall,
            )
            opportunity.verifiedClaims.forEach { claim ->
                Text("• " + claim)
            }

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

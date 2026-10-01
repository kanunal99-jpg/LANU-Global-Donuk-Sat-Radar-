package com.lanu.globaldonuksatisradari

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
fun SalesAiScreen(context: SalesAiContext) {
    val scope = rememberCoroutineScope()
    val engine = remember { FreeSalesAiEngine() }
    var question by remember { mutableStateOf("") }
    var answer by remember { mutableStateOf("") }
    var mode by remember { mutableStateOf<FreeAiMode?>(null) }
    var availability by remember { mutableStateOf(FreeAiAvailability.UNKNOWN) }
    var loading by remember { mutableStateOf(false) }

    DisposableEffect(engine) {
        onDispose { engine.close() }
    }

    Column(
        modifier = Modifier
            .testTag("ai_screen")
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("LANU AI Satış Asistanı", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Öncelik cihaz üzerinde ücretsiz Gemini Nano. Destek yoksa aynı ekran ücretsiz yerel satış asistanıyla çalışır.",
            style = MaterialTheme.typography.bodyMedium,
        )

        Card(Modifier.fillMaxWidth()) {
            Column(
                Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Ücretsiz AI motoru", style = MaterialTheme.typography.titleMedium)
                Text(
                    availabilityLabel(availability),
                    modifier = Modifier.testTag("ai_provider_status"),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        modifier = Modifier.weight(1f),
                        enabled = !loading,
                        onClick = {
                            scope.launch {
                                loading = true
                                availability = engine.availability()
                                loading = false
                            }
                        },
                    ) {
                        Text("Cihaz AI kontrol")
                    }

                    if (availability == FreeAiAvailability.DOWNLOADABLE) {
                        OutlinedButton(
                            modifier = Modifier.weight(1f),
                            enabled = !loading,
                            onClick = {
                                scope.launch {
                                    loading = true
                                    availability = engine.prepareOnDeviceModel()
                                    loading = false
                                }
                            },
                        ) {
                            Text("Nano'yu hazırla")
                        }
                    }
                }
                Text(
                    "API anahtarı yok • Sunucu başına ücret yok • Yerel fallback her cihazda hazır",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        Text("Hızlı görevler", style = MaterialTheme.typography.titleMedium)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                modifier = Modifier.weight(1f),
                onClick = { question = "CRM ve Radar verisini özetle." },
            ) { Text("Özet") }
            OutlinedButton(
                modifier = Modifier.weight(1f),
                onClick = { question = "Bugün hangi işletmelere öncelik vermeliyim?" },
            ) { Text("Öncelik") }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                modifier = Modifier.weight(1f),
                onClick = { question = "Bugünkü ziyaret planımı hazırla." },
            ) { Text("Ziyaret planı") }
            OutlinedButton(
                modifier = Modifier.weight(1f),
                onClick = { question = "İlk temas için kısa WhatsApp mesajı hazırla." },
            ) { Text("Mesaj") }
        }

        OutlinedTextField(
            value = question,
            onValueChange = { question = it },
            modifier = Modifier.fillMaxWidth().testTag("ai_question"),
            label = { Text("AI'ya ne sormak istiyorsun?") },
            minLines = 3,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                modifier = Modifier.weight(1f).testTag("ai_ask"),
                enabled = !loading,
                onClick = {
                    scope.launch {
                        loading = true
                        val result = engine.answer(question, context)
                        answer = result.text
                        mode = result.mode
                        loading = false
                    }
                },
            ) {
                Text(if (loading) "Hazırlanıyor…" else "AI'ya Sor")
            }
            OutlinedButton(
                modifier = Modifier.weight(1f).testTag("ai_local_summary"),
                enabled = !loading,
                onClick = {
                    answer = LocalSalesAssistant.answer("Satış özetini çıkar.", context)
                    mode = FreeAiMode.LOCAL_FALLBACK
                },
            ) {
                Text("Yerel özet")
            }
        }

        if (answer.isNotBlank()) {
            Card(
                modifier = Modifier.fillMaxWidth().testTag("ai_answer"),
            ) {
                Column(
                    Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        when (mode) {
                            FreeAiMode.GEMINI_NANO -> "Gemini Nano • cihaz üzerinde"
                            else -> "Ücretsiz yerel mod"
                        },
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Text(answer)
                }
            }
        }
    }
}

private fun availabilityLabel(value: FreeAiAvailability): String = when (value) {
    FreeAiAvailability.UNKNOWN -> "Durum: henüz kontrol edilmedi • yerel mod hazır"
    FreeAiAvailability.AVAILABLE -> "Durum: Gemini Nano hazır • cihaz üzerinde AI aktif"
    FreeAiAvailability.DOWNLOADABLE -> "Durum: Gemini Nano bu cihazda destekleniyor ve indirilebilir"
    FreeAiAvailability.DOWNLOADING -> "Durum: Gemini Nano hazırlanıyor"
    FreeAiAvailability.UNAVAILABLE -> "Durum: Gemini Nano desteklenmiyor • ücretsiz yerel mod aktif"
}

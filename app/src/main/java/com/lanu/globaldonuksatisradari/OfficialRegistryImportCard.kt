package com.lanu.globaldonuksatisradari

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.lanu.globaldonuksatisradari.data.OfficialRegistrySource
import com.lanu.globaldonuksatisradari.data.OfficialRegistryStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun OfficialRegistryImportCard(
    onImported: (String) -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember(context) { OfficialRegistryStore(context) }
    var selectedSource by remember { mutableStateOf(OfficialRegistrySource.ITO) }
    var pendingSource by remember { mutableStateOf(OfficialRegistrySource.ITO) }
    var importing by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var counts by remember {
        mutableStateOf(OfficialRegistrySource.entries.associateWith(store::count))
    }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        importing = true
        status = "Resmî sicil dosyası kontrol ediliyor…"
        val source = pendingSource
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val fileName = displayName(context.contentResolver, uri)
                        ?: "resmi-sicil-verisi"
                    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: error("Dosya okunamadı.")
                    store.importDocument(
                        source = source,
                        fileName = fileName,
                        bytes = bytes,
                    )
                }
            }.onSuccess { summary ->
                counts = OfficialRegistrySource.entries.associateWith(store::count)
                val message = buildString {
                    append(summary.source.displayName())
                    append(": ")
                    append(summary.importedCount)
                    append(" kayıt içe aktarıldı")
                    if (summary.activeCount > 0 || summary.inactiveCount > 0) {
                        append(" • faal ")
                        append(summary.activeCount)
                        append(" • aktif değil ")
                        append(summary.inactiveCount)
                    }
                    append(". Radar aramalarında resmî telefon/adres önceliklendirilecek.")
                }
                status = message
                onImported(message)
            }.onFailure {
                status = "Resmî sicil dosyası içe aktarılamadı. CSV/TSV/TXT veya XLSX ve uygun sütun başlıklarını kontrol edin."
            }
            importing = false
        }
    }

    Card(Modifier.fillMaxWidth().testTag("official_registry_import_card")) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Resmî sicil doğrulaması", style = MaterialTheme.typography.titleMedium)
            Text(
                "İTO / MERSİS / ESBİS üzerinden resmî olarak temin ettiğiniz CSV veya XLSX çıktısını içe aktarın. " +
                    "Radar işletmeyi OSM ile bulur; eşleşen telefon, açık adres ve sicil durumu resmî kayıttan kullanılır.",
                style = MaterialTheme.typography.bodySmall,
            )

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                OfficialRegistrySource.entries.forEach { source ->
                    FilterChip(
                        selected = selectedSource == source,
                        onClick = { selectedSource = source },
                        label = { Text(source.shortLabel()) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            Text(
                "Kayıtlar: İTO \${counts[OfficialRegistrySource.ITO] ?: 0} • " +
                    "MERSİS \${counts[OfficialRegistrySource.MERSIS] ?: 0} • " +
                    "ESBİS \${counts[OfficialRegistrySource.ESBIS] ?: 0}",
                style = MaterialTheme.typography.bodySmall,
            )

            Button(
                onClick = {
                    pendingSource = selectedSource
                    picker.launch(
                        arrayOf(
                            "text/csv",
                            "text/tab-separated-values",
                            "text/plain",
                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                            "application/octet-stream",
                        ),
                    )
                },
                enabled = !importing,
                modifier = Modifier.fillMaxWidth().testTag("official_registry_import_button"),
            ) {
                Text(if (importing) "İçe aktarılıyor…" else "\${selectedSource.shortLabel()} dosyası içe aktar")
            }

            status?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            Text(
                "Not: Uygulama MERSİS/ESBİS oturumunu veya İTO sitesini otomatik kazımaz; yalnızca sizin resmî/izinli yoldan temin ettiğiniz çıktıyı kullanır.",
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

private fun OfficialRegistrySource.shortLabel(): String = when (this) {
    OfficialRegistrySource.ITO -> "İTO"
    OfficialRegistrySource.MERSIS -> "MERSİS"
    OfficialRegistrySource.ESBIS -> "ESBİS"
}

private fun OfficialRegistrySource.displayName(): String = when (this) {
    OfficialRegistrySource.ITO -> "İstanbul Ticaret Odası"
    OfficialRegistrySource.MERSIS -> "MERSİS"
    OfficialRegistrySource.ESBIS -> "ESBİS"
}

private fun displayName(
    resolver: android.content.ContentResolver,
    uri: Uri,
): String? {
    return resolver.query(
        uri,
        arrayOf(OpenableColumns.DISPLAY_NAME),
        null,
        null,
        null,
    )?.use { cursor ->
        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
    }
}

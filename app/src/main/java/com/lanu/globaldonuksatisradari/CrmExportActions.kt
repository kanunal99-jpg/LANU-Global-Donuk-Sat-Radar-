package com.lanu.globaldonuksatisradari

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
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
import androidx.core.content.FileProvider
import com.lanu.globaldonuksatisradari.crm.CrmCustomer
import com.lanu.globaldonuksatisradari.crm.CrmExcelExporter
import com.lanu.globaldonuksatisradari.crm.CrmLocationEnrichmentService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Composable
fun CrmExportActions(customers: List<CrmCustomer>) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pendingWorkbook by remember { mutableStateOf<ByteArray?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    var exporting by remember { mutableStateOf(false) }
    val locationEnrichment = remember(context) { CrmLocationEnrichmentService(context) }

    val fileName = remember(customers.size) {
        "LANU-CRM-Noktalari-" +
            LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmm")) +
            ".xlsx"
    }

    val saveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(CrmExcelExporter.MIME_TYPE),
    ) { uri ->
        val bytes = pendingWorkbook
        pendingWorkbook = null
        if (uri == null || bytes == null) return@rememberLauncherForActivityResult
        scope.launch(Dispatchers.IO) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                    ?: error("Dosya yazma akışı açılamadı.")
            }.onSuccess {
                withContext(Dispatchers.Main) { status = "Excel dosyası kaydedildi." }
            }.onFailure {
                withContext(Dispatchers.Main) { status = "Excel kaydedilemedi. Lütfen tekrar deneyin." }
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                modifier = Modifier.weight(1f).testTag("crm_excel_save"),
                enabled = customers.isNotEmpty() && !exporting,
                onClick = {
                    scope.launch {
                        exporting = true
                        status = "Konum bilgileri doğrulanıyor ve Excel hazırlanıyor…"
                        runCatching {
                            val enriched = locationEnrichment.enrich(customers)
                            withContext(Dispatchers.Default) { CrmExcelExporter.build(enriched) }
                        }.onSuccess { bytes ->
                            pendingWorkbook = bytes
                            status = "Excel hazır. Kaydedilecek konumu seçin."
                            saveLauncher.launch(fileName)
                        }.onFailure {
                            status = "Excel hazırlanamadı. Lütfen bağlantınızı kontrol edip tekrar deneyin."
                        }
                        exporting = false
                    }
                },
            ) { Text(if (exporting) "Hazırlanıyor…" else "Excel indir / kaydet") }

            OutlinedButton(
                modifier = Modifier.weight(1f).testTag("crm_excel_share"),
                enabled = customers.isNotEmpty() && !exporting,
                onClick = {
                    scope.launch {
                        exporting = true
                        status = "Konum bilgileri doğrulanıyor ve paylaşım dosyası hazırlanıyor…"
                        runCatching {
                            val enriched = locationEnrichment.enrich(customers)
                            val bytes = withContext(Dispatchers.Default) { CrmExcelExporter.build(enriched) }
                            val file = withContext(Dispatchers.IO) {
                                val exportDir = File(context.cacheDir, "crm_exports").apply { mkdirs() }
                                File(exportDir, fileName).apply { writeBytes(bytes) }
                            }
                            val uri = FileProvider.getUriForFile(
                                context,
                                context.packageName + ".fileprovider",
                                file,
                            )
                            Intent(Intent.ACTION_SEND).apply {
                                type = CrmExcelExporter.MIME_TYPE
                                putExtra(Intent.EXTRA_STREAM, uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                        }.onSuccess { intent ->
                            context.startActivity(Intent.createChooser(intent, "CRM Excel dosyasını paylaş"))
                            status = "Excel paylaşım için hazır."
                        }.onFailure {
                            status = "Excel paylaşımı hazırlanamadı. Lütfen tekrar deneyin."
                        }
                        exporting = false
                    }
                },
            ) { Text("Excel paylaş") }
        }
        status?.let { Text(it) }
    }
}

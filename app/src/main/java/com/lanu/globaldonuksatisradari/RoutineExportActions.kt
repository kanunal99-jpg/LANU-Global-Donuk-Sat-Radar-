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
import com.lanu.globaldonuksatisradari.crm.CrmLocationEnrichmentService
import com.lanu.globaldonuksatisradari.crm.MonthlyRoutinePlan
import com.lanu.globaldonuksatisradari.crm.RoutineExcelExporter
import com.lanu.globaldonuksatisradari.data.OfficialRegistryStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Composable
fun RoutineExportActions(
    plan: MonthlyRoutinePlan,
    planLabel: String = "Otomatik",
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pendingWorkbook by remember { mutableStateOf<ByteArray?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    var exporting by remember { mutableStateOf(false) }
    val locationEnrichment = remember(context) { CrmLocationEnrichmentService(context) }
    val officialRegistryStore = remember(context) { OfficialRegistryStore(context) }

    val fileName = remember(plan.totalPointCount, planLabel) {
        "LANU-Aylik-Rutin-" +
            planLabel.filter { it.isLetterOrDigit() || it == '-' }.ifBlank { "Plan" } +
            "-" +
            LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmm")) +
            ".xlsx"
    }

    val saveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(RoutineExcelExporter.MIME_TYPE),
    ) { uri ->
        val bytes = pendingWorkbook
        pendingWorkbook = null
        if (uri == null || bytes == null) return@rememberLauncherForActivityResult
        scope.launch(Dispatchers.IO) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                    ?: error("Dosya yazma akışı açılamadı.")
            }.onSuccess {
                withContext(Dispatchers.Main) {
                    status = "Aylık rutin Excel dosyası kaydedildi."
                }
            }.onFailure {
                withContext(Dispatchers.Main) {
                    status = "Aylık rutin Excel dosyası kaydedilemedi."
                }
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                modifier = Modifier
                    .weight(1f)
                    .testTag("routine_excel_save"),
                enabled = plan.totalPointCount > 0 && !exporting,
                onClick = {
                    scope.launch {
                        exporting = true
                        status = planLabel + " rutin verileri konum ve resmî sicille zenginleştiriliyor…"
                        runCatching {
                            val enrichedPlan = enrichRoutinePlanForExport(
                                plan = plan,
                                locationEnrichment = locationEnrichment,
                            )
                            val officialRecords = withContext(Dispatchers.IO) {
                                enrichedPlan.days
                                    .flatMap { it.stops }
                                    .map { it.customer.city.trim() }
                                    .filter(String::isNotBlank)
                                    .distinct()
                                    .flatMap { city -> officialRegistryStore.recordsFor(city, null) }
                            }
                            withContext(Dispatchers.Default) {
                                RoutineExcelExporter.build(enrichedPlan, officialRecords)
                            }
                        }.onSuccess { bytes ->
                            pendingWorkbook = bytes
                            status = "Excel hazır. Kaydedilecek konumu seçin."
                            saveLauncher.launch(fileName)
                        }.onFailure {
                            status = "Rutin Excel hazırlanamadı. Lütfen tekrar deneyin."
                        }
                        exporting = false
                    }
                },
            ) {
                Text(if (exporting) "Hazırlanıyor…" else "Excel indir / kaydet")
            }

            OutlinedButton(
                modifier = Modifier
                    .weight(1f)
                    .testTag("routine_excel_share"),
                enabled = plan.totalPointCount > 0 && !exporting,
                onClick = {
                    scope.launch {
                        exporting = true
                        status = "Rutin Excel paylaşım dosyası hazırlanıyor…"
                        runCatching {
                            val enrichedPlan = enrichRoutinePlanForExport(
                                plan = plan,
                                locationEnrichment = locationEnrichment,
                            )
                            val officialRecords = withContext(Dispatchers.IO) {
                                enrichedPlan.days
                                    .flatMap { it.stops }
                                    .map { it.customer.city.trim() }
                                    .filter(String::isNotBlank)
                                    .distinct()
                                    .flatMap { city -> officialRegistryStore.recordsFor(city, null) }
                            }
                            val bytes = withContext(Dispatchers.Default) {
                                RoutineExcelExporter.build(enrichedPlan, officialRecords)
                            }
                            val file = withContext(Dispatchers.IO) {
                                val exportDir = File(context.cacheDir, "routine_exports").apply { mkdirs() }
                                File(exportDir, fileName).apply { writeBytes(bytes) }
                            }
                            val uri = FileProvider.getUriForFile(
                                context,
                                context.packageName + ".fileprovider",
                                file,
                            )
                            Intent(Intent.ACTION_SEND).apply {
                                type = RoutineExcelExporter.MIME_TYPE
                                putExtra(Intent.EXTRA_STREAM, uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                        }.onSuccess { intent ->
                            context.startActivity(
                                Intent.createChooser(intent, planLabel + " rutin Excel dosyasını paylaş"),
                            )
                            status = "Aylık rutin Excel paylaşım için hazır."
                        }.onFailure {
                            status = "Rutin Excel paylaşımı hazırlanamadı."
                        }
                        exporting = false
                    }
                },
            ) {
                Text("Excel paylaş")
            }
        }
        status?.let { Text(it) }
    }
}
private suspend fun enrichRoutinePlanForExport(
    plan: MonthlyRoutinePlan,
    locationEnrichment: CrmLocationEnrichmentService,
): MonthlyRoutinePlan {
    val uniqueCustomers = plan.days
        .flatMap { it.stops }
        .map { it.customer }
        .distinctBy { it.id }
    val enrichedById = locationEnrichment.enrich(uniqueCustomers).associateBy { it.id }
    return plan.copy(
        days = plan.days.map { day ->
            day.copy(
                stops = day.stops.map { stop ->
                    stop.copy(customer = enrichedById[stop.customer.id] ?: stop.customer)
                },
            )
        },
    )
}


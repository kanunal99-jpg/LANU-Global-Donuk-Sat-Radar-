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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.lanu.globaldonuksatisradari.crm.CrmCustomer
import com.lanu.globaldonuksatisradari.data.OfficialRegistryImportSummary
import com.lanu.globaldonuksatisradari.data.OfficialRegistryRecord
import com.lanu.globaldonuksatisradari.data.OfficialRegistrySource
import com.lanu.globaldonuksatisradari.data.OfficialRegistryStatus
import com.lanu.globaldonuksatisradari.data.OfficialRegistryStore
import com.lanu.globaldonuksatisradari.data.OfficialRegistryTrust
import com.lanu.globaldonuksatisradari.data.VerifiedBusiness
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun OfficialRegistryImportCard(
    defaultCity: String? = null,
    contextCustomers: List<CrmCustomer> = emptyList(),
    contextBusinesses: List<VerifiedBusiness> = emptyList(),
    onImported: (OfficialRegistryImportSummary, List<OfficialRegistryRecord>) -> Unit = { _, _ -> },
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember(context) { OfficialRegistryStore(context) }
    var selectedSource by remember { mutableStateOf(OfficialRegistrySource.ITO) }
    var pendingSource by remember { mutableStateOf(OfficialRegistrySource.ITO) }
    var importing by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var showRecords by remember { mutableStateOf(false) }
    var recordQuery by remember { mutableStateOf("") }
    var phonePresenceFilter by remember { mutableStateOf("Tümü") }
    var registryRecords by remember { mutableStateOf<List<OfficialRegistryRecord>>(emptyList()) }
    var loadingRecords by remember { mutableStateOf(false) }
    var counts by remember {
        mutableStateOf<Map<OfficialRegistrySource, Int>>(emptyMap())
    }

    LaunchedEffect(store) {
        counts = withContext(Dispatchers.IO) {
            OfficialRegistrySource.entries.associateWith(store::count)
        }
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
                        defaultCity = if (
                            source == OfficialRegistrySource.CHAMBER ||
                            source == OfficialRegistrySource.TTSG
                        ) defaultCity else null,
                    )
                }
            }.onSuccess { summary ->
                counts = withContext(Dispatchers.IO) {
                    OfficialRegistrySource.entries.associateWith(store::count)
                }
                val records = withContext(Dispatchers.IO) {
                    if (!defaultCity.isNullOrBlank()) {
                        store.recordsFor(summary.source, defaultCity, null)
                    } else {
                        store.records(summary.source)
                    }
                }
                registryRecords = records
                showRecords = true
                recordQuery = ""
                phonePresenceFilter = "Tümü"
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
                    append(" • sicil kimliği doğrulanan ")
                    append(summary.verifiedIdentityCount)
                    append(" • telefon bulunan ")
                    append(summary.phoneCount)
                    append(" • VKN/TCKN bulunan ")
                    append(summary.taxOrNationalIdCount)
                    append(". ")
                    if (summary.verifiedIdentityCount > 0) {
                        append("Sicil kimliği doğrulanan eşleşmeler CRM'i güvenli şekilde zenginleştirebilir.")
                    } else {
                        append("Sicil/kayıt numarası bulunmadığı için bu dosya resmî kimlik kanıtı olarak kullanılmayacak.")
                    }
                }
                status = message
                onImported(summary, records)
            }.onFailure { error ->
                status = "Resmî sicil dosyası içe aktarılamadı: " +
                    (error.message ?: "CSV/TSV/TXT veya XLSX sütunlarını kontrol edin.")
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
                "İTO, diğer Ticaret/Ticaret ve Sanayi Odaları, TOBB, MERSİS, ESBİS veya TTSG üzerinden resmî olarak " +
                    "yetkili kanaldan temin ettiğiniz CSV/XLSX çıktısını içe aktarın. Aynı kaynak/il için birden fazla parça dosya güvenli biçimde birleştirilir; Telefon/GSM ile VKN/TCKN alanları kaynakta varsa korunur. TTSG kayıtları ilan/olay kanıtıdır; tek ilan güncel aktiflik kanıtı sayılmaz.",
                style = MaterialTheme.typography.bodySmall,
            )

            OfficialRegistrySource.entries.chunked(3).forEach { rowSources ->
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    rowSources.forEach { source ->
                        FilterChip(
                            selected = selectedSource == source,
                            onClick = {
                                selectedSource = source
                                showRecords = false
                                recordQuery = ""
                                phonePresenceFilter = "Tümü"
                            },
                            label = { Text(source.shortLabel()) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    repeat(3 - rowSources.size) {
                        androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                    }
                }
            }

            Text(
                selectedSource.acquisitionGuidance,
                style = MaterialTheme.typography.bodySmall,
            )

            Text(
                "Kayıtlar: " + OfficialRegistrySource.entries.joinToString(" • ") { source ->
                    "${source.shortLabel()} ${counts[source] ?: 0}"
                },
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
                Text(if (importing) "İçe aktarılıyor…" else "${selectedSource.shortLabel()} dosyası içe aktar")
            }

            OutlinedButton(
                onClick = {
                    if (showRecords && registryRecords.firstOrNull()?.source == selectedSource) {
                        showRecords = false
                    } else {
                        loadingRecords = true
                        scope.launch {
                            registryRecords = withContext(Dispatchers.IO) {
                                if (!defaultCity.isNullOrBlank()) {
                                    store.recordsFor(selectedSource, defaultCity, null)
                                } else {
                                    store.records(selectedSource)
                                }
                            }
                            recordQuery = ""
                            phonePresenceFilter = "Tümü"
                            showRecords = true
                            loadingRecords = false
                        }
                    }
                },
                enabled = !loadingRecords,
                modifier = Modifier.fillMaxWidth().testTag("official_registry_view_button"),
            ) {
                Text(
                    if (loadingRecords) "Kayıtlar yükleniyor…"
                    else if (showRecords && registryRecords.firstOrNull()?.source == selectedSource) "Kayıtları gizle"
                    else "${selectedSource.shortLabel()} kayıtlarını görüntüle",
                )
            }

            if (showRecords) {
                val normalizedQuery = recordQuery.trim().lowercase()
                val searchedRecords = if (normalizedQuery.isBlank()) {
                    registryRecords
                } else {
                    registryRecords.filter { record ->
                        listOf(
                            record.businessName,
                            record.registrationNumber.orEmpty(),
                            record.mersisNumber.orEmpty(),
                            record.registryOffice.orEmpty(),
                            record.registryEvent.orEmpty(),
                            record.publicationDate.orEmpty(),
                            record.registrationDate.orEmpty(),
                            record.address.orEmpty(),
                            record.phone.orEmpty(),
                            record.city.orEmpty(),
                            record.district.orEmpty(),
                            record.neighborhood.orEmpty(),
                            record.naceCode.orEmpty(),
                            record.taxOrNationalId.orEmpty(),
                        ).any { it.lowercase().contains(normalizedQuery) }
                    }
                }
                val filteredRecords = searchedRecords.filter { record ->
                    when (phonePresenceFilter) {
                        "Var" -> !record.phone.isNullOrBlank()
                        "Eksik" -> record.phone.isNullOrBlank()
                        else -> true
                    }
                }
                val phoneCount = registryRecords.count { !it.phone.isNullOrBlank() }
                val verifiedCount = registryRecords.count(OfficialRegistryTrust::isIdentityVerified)
                val phonePercent = if (registryRecords.isEmpty()) 0 else phoneCount * 100 / registryRecords.size
                OutlinedTextField(
                    value = recordQuery,
                    onValueChange = { recordQuery = it },
                    modifier = Modifier.fillMaxWidth().testTag("official_registry_search"),
                    label = { Text("Kayıtlarda ad, adres, telefon, sicil/MERSİS, olay, VKN/TCKN veya NACE ara") },
                    singleLine = true,
                )
                Text(
                    "Telefon kapsamı: $phoneCount / ${registryRecords.size} (%$phonePercent) • " +
                        "Sicil kimliği doğrulanan: $verifiedCount / ${registryRecords.size}",
                    style = MaterialTheme.typography.bodySmall,
                )
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    listOf("Tümü", "Var", "Eksik").forEach { option ->
                        FilterChip(
                            selected = phonePresenceFilter == option,
                            onClick = { phonePresenceFilter = option },
                            label = { Text(if (option == "Tümü") "Telefon: Tümü" else "Telefon: $option") },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                if (registryRecords.isNotEmpty() && verifiedCount == 0) {
                    Card(Modifier.fillMaxWidth().testTag("registry_unverified_warning")) {
                        Text(
                            "Uyarı: Bu dosyada sicil/kayıt numarası yok. Kaynak düğmesinde ${selectedSource.shortLabel()} seçilmiş olsa da " +
                                "dosyanın resmî kimliği doğrulanamıyor. Kayıtlar görüntülenir ve Excel'e aktarılır; " +
                                "ancak CRM'de resmî veri olarak mevcut bilgilerin üzerine yazılmaz.",
                            Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                Text(
                    (defaultCity?.takeIf(String::isNotBlank)?.let { "$it • " } ?: "") +
                        "${filteredRecords.size} eşleşme • ilk ${minOf(filteredRecords.size, 30)} kayıt gösteriliyor",
                    style = MaterialTheme.typography.bodySmall,
                )
                OfficialRegistryExportActions(
                    records = filteredRecords,
                    source = selectedSource,
                    customers = contextCustomers,
                    businesses = contextBusinesses,
                )
                Text(
                    "Excel dışa aktarımı ekranda gösterilen ilk 30 kayıtla sınırlı değildir; filtreye uyan ${filteredRecords.size} kaydın tamamını içerir.",
                    style = MaterialTheme.typography.labelSmall,
                )
                filteredRecords.take(30).forEach { record ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(
                            Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(3.dp),
                        ) {
                            Text(record.businessName, style = MaterialTheme.typography.titleSmall)
                            val location = listOfNotNull(
                                record.neighborhood,
                                record.district,
                                record.city,
                            ).filter(String::isNotBlank).joinToString(" • ")
                            if (location.isNotBlank()) Text(location, style = MaterialTheme.typography.bodySmall)
                            record.address?.takeIf(String::isNotBlank)?.let {
                                Text("Açık adres: $it", style = MaterialTheme.typography.bodySmall)
                            }
                            record.phone?.takeIf(String::isNotBlank)?.let {
                                Text("Telefon: $it", style = MaterialTheme.typography.bodySmall)
                            } ?: Text("Telefon: kaynakta yok", style = MaterialTheme.typography.bodySmall)
                            record.registrationNumber?.takeIf(String::isNotBlank)?.let {
                                Text("Sicil: $it", style = MaterialTheme.typography.labelSmall)
                            }
                            record.mersisNumber?.takeIf(String::isNotBlank)?.let {
                                Text("MERSİS: $it", style = MaterialTheme.typography.labelSmall)
                            }
                            record.registryOffice?.takeIf(String::isNotBlank)?.let {
                                Text("Sicil müdürlüğü: $it", style = MaterialTheme.typography.labelSmall)
                            }
                            record.registryEvent?.takeIf(String::isNotBlank)?.let {
                                Text("Sicil olayı: $it", style = MaterialTheme.typography.labelSmall)
                            }
                            record.publicationDate?.takeIf(String::isNotBlank)?.let {
                                Text("Yayın tarihi: $it", style = MaterialTheme.typography.labelSmall)
                            }
                            record.registrationDate?.takeIf(String::isNotBlank)?.let {
                                Text("Tescil tarihi: $it", style = MaterialTheme.typography.labelSmall)
                            }
                            if (!record.gazetteNumber.isNullOrBlank() || !record.gazettePage.isNullOrBlank()) {
                                Text(
                                    "Gazete: ${record.gazetteNumber.orEmpty()} / sayfa ${record.gazettePage.orEmpty()}",
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                            record.taxOrNationalId?.takeIf(String::isNotBlank)?.let {
                                Text("TC/Vergi No: $it", style = MaterialTheme.typography.labelSmall)
                            }
                            record.naceCode?.takeIf(String::isNotBlank)?.let {
                                Text("NACE: $it", style = MaterialTheme.typography.labelSmall)
                            }
                            Text(
                                if (OfficialRegistryTrust.isIdentityVerified(record)) {
                                    "Resmî kimlik: doğrulandı"
                                } else {
                                    "Resmî kimlik: sicil/kayıt veya MERSİS no yok"
                                },
                                style = MaterialTheme.typography.labelSmall,
                            )
                            val stateLabel = when {
                                record.status?.let(OfficialRegistryStatus::isActive) == true -> "FAAL"
                                record.status?.let(OfficialRegistryStatus::isInactive) == true -> "AKTİF DEĞİL"
                                else -> record.status ?: "Durum belirtilmemiş"
                            }
                            Text(stateLabel, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }

            status?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            Text(
                buildString {
                    append("Not: ODA/TOBB/TTSG dahil kaynak seçimi dosyanın nereden alındığını beyan eder; resmî kimlik için sicil/kayıt veya MERSİS numarası aranır. ")
                    append("Giriş/CAPTCHA/yetki gerektiren TOBB, MERSİS, ESBİS, TTSG ve oda sistemleri otomatik kazınmaz; yalnız kullanıcının yetkili/resmî çıktısı içe aktarılır. TTSG ilanı, olayın kanıtıdır; şirketin bugünkü durumunu tek başına FAAL/PASİF yapmaz. Büyük çıktılar güvenli şekilde parçalara bölünerek art arda içe aktarılabilir.")
                    if (!defaultCity.isNullOrBlank()) {
                        append(" ODA dosyasında İl sütunu yoksa seçili şehir ($defaultCity) kullanılır.")
                    }
                },
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

private fun OfficialRegistrySource.shortLabel(): String = when (this) {
    OfficialRegistrySource.ITO -> "İTO"
    OfficialRegistrySource.CHAMBER -> "ODA"
    OfficialRegistrySource.TOBB -> "TOBB"
    OfficialRegistrySource.MERSIS -> "MERSİS"
    OfficialRegistrySource.ESBIS -> "ESBİS"
    OfficialRegistrySource.TTSG -> "TTSG"
}

private fun OfficialRegistrySource.displayName(): String = when (this) {
    OfficialRegistrySource.ITO -> "İstanbul Ticaret Odası"
    OfficialRegistrySource.CHAMBER -> "Yerel Ticaret / Ticaret ve Sanayi Odası"
    OfficialRegistrySource.TOBB -> "TOBB"
    OfficialRegistrySource.MERSIS -> "MERSİS"
    OfficialRegistrySource.ESBIS -> "ESBİS"
    OfficialRegistrySource.TTSG -> "Türkiye Ticaret Sicili Gazetesi"
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

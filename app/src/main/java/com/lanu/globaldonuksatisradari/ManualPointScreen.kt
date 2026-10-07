package com.lanu.globaldonuksatisradari

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lanu.globaldonuksatisradari.crm.LocalCrmRepository
import kotlinx.coroutines.launch

@Composable
fun ManualPointScreen(
    repository: LocalCrmRepository,
    defaultCity: String,
    defaultDistrict: String? = null,
    ownerUserId: String? = null,
    onSaved: () -> Unit,
) {
    val initialCity = remember(defaultCity) {
        TurkeyCityCatalog.ALL.firstOrNull { it.name.equals(defaultCity, ignoreCase = true) }
            ?: TurkeyCityCatalog.ALL.first()
    }

    var name by remember { mutableStateOf("") }
    var signboardName by remember { mutableStateOf("") }
    var contactName by remember { mutableStateOf("") }
    var businessType by remember { mutableStateOf("") }
    var taxOrNationalId by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var selectedCity by remember(defaultCity) { mutableStateOf(initialCity) }
    var district by remember(defaultCity, defaultDistrict) {
        mutableStateOf(
            defaultDistrict?.takeIf { wanted ->
                initialCity.fallbackDistricts.any { it.equals(wanted, ignoreCase = true) }
            }.orEmpty(),
        )
    }
    var neighborhood by remember { mutableStateOf("") }
    var longitudeX by remember { mutableStateOf("") }
    var latitudeY by remember { mutableStateOf("") }
    var cityMenu by remember { mutableStateOf(false) }
    var districtMenu by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .testTag("manual_point_screen")
            .padding(horizontal = 16.dp)
            .imePadding()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(
            modifier = Modifier.padding(top = 16.dp, bottom = 2.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("Manuel Nokta", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Yeni müşteri veya işletmeyi doğrulanmış il/ilçe kapsamıyla CRM'e ekleyin.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        FormSectionCard(
            title = "İşletme",
            subtitle = "Noktayı tanımlayan temel bilgiler",
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                modifier = Modifier.fillMaxWidth().testTag("manual_point_name"),
                label = { Text("Nokta / işletme adı *") },
                singleLine = true,
            )
            OutlinedTextField(
                value = signboardName,
                onValueChange = { signboardName = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Tabela adı") },
                singleLine = true,
            )
            OutlinedTextField(
                value = businessType,
                onValueChange = { businessType = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("İşletme türü") },
                singleLine = true,
            )
        }

        FormSectionCard(
            title = "İletişim",
            subtitle = "Yetkili kişi ve erişim bilgileri",
        ) {
            OutlinedTextField(
                value = contactName,
                onValueChange = { contactName = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Ad Soyad") },
                singleLine = true,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                OutlinedTextField(
                    value = taxOrNationalId,
                    onValueChange = { taxOrNationalId = it.filter(Char::isDigit).take(11) },
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    label = { Text("TC / Vergi No") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    label = { Text("Telefon") },
                    singleLine = true,
                )
            }
        }

        FormSectionCard(
            title = "Adres ve konum",
            subtitle = "İl ve ilçe kanonik Türkiye kataloğundan seçilir",
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Box(Modifier.weight(1f)) {
                    OutlinedButton(
                        onClick = { cityMenu = true },
                        modifier = Modifier.fillMaxWidth().testTag("manual_city_filter"),
                    ) {
                        Text(
                            selectedCity.name,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    DropdownMenu(
                        expanded = cityMenu,
                        onDismissRequest = { cityMenu = false },
                    ) {
                        TurkeyCityCatalog.ALL.forEach { city ->
                            DropdownMenuItem(
                                text = { Text(city.name) },
                                onClick = {
                                    selectedCity = city
                                    district = ""
                                    neighborhood = ""
                                    message = null
                                    districtMenu = false
                                    cityMenu = false
                                },
                            )
                        }
                    }
                }
                Box(Modifier.weight(1f)) {
                    OutlinedButton(
                        onClick = { districtMenu = true },
                        enabled = selectedCity.fallbackDistricts.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth().testTag("manual_district_filter"),
                    ) {
                        Text(
                            district.ifBlank { "İlçe seç *" },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    DropdownMenu(
                        expanded = districtMenu,
                        onDismissRequest = { districtMenu = false },
                    ) {
                        selectedCity.fallbackDistricts.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option) },
                                onClick = {
                                    district = option
                                    neighborhood = ""
                                    message = null
                                    districtMenu = false
                                },
                            )
                        }
                    }
                }
            }

            OutlinedTextField(
                value = neighborhood,
                onValueChange = { neighborhood = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Mahalle") },
                singleLine = true,
            )
            OutlinedTextField(
                value = address,
                onValueChange = { address = it },
                modifier = Modifier.fillMaxWidth().testTag("manual_point_address"),
                label = { Text("Açık adres *") },
                minLines = 2,
            )
            Text(
                "Koordinat: X = boylam (-180…180), Y = enlem (-90…90)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                OutlinedTextField(
                    value = longitudeX,
                    onValueChange = { longitudeX = it },
                    modifier = Modifier.weight(1f).testTag("manual_longitude"),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    label = { Text("X / Boylam *") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = latitudeY,
                    onValueChange = { latitudeY = it },
                    modifier = Modifier.weight(1f).testTag("manual_latitude"),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    label = { Text("Y / Enlem *") },
                    singleLine = true,
                )
            }
        }

        Button(
            enabled = !saving,
            onClick = {
                val lon = longitudeX.replace(',', '.').toDoubleOrNull()
                val lat = latitudeY.replace(',', '.').toDoubleOrNull()
                if (name.isBlank() || address.isBlank() || district.isBlank()) {
                    message = "İşletme adı, açık adres ve ilçe zorunludur."
                    return@Button
                }
                if (district !in selectedCity.fallbackDistricts) {
                    message = "İlçe seçilen ile ait doğrulanmış listeden seçilmelidir."
                    return@Button
                }
                if (lon == null || lat == null || lon !in -180.0..180.0 || lat !in -90.0..90.0) {
                    message = "Koordinatlar geçersiz. X=boylam, Y=enlem olmalıdır."
                    return@Button
                }
                saving = true
                message = null
                scope.launch {
                    runCatching {
                        repository.addManualCustomerPoint(
                            businessName = name.trim(),
                            address = address.trim(),
                            city = selectedCity.name,
                            district = district,
                            neighborhood = neighborhood.trim(),
                            latitude = lat,
                            longitude = lon,
                            contactName = contactName.trim(),
                            signboardName = signboardName.trim(),
                            businessType = businessType.trim(),
                            taxOrNationalId = taxOrNationalId.trim(),
                            phone = phone.trim(),
                            ownerUserId = ownerUserId,
                        )
                    }.onSuccess {
                        message = "Manuel nokta kaydedildi ve rutin havuzuna eklendi."
                        saving = false
                        onSaved()
                    }.onFailure {
                        Log.e("LanuManualPoint", "Manuel müşteri noktası kaydedilemedi.", it)
                        message = "Kayıt sırasında hata oluştu. Lütfen tekrar deneyin."
                        saving = false
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().testTag("manual_point_save"),
        ) {
            Text(if (saving) "Kaydediliyor…" else "Noktayı CRM'e kaydet")
        }

        message?.let {
            Text(
                it,
                modifier = Modifier.testTag("manual_point_message"),
                style = MaterialTheme.typography.bodyMedium,
                color = if (it.startsWith("Manuel nokta kaydedildi")) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                },
            )
        }
        androidx.compose.foundation.layout.Spacer(Modifier.padding(bottom = 4.dp))
    }
}

@Composable
private fun FormSectionCard(
    title: String,
    subtitle: String,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            content()
        }
    }
}

package com.lanu.globaldonuksatisradari

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.lanu.globaldonuksatisradari.crm.LocalCrmRepository
import kotlinx.coroutines.launch

@Composable
fun ManualPointScreen(
    repository: LocalCrmRepository,
    defaultCity: String,
    ownerUserId: String? = null,
    onSaved: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var contactName by remember { mutableStateOf("") }
    var businessType by remember { mutableStateOf("") }
    var taxOrNationalId by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var city by remember(defaultCity) { mutableStateOf(defaultCity) }
    var district by remember { mutableStateOf("") }
    var neighborhood by remember { mutableStateOf("") }
    var longitudeX by remember { mutableStateOf("") }
    var latitudeY by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .padding(16.dp)
            .imePadding()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Manuel Nokta Kaydı", style = MaterialTheme.typography.headlineSmall)
        Text("Adres ve koordinatlarını bildiğiniz müşteri/noktayı doğrudan CRM havuzuna ekleyin.")
        OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("Nokta / işletme adı") }, singleLine = true)
        OutlinedTextField(contactName, { contactName = it }, Modifier.fillMaxWidth(), label = { Text("Ad Soyad (opsiyonel)") }, singleLine = true)
        OutlinedTextField(businessType, { businessType = it }, Modifier.fillMaxWidth(), label = { Text("İşletme türü (opsiyonel)") }, singleLine = true)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                taxOrNationalId,
                { taxOrNationalId = it },
                Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                label = { Text("TC / Vergi No") },
                singleLine = true,
            )
            OutlinedTextField(
                phone,
                { phone = it },
                Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                label = { Text("Telefon No") },
                singleLine = true,
            )
        }
        OutlinedTextField(address, { address = it }, Modifier.fillMaxWidth(), label = { Text("Açık adres") }, minLines = 2)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(city, { city = it }, Modifier.weight(1f), label = { Text("İl") }, singleLine = true)
            OutlinedTextField(district, { district = it }, Modifier.weight(1f), label = { Text("İlçe") }, singleLine = true)
        }
        OutlinedTextField(neighborhood, { neighborhood = it }, Modifier.fillMaxWidth(), label = { Text("Mahalle") }, singleLine = true)
        Text("Koordinat sistemi: X = Boylam (-180..180), Y = Enlem (-90..90)")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                longitudeX,
                { longitudeX = it },
                Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                label = { Text("X / Boylam") },
                singleLine = true,
            )
            OutlinedTextField(
                latitudeY,
                { latitudeY = it },
                Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                label = { Text("Y / Enlem") },
                singleLine = true,
            )
        }
        Button(
            enabled = !saving,
            onClick = {
                val lon = longitudeX.replace(',', '.').toDoubleOrNull()
                val lat = latitudeY.replace(',', '.').toDoubleOrNull()
                if (name.isBlank() || address.isBlank() || city.isBlank() || district.isBlank()) {
                    message = "Ad, adres, il ve ilçe zorunludur."
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
                            businessName = name,
                            address = address,
                            city = city,
                            district = district,
                            neighborhood = neighborhood,
                            latitude = lat,
                            longitude = lon,
                            contactName = contactName,
                            businessType = businessType,
                            taxOrNationalId = taxOrNationalId,
                            phone = phone,
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
            Text(if (saving) "Kaydediliyor…" else "Noktayı kaydet")
        }
        message?.let {
            Text(
                it,
                modifier = Modifier.testTag("manual_point_message"),
                style = MaterialTheme.typography.bodyMedium,
                color = if (it.startsWith("Manuel nokta kaydedildi")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            )
        }
    }
}

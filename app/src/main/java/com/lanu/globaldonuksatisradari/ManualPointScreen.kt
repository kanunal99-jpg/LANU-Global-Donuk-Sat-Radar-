package com.lanu.globaldonuksatisradari

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lanu.globaldonuksatisradari.crm.LocalCrmRepository
import kotlinx.coroutines.launch

@Composable
fun ManualPointScreen(
    repository: LocalCrmRepository,
    defaultCity: String,
    onSaved: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
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
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Manuel Nokta Kaydı", style = MaterialTheme.typography.headlineSmall)
        Text("Adres ve koordinatlarını bildiğiniz müşteri/noktayı doğrudan CRM havuzuna ekleyin.")
        OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("Müşteri / işletme adı") }, singleLine = true)
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
                label = { Text("X / Boylam") },
                singleLine = true,
            )
            OutlinedTextField(
                latitudeY,
                { latitudeY = it },
                Modifier.weight(1f),
                label = { Text("Y / Enlem") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
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
                        )
                    }.onSuccess {
                        message = "Manuel nokta kaydedildi ve rutin havuzuna eklendi."
                        saving = false
                        onSaved()
                    }.onFailure {
                        message = it.message ?: "Kayıt sırasında hata oluştu."
                        saving = false
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (saving) "Kaydediliyor…" else "Noktayı kaydet")
        }
        message?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
    }
}

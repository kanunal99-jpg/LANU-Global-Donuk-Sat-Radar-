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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.lanu.globaldonuksatisradari.crm.CrmContact
import com.lanu.globaldonuksatisradari.crm.CrmContactValidator

@Composable
fun CrmContactsCard(
    customerId: String,
    contacts: List<CrmContact>,
    onCreate: (String, String?, String?, String?, Boolean) -> Unit,
    onUpdate: (String, String, String?, String?, String?) -> Unit,
    onMakePrimary: (String) -> Unit,
) {
    var editingId by remember(customerId) { mutableStateOf<String?>(null) }
    var fullName by remember(customerId) { mutableStateOf("") }
    var role by remember(customerId) { mutableStateOf("") }
    var phone by remember(customerId) { mutableStateOf("") }
    var email by remember(customerId) { mutableStateOf("") }
    var makePrimary by remember(customerId) { mutableStateOf(contacts.isEmpty()) }
    var formError by remember(customerId) { mutableStateOf<String?>(null) }

    fun clearForm() {
        editingId = null
        fullName = ""
        role = ""
        phone = ""
        email = ""
        makePrimary = contacts.isEmpty()
        formError = null
    }

    Card(Modifier.fillMaxWidth().testTag("crm_contacts_card")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Yetkili kişiler", style = MaterialTheme.typography.titleMedium)
            Text(
                "Müşteri karar vericilerini tek yerde yönetin. Birincil kişi arama ve teklif iletişiminde varsayılan yetkilidir.",
                style = MaterialTheme.typography.bodySmall,
            )

            if (contacts.isEmpty()) {
                Text("Henüz yetkili kişi eklenmedi.", style = MaterialTheme.typography.bodySmall)
            } else {
                contacts.forEach { contact ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(contact.fullName, style = MaterialTheme.typography.titleSmall)
                                if (contact.isPrimary) Text("Birincil", color = MaterialTheme.colorScheme.primary)
                            }
                            contact.role?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                            contact.phone?.let { Text("Telefon: $it", style = MaterialTheme.typography.bodySmall) }
                            contact.email?.let { Text("E-posta: $it", style = MaterialTheme.typography.bodySmall) }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = {
                                    editingId = contact.id
                                    fullName = contact.fullName
                                    role = contact.role.orEmpty()
                                    phone = contact.phone.orEmpty()
                                    email = contact.email.orEmpty()
                                    makePrimary = contact.isPrimary
                                    formError = null
                                }) { Text("Düzenle") }
                                if (!contact.isPrimary) {
                                    OutlinedButton(onClick = { onMakePrimary(contact.id) }) { Text("Birincil yap") }
                                }
                            }
                        }
                    }
                }
            }

            Text(if (editingId == null) "Yeni yetkili" else "Yetkiliyi düzenle", style = MaterialTheme.typography.titleSmall)
            OutlinedTextField(fullName, { fullName = it; formError = null }, Modifier.fillMaxWidth().testTag("contact_name"), label = { Text("Ad soyad") }, singleLine = true)
            OutlinedTextField(role, { role = it; formError = null }, Modifier.fillMaxWidth(), label = { Text("Görev / rol") }, singleLine = true)
            OutlinedTextField(phone, { phone = it; formError = null }, Modifier.fillMaxWidth(), label = { Text("Telefon") }, singleLine = true)
            OutlinedTextField(email, { email = it; formError = null }, Modifier.fillMaxWidth(), label = { Text("E-posta") }, singleLine = true)
            formError?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("contact_form_error"))
            }
            if (editingId == null && !makePrimary) {
                OutlinedButton(onClick = { makePrimary = true }, Modifier.fillMaxWidth()) { Text("Birincil kişi olarak ekle") }
            } else if (editingId == null && makePrimary) {
                Text("Birincil kişi olarak eklenecek.", style = MaterialTheme.typography.bodySmall)
            }
            Button(
                onClick = {
                    val validated = runCatching {
                        ContactFormInput(
                            name = CrmContactValidator.normalizeName(fullName),
                            role = CrmContactValidator.normalizeOptionalText(role),
                            phone = CrmContactValidator.normalizePhone(phone),
                            email = CrmContactValidator.normalizeEmail(email),
                        )
                    }.onFailure { formError = it.message ?: "Yetkili bilgilerini kontrol edin." }.getOrNull()
                        ?: return@Button
                    val id = editingId
                    if (id == null) {
                        onCreate(validated.name, validated.role, validated.phone, validated.email, makePrimary)
                    } else {
                        onUpdate(id, validated.name, validated.role, validated.phone, validated.email)
                    }
                    clearForm()
                },
                enabled = fullName.isNotBlank(),
                modifier = Modifier.fillMaxWidth().testTag("contact_save"),
            ) { Text(if (editingId == null) "Yetkili ekle" else "Değişiklikleri kaydet") }
            if (editingId != null) {
                OutlinedButton(onClick = { clearForm() }, Modifier.fillMaxWidth()) { Text("Düzenlemeyi iptal et") }
            }
        }
    }
}

private data class ContactFormInput(
    val name: String,
    val role: String?,
    val phone: String?,
    val email: String?,
)

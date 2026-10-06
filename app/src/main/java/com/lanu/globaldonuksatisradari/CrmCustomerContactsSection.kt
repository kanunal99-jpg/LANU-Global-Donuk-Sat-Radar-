package com.lanu.globaldonuksatisradari

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import com.lanu.globaldonuksatisradari.crm.ContactCrmRepository
import com.lanu.globaldonuksatisradari.crm.CrmCustomer
import com.lanu.globaldonuksatisradari.crm.LanuCrmDatabase
import kotlinx.coroutines.launch

/** Customer-detail persistence boundary for owner-scoped contacts and commercial documents. */
@Composable
fun CrmCustomerContactsSection(
    customer: CrmCustomer,
    onMessage: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val database = remember(context) { LanuCrmDatabase.getInstance(context) }
    val contactRepository = remember(database, customer.ownerUserId) {
        ContactCrmRepository(database = database, ownerUserId = customer.ownerUserId)
    }
    val contacts by remember(contactRepository, customer.id) {
        contactRepository.observeContacts(customer.id)
    }.collectAsState(initial = emptyList())
    CrmContactsCard(
        customerId = customer.id,
        contacts = contacts,
        onCreate = { fullName, role, phone, email, makePrimary ->
            scope.launch {
                runCatching {
                    contactRepository.createContact(
                        customerId = customer.id,
                        fullName = fullName,
                        role = role,
                        phone = phone,
                        email = email,
                        makePrimary = makePrimary,
                    )
                }.onSuccess { onMessage("Yetkili kişi kaydedildi.") }
                    .onFailure { onMessage("Yetkili kişi kaydedilemedi: ${it.message.orEmpty()}") }
            }
        },
        onUpdate = { contactId, fullName, role, phone, email ->
            scope.launch {
                runCatching {
                    contactRepository.updateContact(
                        contactId = contactId,
                        fullName = fullName,
                        role = role,
                        phone = phone,
                        email = email,
                    )
                }.onSuccess { onMessage("Yetkili kişi güncellendi.") }
                    .onFailure { onMessage("Yetkili kişi güncellenemedi: ${it.message.orEmpty()}") }
            }
        },
        onMakePrimary = { contactId ->
            scope.launch {
                runCatching { contactRepository.makePrimary(contactId) }
                    .onSuccess { onMessage("Birincil yetkili güncellendi.") }
                    .onFailure { onMessage("Birincil yetkili güncellenemedi: ${it.message.orEmpty()}") }
            }
        },
    )
}

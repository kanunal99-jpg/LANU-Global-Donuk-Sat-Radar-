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

/**
 * Owner-scoped contact section for the CRM customer detail surface.
 *
 * The repository boundary is intentionally created from the selected customer's owner id so
 * authenticated and anonymous/local records cannot bleed into each other. Contact mutations stay
 * LOCAL_ONLY until the LANU Supabase schema/RLS is verified end-to-end.
 */
@Composable
fun CrmCustomerContactsSection(
    customer: CrmCustomer,
    onMessage: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember(context, customer.ownerUserId) {
        ContactCrmRepository(
            database = LanuCrmDatabase.getInstance(context),
            ownerUserId = customer.ownerUserId,
        )
    }
    val contacts by remember(repository, customer.id) {
        repository.observeContacts(customer.id)
    }.collectAsState(initial = emptyList())

    CrmContactsCard(
        customerId = customer.id,
        contacts = contacts,
        onCreate = { fullName, role, phone, email, makePrimary ->
            scope.launch {
                runCatching {
                    repository.createContact(
                        customerId = customer.id,
                        fullName = fullName,
                        role = role,
                        phone = phone,
                        email = email,
                        makePrimary = makePrimary,
                    )
                }.onSuccess {
                    onMessage("Yetkili kişi kaydedildi.")
                }.onFailure {
                    onMessage("Yetkili kişi kaydedilemedi: ${it.message.orEmpty()}")
                }
            }
        },
        onUpdate = { contactId, fullName, role, phone, email ->
            scope.launch {
                runCatching {
                    repository.updateContact(
                        contactId = contactId,
                        fullName = fullName,
                        role = role,
                        phone = phone,
                        email = email,
                    )
                }.onSuccess {
                    onMessage("Yetkili kişi güncellendi.")
                }.onFailure {
                    onMessage("Yetkili kişi güncellenemedi: ${it.message.orEmpty()}")
                }
            }
        },
        onMakePrimary = { contactId ->
            scope.launch {
                runCatching { repository.makePrimary(contactId) }
                    .onSuccess { onMessage("Birincil yetkili güncellendi.") }
                    .onFailure { onMessage("Birincil yetkili güncellenemedi: ${it.message.orEmpty()}") }
            }
        },
    )
}

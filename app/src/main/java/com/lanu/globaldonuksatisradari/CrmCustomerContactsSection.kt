package com.lanu.globaldonuksatisradari

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import com.lanu.globaldonuksatisradari.crm.CommercialCrmRepository
import com.lanu.globaldonuksatisradari.crm.ContactCrmRepository
import com.lanu.globaldonuksatisradari.crm.CrmCustomer
import com.lanu.globaldonuksatisradari.crm.CrmQuoteStatus
import com.lanu.globaldonuksatisradari.crm.LanuCrmDatabase
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

/** Customer-detail persistence boundary for owner-scoped contacts and commercial documents. */
@Composable
fun CrmCustomerContactsSection(
    customer: CrmCustomer,
    catalogProducts: List<CatalogProduct> = emptyList(),
    onMessage: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val database = remember(context) { LanuCrmDatabase.getInstance(context) }
    val contactRepository = remember(database, customer.ownerUserId) {
        ContactCrmRepository(database = database, ownerUserId = customer.ownerUserId)
    }
    val commercialRepository = remember(database, customer.ownerUserId) {
        CommercialCrmRepository(database = database, ownerUserId = customer.ownerUserId)
    }

    val contacts by remember(contactRepository, customer.id) {
        contactRepository.observeContacts(customer.id)
    }.collectAsState(initial = emptyList())
    val quotes by remember(commercialRepository, customer.id) {
        commercialRepository.observeQuotes(customer.id)
    }.collectAsState(initial = emptyList())
    val orders by remember(commercialRepository, customer.id) {
        commercialRepository.observeOrders(customer.id)
    }.collectAsState(initial = emptyList())

    val quoteLinesFlow = remember(commercialRepository, quotes.map { it.id }) {
        if (quotes.isEmpty()) flowOf(emptyMap()) else combine(
            quotes.map { quote -> commercialRepository.observeQuoteLines(quote.id) },
        ) { rows -> quotes.mapIndexed { index, quote -> quote.id to rows[index] }.toMap() }
    }
    val orderLinesFlow = remember(commercialRepository, orders.map { it.id }) {
        if (orders.isEmpty()) flowOf(emptyMap()) else combine(
            orders.map { order -> commercialRepository.observeOrderLines(order.id) },
        ) { rows -> orders.mapIndexed { index, order -> order.id to rows[index] }.toMap() }
    }
    val quoteLines by quoteLinesFlow.collectAsState(initial = emptyMap())
    val orderLines by orderLinesFlow.collectAsState(initial = emptyMap())

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

    CrmCommercialSection(
        quotes = quotes,
        orders = orders,
        quoteLines = quoteLines,
        orderLines = orderLines,
        catalogProducts = catalogProducts,
        onCreateQuote = { quoteNumber, currency ->
            scope.launch {
                runCatching {
                    commercialRepository.createQuote(
                        customerId = customer.id,
                        opportunityId = null,
                        quoteNumber = quoteNumber,
                        currency = currency,
                    )
                }.onSuccess { onMessage("Taslak teklif oluşturuldu.") }
                    .onFailure { onMessage("Teklif oluşturulamadı: ${it.message.orEmpty()}") }
            }
        },
        onAddQuoteLine = { quoteId, productId, productName, unit, quantityMilli, unitPriceMinor ->
            scope.launch {
                runCatching {
                    commercialRepository.addQuoteLine(
                        quoteId = quoteId,
                        productId = productId,
                        productName = productName,
                        unit = unit,
                        quantityMilli = quantityMilli,
                        unitPriceMinor = unitPriceMinor,
                    )
                }.onSuccess { onMessage("Teklif ürün satırı eklendi.") }
                    .onFailure { onMessage("Teklif satırı eklenemedi: ${it.message.orEmpty()}") }
            }
        },
        onSendQuote = { quoteId ->
            scope.launch {
                runCatching { commercialRepository.transitionQuoteStatus(quoteId, CrmQuoteStatus.SENT) }
                    .onSuccess { onMessage("Teklif gönderildi olarak işaretlendi.") }
                    .onFailure { onMessage("Teklif gönderilemedi: ${it.message.orEmpty()}") }
            }
        },
        onAcceptQuote = { quoteId ->
            scope.launch {
                runCatching { commercialRepository.transitionQuoteStatus(quoteId, CrmQuoteStatus.ACCEPTED) }
                    .onSuccess { onMessage("Teklif kabul edildi.") }
                    .onFailure { onMessage("Teklif kabul edilemedi: ${it.message.orEmpty()}") }
            }
        },
        onCreateOrder = { quoteId, orderNumber ->
            scope.launch {
                runCatching { commercialRepository.createOrderFromAcceptedQuote(quoteId, orderNumber) }
                    .onSuccess { onMessage("Kabul edilen teklif siparişe dönüştürüldü.") }
                    .onFailure { onMessage("Sipariş oluşturulamadı: ${it.message.orEmpty()}") }
            }
        },
    )
}

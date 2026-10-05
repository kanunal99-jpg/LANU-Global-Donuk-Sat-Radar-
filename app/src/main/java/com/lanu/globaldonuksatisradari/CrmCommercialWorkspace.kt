package com.lanu.globaldonuksatisradari

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import com.lanu.globaldonuksatisradari.crm.CommercialCrmRepository
import com.lanu.globaldonuksatisradari.crm.CrmOrderStatus
import kotlinx.coroutines.launch
import com.lanu.globaldonuksatisradari.crm.CrmQuoteStatus

@Composable
fun CrmCommercialWorkspace(
    customerId: String,
    repository: CommercialCrmRepository,
    productRepository: ProductCatalogRepository,
    onMessage: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val quotes by repository.observeQuotes(customerId).collectAsState(initial = emptyList())
    val orders by repository.observeOrders(customerId).collectAsState(initial = emptyList())
    val products by productRepository.products.collectAsState()

    val quoteLines = mutableMapOf<String, List<com.lanu.globaldonuksatisradari.crm.CrmCommercialLine>>()
    for (quote in quotes) {
        val lines by repository.observeQuoteLines(quote.id).collectAsState(initial = emptyList())
        quoteLines[quote.id] = lines
    }
    val orderLines = mutableMapOf<String, List<com.lanu.globaldonuksatisradari.crm.CrmCommercialLine>>()
    for (order in orders) {
        val lines by repository.observeOrderLines(order.id).collectAsState(initial = emptyList())
        orderLines[order.id] = lines
    }

    CrmCommercialSection(
        quotes = quotes,
        orders = orders,
        quoteLines = quoteLines,
        orderLines = orderLines,
        products = products,
        onCreateQuote = { quoteNumber, currency ->
            scope.launch {
                runCatching {
                    repository.createQuote(
                        customerId = customerId,
                        opportunityId = null,
                        quoteNumber = quoteNumber,
                        currency = currency,
                    )
                }.onSuccess { onMessage("Taslak teklif oluşturuldu.") }
                    .onFailure { onMessage("Teklif oluşturulamadı: " + it.message.orEmpty()) }
            }
        },
        onAddQuoteLine = { quoteId, productId, productName, unit, quantityMilli, unitPriceMinor ->
            scope.launch {
                runCatching {
                    repository.addQuoteLine(
                        quoteId = quoteId,
                        productId = productId,
                        productName = productName,
                        unit = unit,
                        quantityMilli = quantityMilli,
                        unitPriceMinor = unitPriceMinor,
                    )
                }.onSuccess { onMessage("Ürün teklif satırına eklendi.") }
                    .onFailure { onMessage("Teklif satırı eklenemedi: " + it.message.orEmpty()) }
            }
        },
        onSendQuote = { quoteId ->
            scope.launch {
                runCatching { repository.transitionQuoteStatus(quoteId, CrmQuoteStatus.SENT) }
                    .onSuccess { onMessage("Teklif gönderildi olarak işaretlendi.") }
                    .onFailure { onMessage("Teklif güncellenemedi: " + it.message.orEmpty()) }
            }
        },
        onAcceptQuote = { quoteId ->
            scope.launch {
                runCatching { repository.transitionQuoteStatus(quoteId, CrmQuoteStatus.ACCEPTED) }
                    .onSuccess { onMessage("Teklif kabul edildi olarak işaretlendi.") }
                    .onFailure { onMessage("Teklif güncellenemedi: " + it.message.orEmpty()) }
            }
        },
        onCreateOrder = { quoteId, orderNumber ->
            scope.launch {
                runCatching { repository.createOrderFromAcceptedQuote(quoteId, orderNumber) }
                    .onSuccess { onMessage("Sipariş oluşturuldu.") }
                    .onFailure { onMessage("Sipariş oluşturulamadı: " + it.message.orEmpty()) }
            }
        },
        onAdvanceOrder = { orderId, target ->
            scope.launch {
                runCatching { repository.transitionOrderStatus(orderId, target) }
                    .onSuccess { onMessage("Sipariş durumu güncellendi.") }
                    .onFailure { onMessage("Sipariş güncellenemedi: " + it.message.orEmpty()) }
            }
        },
    )
}

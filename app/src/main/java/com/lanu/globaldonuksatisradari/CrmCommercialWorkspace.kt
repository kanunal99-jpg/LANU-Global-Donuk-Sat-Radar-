package com.lanu.globaldonuksatisradari

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import com.lanu.globaldonuksatisradari.crm.CommercialCrmRepository
import com.lanu.globaldonuksatisradari.crm.CrmQuoteStatus
import kotlinx.coroutines.launch

@Composable
fun CrmCommercialWorkspace(
    customerId: String,
    repository: CommercialCrmRepository,
    catalogProducts: List<CatalogProduct>,
    onMessage: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val quotes by repository.observeQuotes(customerId).collectAsState(initial = emptyList())
    val orders by repository.observeOrders(customerId).collectAsState(initial = emptyList())
    val allQuoteLines by repository.observeQuoteLinesForCustomer(customerId)
        .collectAsState(initial = emptyList())
    val allOrderLines by repository.observeOrderLinesForCustomer(customerId)
        .collectAsState(initial = emptyList())
    val quoteLines = allQuoteLines.groupBy { it.parentId }
    val orderLines = allOrderLines.groupBy { it.parentId }

    CrmCommercialSection(
        quotes = quotes,
        orders = orders,
        quoteLines = quoteLines,
        orderLines = orderLines,
        catalogProducts = catalogProducts,
        onCreateQuote = { quoteNumber, currency ->
            scope.launch {
                runCatching {
                    repository.createQuote(
                        customerId = customerId,
                        opportunityId = null,
                        quoteNumber = quoteNumber,
                        currency = currency,
                    )
                }.onSuccess {
                    onMessage("Taslak teklif oluşturuldu.")
                }.onFailure {
                    onMessage("Teklif oluşturulamadı: ${it.message.orEmpty()}")
                }
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
                }.onSuccess {
                    onMessage("Ürün teklife eklendi.")
                }.onFailure {
                    onMessage("Teklif satırı eklenemedi: ${it.message.orEmpty()}")
                }
            }
        },
        onSendQuote = { quoteId ->
            scope.launch {
                runCatching {
                    repository.transitionQuoteStatus(quoteId, CrmQuoteStatus.SENT)
                }.onSuccess {
                    onMessage("Teklif gönderildi olarak işaretlendi.")
                }.onFailure {
                    onMessage("Teklif durumu güncellenemedi: ${it.message.orEmpty()}")
                }
            }
        },
        onAcceptQuote = { quoteId ->
            scope.launch {
                runCatching {
                    repository.transitionQuoteStatus(quoteId, CrmQuoteStatus.ACCEPTED)
                }.onSuccess {
                    onMessage("Teklif kabul edildi.")
                }.onFailure {
                    onMessage("Teklif kabul edilemedi: ${it.message.orEmpty()}")
                }
            }
        },
        onCreateOrder = { quoteId, orderNumber ->
            scope.launch {
                runCatching {
                    repository.createOrderFromAcceptedQuote(
                        quoteId = quoteId,
                        orderNumber = orderNumber,
                    )
                }.onSuccess {
                    onMessage("Kabul edilen teklif siparişe dönüştürüldü.")
                }.onFailure {
                    onMessage("Sipariş oluşturulamadı: ${it.message.orEmpty()}")
                }
            }
        },
    )
}

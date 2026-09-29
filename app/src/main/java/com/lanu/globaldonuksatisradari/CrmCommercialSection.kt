package com.lanu.globaldonuksatisradari

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.lanu.globaldonuksatisradari.crm.CrmCommercialLine
import com.lanu.globaldonuksatisradari.crm.CrmOrder
import com.lanu.globaldonuksatisradari.crm.CrmQuote
import com.lanu.globaldonuksatisradari.crm.CrmQuoteStatus

/**
 * Customer-detail commercial workspace. All persistence stays in CommercialCrmRepository;
 * this composable only validates basic UI input and exposes explicit user actions.
 */
@Composable
fun CrmCommercialSection(
    quotes: List<CrmQuote>,
    orders: List<CrmOrder>,
    quoteLines: Map<String, List<CrmCommercialLine>>,
    orderLines: Map<String, List<CrmCommercialLine>>,
    onCreateQuote: (quoteNumber: String, currency: String) -> Unit,
    onAddQuoteLine: (quoteId: String, productName: String, unit: String, quantityMilli: Long, unitPriceMinor: Long) -> Unit,
    onSendQuote: (String) -> Unit,
    onAcceptQuote: (String) -> Unit,
    onCreateOrder: (quoteId: String, orderNumber: String) -> Unit,
) {
    var quoteNumber by remember { mutableStateOf("") }
    var currency by remember { mutableStateOf("TRY") }
    var productName by remember { mutableStateOf("") }
    var unit by remember { mutableStateOf("adet") }
    var quantity by remember { mutableStateOf("1") }
    var price by remember { mutableStateOf("") }
    var orderNumber by remember { mutableStateOf("") }

    Card(Modifier.fillMaxWidth().testTag("crm_commercial_section")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Teklif ve sipariş", style = MaterialTheme.typography.titleMedium)
            Text("Ticari kayıtlar cihazda güvenli şekilde saklanır; doğrulanmamış bulut kaydı senkronize gösterilmez.", style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(quoteNumber, { quoteNumber = it }, Modifier.weight(1f), label = { Text("Teklif no") }, singleLine = true)
                OutlinedTextField(currency, { currency = it.uppercase().take(3) }, label = { Text("Para") }, singleLine = true)
            }
            Button(
                onClick = { if (quoteNumber.isNotBlank() && currency.length == 3) { onCreateQuote(quoteNumber.trim(), currency); quoteNumber = "" } },
                modifier = Modifier.fillMaxWidth().testTag("crm_create_quote"),
                enabled = quoteNumber.isNotBlank() && currency.length == 3,
            ) { Text("Taslak teklif oluştur") }

            if (quotes.isEmpty()) Text("Henüz teklif yok.")
            quotes.forEach { quote ->
                Card(Modifier.fillMaxWidth().testTag("crm_quote_${quote.id}")) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("${quote.quoteNumber} • ${quote.status.name} • ${quote.currency}")
                        quoteLines[quote.id].orEmpty().forEach { line -> Text("• ${line.productName} — ${line.quantityMilli / 1000.0} ${line.unit}") }
                        if (quote.status == CrmQuoteStatus.DRAFT) {
                            OutlinedTextField(productName, { productName = it }, Modifier.fillMaxWidth(), label = { Text("Ürün adı") })
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(unit, { unit = it }, Modifier.weight(1f), label = { Text("Birim") })
                                OutlinedTextField(quantity, { quantity = it }, Modifier.weight(1f), label = { Text("Miktar") })
                                OutlinedTextField(price, { price = it }, Modifier.weight(1f), label = { Text("Birim fiyat") })
                            }
                            Button(onClick = {
                                val q = quantity.replace(',', '.').toBigDecimalOrNull()
                                val p = price.replace(',', '.').toBigDecimalOrNull()
                                if (productName.isNotBlank() && unit.isNotBlank() && q != null && q > java.math.BigDecimal.ZERO && p != null && p >= java.math.BigDecimal.ZERO) {
                                    onAddQuoteLine(quote.id, productName.trim(), unit.trim(), q.movePointRight(3).longValueExact(), p.movePointRight(2).longValueExact())
                                    productName = ""; price = ""; quantity = "1"
                                }
                            }, Modifier.fillMaxWidth(), enabled = productName.isNotBlank() && price.isNotBlank()) { Text("Teklife ürün satırı ekle") }
                            OutlinedButton(onClick = { onSendQuote(quote.id) }, Modifier.fillMaxWidth()) { Text("Teklifi gönderildi olarak işaretle") }
                        }
                        if (quote.status == CrmQuoteStatus.SENT) Button(onClick = { onAcceptQuote(quote.id) }, Modifier.fillMaxWidth()) { Text("Teklifi kabul edildi olarak işaretle") }
                        if (quote.status == CrmQuoteStatus.ACCEPTED) {
                            OutlinedTextField(orderNumber, { orderNumber = it }, Modifier.fillMaxWidth(), label = { Text("Sipariş no") })
                            Button(onClick = { if (orderNumber.isNotBlank()) { onCreateOrder(quote.id, orderNumber.trim()); orderNumber = "" } }, Modifier.fillMaxWidth(), enabled = orderNumber.isNotBlank()) { Text("Siparişe dönüştür") }
                        }
                    }
                }
            }

            Text("Siparişler", style = MaterialTheme.typography.titleSmall)
            if (orders.isEmpty()) Text("Henüz sipariş yok.")
            orders.forEach { order ->
                Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) {
                    Text("${order.orderNumber} • ${order.status.name} • ${order.currency}")
                    orderLines[order.id].orEmpty().forEach { line -> Text("• ${line.productName} — ${line.quantityMilli / 1000.0} ${line.unit}") }
                } }
            }
        }
    }
}

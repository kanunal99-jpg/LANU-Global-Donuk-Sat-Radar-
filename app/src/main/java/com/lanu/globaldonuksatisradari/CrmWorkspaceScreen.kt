package com.lanu.globaldonuksatisradari

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.lanu.globaldonuksatisradari.crm.CrmCustomer
import com.lanu.globaldonuksatisradari.crm.CrmDashboardMetrics
import com.lanu.globaldonuksatisradari.crm.CrmDuplicateDetector
import com.lanu.globaldonuksatisradari.crm.CrmNextAction
import com.lanu.globaldonuksatisradari.crm.CrmNextActionType
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private enum class CrmWorkspaceTab { TODAY, CUSTOMERS, DUPLICATES, DASHBOARD }

data class CrmTodayBuckets(
    val overdue: List<CrmNextAction>,
    val today: List<CrmNextAction>,
    val upcoming: List<CrmNextAction>,
)

internal fun bucketOpenActions(
    actions: List<CrmNextAction>,
    nowEpochMs: Long,
    zoneId: ZoneId = ZoneId.systemDefault(),
): CrmTodayBuckets {
    val today = Instant.ofEpochMilli(nowEpochMs).atZone(zoneId).toLocalDate()
    val dayStart = today.atStartOfDay(zoneId).toInstant().toEpochMilli()
    val nextDayStart = today.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
    val open = actions
        .filter { it.completedAtEpochMs == null }
        .sortedBy(CrmNextAction::dueAtEpochMs)
    return CrmTodayBuckets(
        overdue = open.filter { it.dueAtEpochMs < dayStart },
        today = open.filter { it.dueAtEpochMs in dayStart until nextDayStart },
        upcoming = open.filter { it.dueAtEpochMs >= nextDayStart },
    )
}

@Composable
fun CrmWorkspaceScreen(
    customers: List<CrmCustomer>,
    openActions: List<CrmNextAction>,
    metrics: CrmDashboardMetrics,
    selectedCity: String,
    selectedDistrict: String,
    pendingSyncCount: Int,
    onOpenCustomer: (String) -> Unit,
    onCompleteAction: (String) -> Unit,
    onMergeCustomers: (targetCustomerId: String, sourceCustomerId: String) -> Unit,
) {
    var tab by remember { mutableStateOf(CrmWorkspaceTab.TODAY) }

    Column(
        modifier = Modifier.testTag("crm_workspace"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ScrollableTabRow(
            selectedTabIndex = tab.ordinal,
            edgePadding = 8.dp,
        ) {
            Tab(
                selected = tab == CrmWorkspaceTab.TODAY,
                onClick = { tab = CrmWorkspaceTab.TODAY },
                text = { Text("Bugün", maxLines = 1, softWrap = false) },
                modifier = Modifier.testTag("crm_tab_today"),
            )
            Tab(
                selected = tab == CrmWorkspaceTab.CUSTOMERS,
                onClick = { tab = CrmWorkspaceTab.CUSTOMERS },
                text = { Text("Müşteriler", maxLines = 1, softWrap = false) },
                modifier = Modifier.testTag("crm_tab_customers"),
            )
            Tab(
                selected = tab == CrmWorkspaceTab.DUPLICATES,
                onClick = { tab = CrmWorkspaceTab.DUPLICATES },
                text = { Text("Mükerrer", maxLines = 1, softWrap = false) },
                modifier = Modifier.testTag("crm_tab_duplicates"),
            )
            Tab(
                selected = tab == CrmWorkspaceTab.DASHBOARD,
                onClick = { tab = CrmWorkspaceTab.DASHBOARD },
                text = { Text("Dashboard", maxLines = 1, softWrap = false) },
                modifier = Modifier.testTag("crm_tab_dashboard"),
            )
        }

        when (tab) {
            CrmWorkspaceTab.TODAY -> CrmTodayScreen(
                customers = customers,
                openActions = openActions,
                selectedCity = selectedCity,
                selectedDistrict = selectedDistrict,
                pendingSyncCount = pendingSyncCount,
                onOpenCustomer = onOpenCustomer,
                onCompleteAction = onCompleteAction,
            )
            CrmWorkspaceTab.CUSTOMERS -> CrmCustomerListScreen(
                customers = customers,
                selectedCity = selectedCity,
                selectedDistrict = selectedDistrict,
                onOpenCustomer = onOpenCustomer,
            )
            CrmWorkspaceTab.DUPLICATES -> CrmDuplicateReviewScreen(
                customers = customers,
                onOpenCustomer = onOpenCustomer,
                onMergeCustomers = onMergeCustomers,
            )
            CrmWorkspaceTab.DASHBOARD -> {
                LazyColumn(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 16.dp),
                ) {
                    item {
                        SalesDashboard(
                            selectedCity = selectedCity,
                            selectedDistrict = selectedDistrict,
                            metrics = metrics,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CrmTodayScreen(
    customers: List<CrmCustomer>,
    openActions: List<CrmNextAction>,
    selectedCity: String,
    selectedDistrict: String,
    pendingSyncCount: Int,
    onOpenCustomer: (String) -> Unit,
    onCompleteAction: (String) -> Unit,
) {
    val now = System.currentTimeMillis()
    val buckets = remember(openActions, now / 60_000L) {
        bucketOpenActions(openActions, now)
    }
    val customerById = remember(customers) { customers.associateBy { it.id } }

    LazyColumn(
        modifier = Modifier
            .testTag("crm_today_screen")
            .padding(horizontal = 16.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("Bugün", style = MaterialTheme.typography.headlineSmall)
            Text(
                "$selectedCity / $selectedDistrict • saha takip merkezi",
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        item {
            Card(Modifier.fillMaxWidth()) {
                Column(
                    Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        "Geciken ${buckets.overdue.size} • Bugün ${buckets.today.size} • Yaklaşan ${buckets.upcoming.size}",
                        modifier = Modifier.testTag("crm_today_counts"),
                    )
                    Text(
                        if (pendingSyncCount == 0) {
                            "Yerel değişiklik kuyruğu temiz."
                        } else {
                            "$pendingSyncCount değişiklik bulut aktarımı bekliyor; cihazda güvenle saklanıyor."
                        },
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }

        if (buckets.overdue.isNotEmpty()) {
            item { Text("Geciken takipler", style = MaterialTheme.typography.titleMedium) }
            items(buckets.overdue, key = { "overdue-" + it.id }) { action ->
                TodayActionCard(
                    action = action,
                    customer = customerById[action.customerId],
                    isOverdue = true,
                    onOpenCustomer = onOpenCustomer,
                    onCompleteAction = onCompleteAction,
                )
            }
        }

        item { Text("Bugünün işleri", style = MaterialTheme.typography.titleMedium) }
        if (buckets.today.isEmpty()) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Text(
                        "Bugün için planlanmış açık takip yok.",
                        Modifier.padding(14.dp),
                    )
                }
            }
        } else {
            items(buckets.today, key = { "today-" + it.id }) { action ->
                TodayActionCard(
                    action = action,
                    customer = customerById[action.customerId],
                    isOverdue = false,
                    onOpenCustomer = onOpenCustomer,
                    onCompleteAction = onCompleteAction,
                )
            }
        }

        if (buckets.upcoming.isNotEmpty()) {
            item { Text("Yaklaşan", style = MaterialTheme.typography.titleMedium) }
            items(buckets.upcoming.take(20), key = { "upcoming-" + it.id }) { action ->
                TodayActionCard(
                    action = action,
                    customer = customerById[action.customerId],
                    isOverdue = false,
                    onOpenCustomer = onOpenCustomer,
                    onCompleteAction = onCompleteAction,
                )
            }
        }
    }
}

@Composable
private fun TodayActionCard(
    action: CrmNextAction,
    customer: CrmCustomer?,
    isOverdue: Boolean,
    onOpenCustomer: (String) -> Unit,
    onCompleteAction: (String) -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("crm_today_action_${action.id}"),
    ) {
        Column(
            Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                customer?.businessName ?: "CRM kaydı bulunamadı",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                actionTypeLabel(action.type) + " • " +
                    formatDueAt(action.dueAtEpochMs) +
                    if (isOverdue) " • GECİKMİŞ" else "",
            )
            action.note?.takeIf(String::isNotBlank)?.let {
                Text(it, style = MaterialTheme.typography.bodySmall)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    enabled = customer != null,
                    onClick = { customer?.let { onOpenCustomer(it.id) } },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("CRM Aç")
                }
                Button(
                    onClick = { onCompleteAction(action.id) },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Tamamla")
                }
            }
        }
    }
}

@Composable
private fun CrmCustomerListScreen(
    customers: List<CrmCustomer>,
    selectedCity: String,
    selectedDistrict: String,
    onOpenCustomer: (String) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var selectedTag by remember { mutableStateOf<String?>(null) }
    val normalized = query.trim().lowercase(Locale.forLanguageTag("tr-TR"))
    val allTags = remember(customers) {
        customers.flatMap { it.tags }.distinct().sortedWith(String.CASE_INSENSITIVE_ORDER)
    }
    val filtered = remember(customers, normalized, selectedTag) {
        customers.filter { customer ->
            val matchesTag = selectedTag == null || customer.tags.any {
                it.equals(selectedTag, ignoreCase = true)
            }
            val matchesQuery = normalized.isBlank() || listOf(
                customer.businessName,
                customer.signboardName.orEmpty(),
                customer.contactName.orEmpty(),
                customer.phone.orEmpty(),
                customer.district,
                customer.neighborhood.orEmpty(),
                customer.tags.joinToString(" "),
            ).any { it.lowercase(Locale.forLanguageTag("tr-TR")).contains(normalized) }
            matchesTag && matchesQuery
        }
    }

    LazyColumn(
        modifier = Modifier
            .testTag("crm_customer_list")
            .padding(horizontal = 16.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text("CRM Müşterileri", style = MaterialTheme.typography.headlineSmall)
            Text("$selectedCity / $selectedDistrict • ${customers.size} kayıt")
        }
        if (allTags.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = selectedTag == null,
                        onClick = { selectedTag = null },
                        label = { Text("Tüm etiketler") },
                    )
                    allTags.forEach { tag ->
                        FilterChip(
                            selected = selectedTag == tag,
                            onClick = { selectedTag = tag },
                            label = { Text(tag) },
                        )
                    }
                }
            }
        }
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Müşteri / tabela / telefon ara") },
                singleLine = true,
            )
        }
        item {
            CrmExportActions(customers)
        }
        if (filtered.isEmpty()) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Text("Bu filtrede CRM kaydı bulunamadı.", Modifier.padding(14.dp))
                }
            }
        }
        items(filtered, key = { it.id }) { customer ->
            Card(Modifier.fillMaxWidth()) {
                Column(
                    Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(customer.businessName, style = MaterialTheme.typography.titleMedium)
                    customer.signboardName?.takeIf(String::isNotBlank)?.let {
                        Text("Tabela: $it", style = MaterialTheme.typography.bodySmall)
                    }
                    Text(
                        "${customer.city} • ${customer.district} • ${stageLabelForMap(customer.stage)}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    if (customer.tags.isNotEmpty()) {
                        Text(
                            "Etiketler: " + customer.tags.joinToString(" • "),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    OutlinedButton(
                        onClick = { onOpenCustomer(customer.id) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("crm_open_${customer.businessSourceId}"),
                    ) {
                        Text("Aç")
                    }
                }
            }
        }
    }
}

@Composable
private fun CrmDuplicateReviewScreen(
    customers: List<CrmCustomer>,
    onOpenCustomer: (String) -> Unit,
    onMergeCustomers: (targetCustomerId: String, sourceCustomerId: String) -> Unit,
) {
    val candidates = remember(customers) { CrmDuplicateDetector.find(customers) }
    var pendingMerge by remember { mutableStateOf<Pair<CrmCustomer, CrmCustomer>?>(null) }

    pendingMerge?.let { (target, source) ->
        AlertDialog(
            onDismissRequest = { pendingMerge = null },
            title = { Text("Kayıtları birleştir") },
            text = {
                Text(
                    "'${source.businessName}' kaydı '${target.businessName}' içine birleştirilecek. " +
                        "Alt CRM kayıtları taşınacak; kaynak kayıt denetim izi olarak gizli tombstone kalacak.",
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onMergeCustomers(target.id, source.id)
                        pendingMerge = null
                    },
                    modifier = Modifier.testTag("crm_duplicate_confirm"),
                ) {
                    Text("Birleştir")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingMerge = null }) { Text("Vazgeç") }
            },
        )
    }

    LazyColumn(
        modifier = Modifier
            .testTag("crm_duplicate_screen")
            .padding(horizontal = 16.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text("Muhtemel Mükerrerler", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Sicil/VKN, telefon, işletme adı, adres ve yakın koordinat kanıtları birlikte değerlendirilir.",
                style = MaterialTheme.typography.bodySmall,
            )
            Text("${candidates.size} aday çift", modifier = Modifier.testTag("crm_duplicate_count"))
        }
        if (candidates.isEmpty()) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Text("Güçlü mükerrer adayı bulunmadı.", Modifier.padding(14.dp))
                }
            }
        }
        items(candidates, key = { it.first.id + "|" + it.second.id }) { candidate ->
            Card(Modifier.fillMaxWidth()) {
                Column(
                    Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text("Skor: ${candidate.score}", style = MaterialTheme.typography.titleMedium)
                    Text(candidate.reasons.joinToString(" • "), style = MaterialTheme.typography.bodySmall)
                    Text("A: ${candidate.first.businessName} — ${candidate.first.district}")
                    Text("B: ${candidate.second.businessName} — ${candidate.second.district}")
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(
                            onClick = { onOpenCustomer(candidate.first.id) },
                            modifier = Modifier.weight(1f),
                        ) { Text("A'yı aç") }
                        OutlinedButton(
                            onClick = { onOpenCustomer(candidate.second.id) },
                            modifier = Modifier.weight(1f),
                        ) { Text("B'yi aç") }
                    }
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(
                            onClick = { pendingMerge = candidate.first to candidate.second },
                            modifier = Modifier.weight(1f).testTag("crm_merge_keep_${candidate.first.id}"),
                        ) { Text("A ana kalsın") }
                        Button(
                            onClick = { pendingMerge = candidate.second to candidate.first },
                            modifier = Modifier.weight(1f).testTag("crm_merge_keep_${candidate.second.id}"),
                        ) { Text("B ana kalsın") }
                    }
                }
            }
        }
    }
}

private fun actionTypeLabel(type: CrmNextActionType): String = when (type) {
    CrmNextActionType.CALL -> "Arama"
    CrmNextActionType.VISIT -> "Ziyaret"
    CrmNextActionType.MEETING -> "Görüşme"
    CrmNextActionType.SAMPLE_FOLLOW_UP -> "Numune takibi"
    CrmNextActionType.PROPOSAL_FOLLOW_UP -> "Teklif takibi"
    CrmNextActionType.ORDER_FOLLOW_UP -> "Sipariş takibi"
    CrmNextActionType.NOTE -> "Not / takip"
}

private fun formatDueAt(epochMs: Long): String =
    DUE_FORMATTER.format(Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()))

private val DUE_FORMATTER = DateTimeFormatter.ofPattern(
    "dd.MM.yyyy HH:mm",
    Locale.forLanguageTag("tr-TR"),
)

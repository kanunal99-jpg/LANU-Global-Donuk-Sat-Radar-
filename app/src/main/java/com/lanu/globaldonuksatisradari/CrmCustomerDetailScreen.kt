package com.lanu.globaldonuksatisradari

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.lanu.globaldonuksatisradari.crm.CrmActivity
import com.lanu.globaldonuksatisradari.crm.CrmOpportunity
import com.lanu.globaldonuksatisradari.crm.CrmOpportunityStatus
import com.lanu.globaldonuksatisradari.crm.CrmActivityType
import com.lanu.globaldonuksatisradari.crm.CrmCustomer
import com.lanu.globaldonuksatisradari.crm.CrmNextAction
import com.lanu.globaldonuksatisradari.crm.CrmNextActionType
import com.lanu.globaldonuksatisradari.crm.CrmStage
import com.lanu.globaldonuksatisradari.crm.CrmStageRules
import com.lanu.globaldonuksatisradari.crm.CrmStageTransition
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun CrmCustomerDetailScreen(
    customer: CrmCustomer,
    activities: List<CrmActivity>,
    nextActions: List<CrmNextAction>,
    transitions: List<CrmStageTransition>,
    opportunities: List<CrmOpportunity>,
    onBack: () -> Unit,
    onStageChange: (CrmStage, String?) -> Unit,
    onRecordActivity: (CrmActivityType, String?) -> Unit,
    onCreateNextAction: (CrmNextActionType, Long, String?) -> Unit,
    onCompleteNextAction: (String) -> Unit,
    onCreateOpportunity: (String, String?, Long?, String?) -> Unit,
    onTransitionOpportunity: (String, CrmOpportunityStatus) -> Unit,
    onSaveNotes: (String?) -> Unit,
    message: String? = null,
) {
    var stageMenu by remember(customer.id, customer.stage) { mutableStateOf(false) }
    var activityMenu by remember { mutableStateOf(false) }
    var actionMenu by remember { mutableStateOf(false) }
    var activityType by remember { mutableStateOf(CrmActivityType.NOTE) }
    var actionType by remember { mutableStateOf(CrmNextActionType.CALL) }
    var activityNote by remember { mutableStateOf("") }
    var actionNote by remember { mutableStateOf("") }
    var notes by remember(customer.id, customer.notes) { mutableStateOf(customer.notes.orEmpty()) }
    var dueAt by remember(customer.id) { mutableStateOf(defaultTomorrowNine()) }
    var opportunityTitle by remember { mutableStateOf("") }
    var opportunityNotes by remember { mutableStateOf("") }
    var opportunityAmount by remember { mutableStateOf("") }
    var opportunityCurrency by remember { mutableStateOf("TRY") }
    var opportunityMenuId by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 0.dp, max = 900.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text("← CRM listesine dön")
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(customer.businessName, style = MaterialTheme.typography.headlineSmall)
                Text("${customer.city} • ${customer.district}${customer.neighborhood?.let { " • ${it}" } ?: ""}")
                Text("CRM aşaması: ${stageLabel(customer.stage)}")

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { stageMenu = true }) {
                        Text("Aşamayı değiştir")
                    }
                    DropdownMenu(
                        expanded = stageMenu,
                        onDismissRequest = { stageMenu = false },
                    ) {
                        CrmStage.values()
                            .filter { it != customer.stage && CrmStageRules.canTransition(customer.stage, it) }
                            .forEach { target ->
                                DropdownMenuItem(
                                    text = { Text(stageLabel(target)) },
                                    onClick = {
                                        stageMenu = false
                                        onStageChange(target, null)
                                    },
                                )
                            }
                    }
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Müşteri notu", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    label = { Text("Saha notu") },
                )
                Button(onClick = { onSaveNotes(notes) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Notu kaydet")
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Satış fırsatı", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Ticari değer girilmezse sistem satış potansiyeli üretmez. Girilen değer kullanıcı verisidir.",
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedTextField(
                    value = opportunityTitle,
                    onValueChange = { opportunityTitle = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Fırsat başlığı") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = opportunityNotes,
                    onValueChange = { opportunityNotes = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    label = { Text("Not (opsiyonel)") },
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = opportunityAmount,
                        onValueChange = { opportunityAmount = it },
                        modifier = Modifier.weight(1f),
                        label = { Text("Tutar (opsiyonel)") },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = opportunityCurrency,
                        onValueChange = { opportunityCurrency = it.uppercase().take(3) },
                        modifier = Modifier.width(92.dp),
                        label = { Text("Para") },
                        singleLine = true,
                    )
                }
                Button(
                    onClick = {
                        onCreateOpportunity(
                            opportunityTitle,
                            opportunityNotes.takeIf { it.isNotBlank() },
                            parseOpportunityAmountMinor(opportunityAmount),
                            opportunityCurrency.takeIf { it.isNotBlank() },
                        )
                        opportunityTitle = ""
                        opportunityNotes = ""
                        opportunityAmount = ""
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Fırsatı kaydet")
                }

                if (opportunities.isEmpty()) {
                    Text("Henüz fırsat kaydı yok.", style = MaterialTheme.typography.bodySmall)
                } else {
                    opportunities.forEach { opportunity ->
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text(opportunity.title, style = MaterialTheme.typography.titleMedium)
                                Text("Durum: " + opportunityStatusLabel(opportunity.status))
                                if (opportunity.estimatedValueMinor != null) {
                                    Text(
                                        "Kullanıcı girilen değer: " +
                                            formatOpportunityValue(
                                                opportunity.estimatedValueMinor,
                                                opportunity.currency ?: "TRY",
                                            ),
                                    )
                                } else {
                                    Text("Ticari değer: girilmedi")
                                }
                                opportunity.notes?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                                OutlinedButton(
                                    onClick = { opportunityMenuId = opportunity.id },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text("Fırsat durumunu değiştir")
                                }
                                DropdownMenu(
                                    expanded = opportunityMenuId == opportunity.id,
                                    onDismissRequest = { opportunityMenuId = null },
                                ) {
                                    CrmOpportunityStatus.values().forEach { status ->
                                        DropdownMenuItem(
                                            text = { Text(opportunityStatusLabel(status)) },
                                            onClick = {
                                                opportunityMenuId = null
                                                onTransitionOpportunity(opportunity.id, status)
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Aktivite kaydet", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { activityMenu = true }) {
                        Text(activityLabel(activityType))
                    }
                    DropdownMenu(
                        expanded = activityMenu,
                        onDismissRequest = { activityMenu = false },
                    ) {
                        CrmActivityType.values().forEach { type ->
                            DropdownMenuItem(
                                text = { Text(activityLabel(type)) },
                                onClick = {
                                    activityType = type
                                    activityMenu = false
                                },
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = activityNote,
                    onValueChange = { activityNote = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Aktivite notu (opsiyonel)") },
                )
                Button(
                    onClick = {
                        onRecordActivity(activityType, activityNote.takeIf { it.isNotBlank() })
                        activityNote = ""
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Aktiviteyi kaydet")
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Sonraki aksiyon", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { actionMenu = true }) {
                        Text(nextActionLabel(actionType))
                    }
                    DropdownMenu(
                        expanded = actionMenu,
                        onDismissRequest = { actionMenu = false },
                    ) {
                        CrmNextActionType.values().forEach { type ->
                            DropdownMenuItem(
                                text = { Text(nextActionLabel(type)) },
                                onClick = {
                                    actionType = type
                                    actionMenu = false
                                },
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = actionNote,
                    onValueChange = { actionNote = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Takip notu (opsiyonel)") },
                )
                Text("Planlanan zaman: ${formatDateTime(dueAt)}")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            val calendar = Calendar.getInstance().apply { timeInMillis = dueAt }
                            DatePickerDialog(
                                context,
                                { _, year, month, day ->
                                    calendar.set(Calendar.YEAR, year)
                                    calendar.set(Calendar.MONTH, month)
                                    calendar.set(Calendar.DAY_OF_MONTH, day)
                                    dueAt = calendar.timeInMillis
                                },
                                calendar.get(Calendar.YEAR),
                                calendar.get(Calendar.MONTH),
                                calendar.get(Calendar.DAY_OF_MONTH),
                            ).show()
                        },
                    ) { Text("Tarih") }
                    OutlinedButton(
                        onClick = {
                            val calendar = Calendar.getInstance().apply { timeInMillis = dueAt }
                            TimePickerDialog(
                                context,
                                { _, hour, minute ->
                                    calendar.set(Calendar.HOUR_OF_DAY, hour)
                                    calendar.set(Calendar.MINUTE, minute)
                                    dueAt = calendar.timeInMillis
                                },
                                calendar.get(Calendar.HOUR_OF_DAY),
                                calendar.get(Calendar.MINUTE),
                                true,
                            ).show()
                        },
                    ) { Text("Saat") }
                }
                Button(
                    onClick = {
                        onCreateNextAction(actionType, dueAt, actionNote.takeIf { it.isNotBlank() })
                        actionNote = ""
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Takip planla")
                }
            }
        }

        message?.let {
            Card(modifier = Modifier.fillMaxWidth()) {
                Text(it, modifier = Modifier.padding(16.dp))
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Açık takipler", style = MaterialTheme.typography.titleMedium)
                val openActions = nextActions.filter { it.completedAtEpochMs == null }
                if (openActions.isEmpty()) {
                    Text("Açık takip yok.", style = MaterialTheme.typography.bodySmall)
                } else {
                    openActions.forEach { action ->
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(nextActionLabel(action.type))
                                Text(formatDateTime(action.dueAtEpochMs))
                                action.note?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                                Button(
                                    onClick = { onCompleteNextAction(action.id) },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text("Takibi tamamla")
                                }
                            }
                        }
                    }
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Aktivite geçmişi", style = MaterialTheme.typography.titleMedium)
                if (activities.isEmpty()) {
                    Text("Henüz aktivite kaydı yok.", style = MaterialTheme.typography.bodySmall)
                } else {
                    activities.forEach { activity ->
                        Text(
                            "• ${activityLabel(activity.type)} — ${formatDateTime(activity.occurredAtEpochMs)}" +
                                (activity.note?.let { " — ${it}" } ?: ""),
                        )
                    }
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Aşama geçmişi", style = MaterialTheme.typography.titleMedium)
                if (transitions.isEmpty()) {
                    Text("Aşama geçmişi yok.", style = MaterialTheme.typography.bodySmall)
                } else {
                    transitions.forEach { transition ->
                        Text(
                            "• ${transition.from?.let(::stageLabel) ?: "Yeni kayıt"} → " +
                                "${stageLabel(transition.to)} — ${formatDateTime(transition.changedAtEpochMs)}",
                        )
                    }
                }
            }
        }
    }
}

private fun parseOpportunityAmountMinor(raw: String): Long? {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return null
    val normalized = when {
        trimmed.contains(",") && trimmed.contains(".") -> trimmed.replace(".", "").replace(",", ".")
        trimmed.contains(",") -> trimmed.replace(",", ".")
        else -> trimmed
    }
    return normalized.toBigDecimalOrNull()
        ?.setScale(2, RoundingMode.HALF_UP)
        ?.movePointRight(2)
        ?.longValueExact()
}

private fun formatOpportunityValue(minor: Long, currency: String): String =
    BigDecimal.valueOf(minor, 2).setScale(2, RoundingMode.HALF_UP).toPlainString() + " " + currency

private fun opportunityStatusLabel(status: CrmOpportunityStatus): String = when (status) {
    CrmOpportunityStatus.OPEN -> "Açık"
    CrmOpportunityStatus.WON -> "Kazanıldı"
    CrmOpportunityStatus.LOST -> "Kayıp"
}

private fun defaultTomorrowNine(): Long =
    Calendar.getInstance().apply {
        add(Calendar.DAY_OF_YEAR, 1)
        set(Calendar.HOUR_OF_DAY, 9)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

private fun formatDateTime(epochMs: Long): String =
    SimpleDateFormat("dd.MM.yyyy HH:mm", Locale("tr", "TR")).format(Date(epochMs))

private fun stageLabel(stage: CrmStage): String = when (stage) {
    CrmStage.PROSPECT -> "Aday"
    CrmStage.VISIT -> "Ziyaret"
    CrmStage.MEETING -> "Görüşme"
    CrmStage.PROPOSAL -> "Teklif"
    CrmStage.SAMPLE -> "Numune"
    CrmStage.ORDER -> "Sipariş"
    CrmStage.ACTIVE_CUSTOMER -> "Aktif müşteri"
    CrmStage.LOST -> "Kayıp"
}

private fun activityLabel(type: CrmActivityType): String = when (type) {
    CrmActivityType.VISIT -> "Ziyaret"
    CrmActivityType.CALL -> "Arama"
    CrmActivityType.MEETING -> "Görüşme"
    CrmActivityType.SAMPLE -> "Numune"
    CrmActivityType.PROPOSAL -> "Teklif"
    CrmActivityType.ORDER -> "Sipariş"
    CrmActivityType.NOTE -> "Not"
}

private fun nextActionLabel(type: CrmNextActionType): String = when (type) {
    CrmNextActionType.CALL -> "Arama"
    CrmNextActionType.VISIT -> "Ziyaret"
    CrmNextActionType.MEETING -> "Görüşme"
    CrmNextActionType.SAMPLE_FOLLOW_UP -> "Numune takibi"
    CrmNextActionType.PROPOSAL_FOLLOW_UP -> "Teklif takibi"
    CrmNextActionType.ORDER_FOLLOW_UP -> "Sipariş takibi"
    CrmNextActionType.NOTE -> "Not"
}

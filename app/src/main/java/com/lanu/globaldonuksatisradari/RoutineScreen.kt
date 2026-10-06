package com.lanu.globaldonuksatisradari

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.lanu.globaldonuksatisradari.crm.CrmCustomer
import com.lanu.globaldonuksatisradari.crm.CrmRoutePlanner
import com.lanu.globaldonuksatisradari.crm.CrmStage
import com.lanu.globaldonuksatisradari.crm.MonthlyRoutinePlan
import com.lanu.globaldonuksatisradari.crm.MonthlyRoutinePlanner
import com.lanu.globaldonuksatisradari.crm.RoutineDayPlan
import com.lanu.globaldonuksatisradari.crm.RouteStop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private sealed interface RoutineComputation {
    data object Loading : RoutineComputation
    data class Ready(
        val route: List<RouteStop>,
        val automaticPlan: MonthlyRoutinePlan,
        val manualPlan: MonthlyRoutinePlan?,
    ) : RoutineComputation
}

private val ROUTINE_LIMIT_OPTIONS = listOf(50, 100, 250, 500, 0)

@Composable
fun RoutineScreen(
    customers: List<CrmCustomer>,
    selectedCity: String,
    selectedDistrict: String,
) {
    var startId by remember(customers) { mutableStateOf<String?>(null) }
    var manualIntervalInput by rememberSaveable { mutableStateOf("") }
    var planLimit by rememberSaveable { mutableStateOf(100) }

    val scopedCustomers = remember(customers, selectedCity, selectedDistrict) {
        scopeCrmCustomers(customers, selectedCity, selectedDistrict)
    }
    val routable = remember(scopedCustomers) {
        scopedCustomers.filter(::isRoutineRoutable)
    }
    val planCustomers = remember(routable, planLimit) {
        selectRoutineCandidates(routable, planLimit)
    }

    LaunchedEffect(planCustomers, startId) {
        if (startId != null && planCustomers.none { it.id == startId }) {
            startId = null
        }
    }

    val manualIntervalDays = manualIntervalInput.trim().toIntOrNull()
        ?.takeIf { it in 1..365 }

    val computation by produceState<RoutineComputation>(
        initialValue = RoutineComputation.Loading,
        planCustomers,
        startId,
        manualIntervalDays,
    ) {
        value = RoutineComputation.Loading
        value = withContext(Dispatchers.Default) {
            val route = CrmRoutePlanner.plan(planCustomers, startId)
            val automatic = MonthlyRoutinePlanner.plan(planCustomers, startId)
            val manual = manualIntervalDays?.let { interval ->
                MonthlyRoutinePlanner.plan(
                    customers = planCustomers,
                    startCustomerId = startId,
                    manualIntervalDays = interval,
                )
            }
            RoutineComputation.Ready(route, automatic, manual)
        }
    }

    val ready = computation as? RoutineComputation.Ready
    val route = ready?.route.orEmpty()
    val automaticPlan = ready?.automaticPlan
    val manualPlan = ready?.manualPlan
    val missingCoordinates = scopedCustomers.size - routable.size
    val excludedByCapacity = (routable.size - planCustomers.size).coerceAtLeast(0)
    val startName = route.firstOrNull()?.customer?.businessName ?: "Otomatik başlangıç"

    LazyColumn(
        modifier = Modifier
            .testTag("routine_screen")
            .padding(horizontal = 16.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("Saha Rutini", style = MaterialTheme.typography.headlineSmall)
            Text(
                "CRM noktalarını yakınlığa göre planlayın. Büyük müşteri havuzlarında hesaplama arka planda yapılır; ekran geçişi beklemez.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("Plan kapsamı", style = MaterialTheme.typography.titleMedium)
                    Text("$selectedCity / $selectedDistrict")
                    Text(
                        "${scopedCustomers.size} CRM kaydı • ${routable.size} koordinatlı • $missingCoordinates koordinatsız",
                    )
                    Text(
                        if (planLimit == 0) {
                            "Çalışma seti: tüm ${planCustomers.size} nokta"
                        } else {
                            "Çalışma seti: ${planCustomers.size} nokta" +
                                if (excludedByCapacity > 0) " • $excludedByCapacity nokta sonraki planlama için havuzda" else ""
                        },
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ROUTINE_LIMIT_OPTIONS.forEach { option ->
                            FilterChip(
                                selected = planLimit == option,
                                onClick = { planLimit = option },
                                label = { Text(if (option == 0) "Tümü" else option.toString()) },
                            )
                        }
                    }
                    if (planLimit == 0 && routable.size > 500) {
                        Text(
                            "Tüm noktalar seçildi. Hesaplama arka planda devam eder; çok büyük havuzlarda birkaç saniye sürebilir.",
                            color = MaterialTheme.colorScheme.tertiary,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Text("Başlangıç: $startName")
                    if (startId != null) {
                        OutlinedButton(
                            onClick = { startId = null },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Başlangıcı otomatiğe al")
                        }
                    }
                }
            }
        }

        if (computation is RoutineComputation.Loading) {
            item {
                Card(Modifier.fillMaxWidth().testTag("routine_loading")) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        CircularProgressIndicator()
                        Column {
                            Text("Rota hazırlanıyor", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "${planCustomers.size} nokta arka planda hesaplanıyor. Uygulama kullanılabilir durumda.",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
        } else {
            automaticPlan?.let { plan ->
                item {
                    RoutinePlanSummaryCard(
                        title = "Otomatik Aylık Ziyaret Planı",
                        plan = plan,
                        detail = "Aktif müşteri/Sipariş: 7 gün • Teklif/Numune/Görüşme/Ziyaret: 14 gün • Aday: 28 gün • Kayıp: otomatik plan dışında",
                        exportLabel = "Otomatik",
                    )
                }
            }

            item {
                Card(modifier = Modifier.fillMaxWidth().testTag("manual_repeat_plan")) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("Manuel Tekrar Planı", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "1-365 gün arası tekrar aralığı girin. Otomatik plan değişmez; ayrı önizleme oluşturulur.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        OutlinedTextField(
                            value = manualIntervalInput,
                            onValueChange = { value ->
                                manualIntervalInput = value.filter(Char::isDigit).take(3)
                            },
                            modifier = Modifier.fillMaxWidth().testTag("manual_interval_days"),
                            label = { Text("Tekrar aralığı (gün)") },
                            singleLine = true,
                        )
                        if (manualIntervalInput.isNotBlank() && manualIntervalDays == null) {
                            Text(
                                "Geçerli aralık 1-365 gündür.",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        manualPlan?.let { plan ->
                            Text(
                                "${plan.uniquePointCount} benzersiz nokta • ${plan.totalPointCount} aylık ziyaret • " +
                                    "%.2f".format(plan.totalDistanceKm) + " km • " +
                                    formatMinutes(plan.totalEstimatedMinutes) + " tahmini yol",
                            )
                            RoutineExportActions(plan, planLabel = "Manuel-${manualIntervalDays}Gun")
                        }
                    }
                }
            }

            automaticPlan?.let { plan ->
                item {
                    Text("Otomatik plan • 4 haftalık dağılım", style = MaterialTheme.typography.titleMedium)
                }
                items((1..MonthlyRoutinePlanner.WEEKS).toList(), key = { "auto-week-$it" }) { week ->
                    WeekPlanCard(
                        week = week,
                        days = plan.days.filter { it.weekNumber == week },
                    )
                }
            }

            manualPlan?.let { plan ->
                item {
                    Text(
                        "Manuel $manualIntervalDays günlük plan • 4 haftalık dağılım",
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                items((1..MonthlyRoutinePlanner.WEEKS).toList(), key = { "manual-week-$it" }) { week ->
                    WeekPlanCard(
                        week = week,
                        days = plan.days.filter { it.weekNumber == week },
                    )
                }
            }

            if (route.size >= 2) {
                item {
                    Text("Başlangıç noktası seçimi", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Aşağıdan başlangıç seçildiğinde rota arka planda yeniden hesaplanır.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            items(route, key = { it.customer.id }) { stop ->
                RouteStopCard(stop = stop, onChooseStart = { startId = stop.customer.id })
            }

            if (route.isNotEmpty()) {
                item {
                    val last = route.last()
                    Text(
                        "Kümülatif rota: %.2f km • %s tahmini yol".format(
                            last.cumulativeDistanceKm,
                            formatMinutes(last.cumulativeEstimatedMinutes),
                        ),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        "Mesafe Haversine hesabıdır. Süre 1,25 yol sapması ve 30 km/s şehir hızıyla offline fallback tahminidir; canlı trafik değildir.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            } else {
                item {
                    Text("Rutin oluşturmak için en az bir müşterinin geçerli X/Y koordinatı olmalı.")
                }
            }
        }
    }
}

internal fun selectRoutineCandidates(
    customers: List<CrmCustomer>,
    limit: Int,
): List<CrmCustomer> {
    val ordered = customers.sortedWith(
        compareBy<CrmCustomer>(
            { routineStagePriority(it.stage) },
            { -it.updatedAtEpochMs },
            { it.businessName.lowercase() },
            { it.id },
        ),
    )
    return if (limit <= 0) ordered else ordered.take(limit)
}

private fun routineStagePriority(stage: CrmStage): Int = when (stage) {
    CrmStage.ORDER -> 0
    CrmStage.ACTIVE_CUSTOMER -> 1
    CrmStage.PROPOSAL -> 2
    CrmStage.SAMPLE -> 3
    CrmStage.MEETING -> 4
    CrmStage.VISIT -> 5
    CrmStage.PROSPECT -> 6
    CrmStage.LOST -> 7
}

private fun isRoutineRoutable(customer: CrmCustomer): Boolean {
    val latitude = customer.latitude
    val longitude = customer.longitude
    return latitude != null &&
        longitude != null &&
        latitude in -90.0..90.0 &&
        longitude in -180.0..180.0
}

@Composable
private fun RoutinePlanSummaryCard(
    title: String,
    plan: MonthlyRoutinePlan,
    detail: String,
    exportLabel: String,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                "4 hafta • 20 iş günü • ${plan.uniquePointCount} benzersiz nokta • " +
                    "${plan.totalPointCount} planlanan ziyaret",
            )
            Text(
                "Toplam günlük rota mesafeleri: %.2f km • tahmini yol süresi %s".format(
                    plan.totalDistanceKm,
                    formatMinutes(plan.totalEstimatedMinutes),
                ),
            )
            Text(detail, style = MaterialTheme.typography.bodySmall)
            RoutineExportActions(plan, planLabel = exportLabel)
        }
    }
}

@Composable
private fun RouteStopCard(
    stop: RouteStop,
    onChooseStart: () -> Unit,
) {
    val context = LocalContext.current
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("${stop.order}. ${stop.customer.businessName}", style = MaterialTheme.typography.titleMedium)
            Text(
                stop.customer.city + " / " + stop.customer.district +
                    (stop.customer.neighborhood?.let { " / $it" } ?: ""),
            )
            stop.customer.address?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            Text(
                "Önceki noktaya: %.2f km • %s tahmini".format(
                    stop.distanceFromPreviousKm,
                    formatMinutes(stop.estimatedMinutesFromPrevious),
                ),
            )
            Text(
                "Kümülatif: %.2f km • %s".format(
                    stop.cumulativeDistanceKm,
                    formatMinutes(stop.cumulativeEstimatedMinutes),
                ),
            )
            Text("X %.6f • Y %.6f".format(stop.customer.longitude, stop.customer.latitude))
            OutlinedButton(
                onClick = onChooseStart,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Rutine bu noktadan başla")
            }
            OutlinedButton(
                onClick = { openExternalNavigation(context, stop.customer) },
                modifier = Modifier.fillMaxWidth().testTag("route_navigate_${stop.customer.id}"),
            ) {
                Text("Navigasyonu aç")
            }
        }
    }
}

@Composable
private fun WeekPlanCard(
    week: Int,
    days: List<RoutineDayPlan>,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("$week. Hafta", style = MaterialTheme.typography.titleMedium)
            days.forEach { day ->
                val first = day.stops.firstOrNull()?.customer?.businessName
                val last = day.stops.lastOrNull()?.customer?.businessName
                Text(
                    "${day.weekday} • ${day.stops.size} ziyaret • %.2f km • %s".format(
                        day.totalDistanceKm,
                        formatMinutes(day.totalEstimatedMinutes),
                    ),
                )
                if (first != null) {
                    Text(
                        if (first == last) first else "$first → $last",
                        style = MaterialTheme.typography.bodySmall,
                    )
                } else {
                    Text("Planlanan ziyaret yok.", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

private fun formatMinutes(totalMinutes: Int): String {
    if (totalMinutes <= 0) return "0 dk"
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours == 0) {
        "$minutes dk"
    } else if (minutes == 0) {
        "$hours sa"
    } else {
        "$hours sa $minutes dk"
    }
}

internal fun openExternalNavigation(
    context: Context,
    customer: CrmCustomer,
) {
    val lat = customer.latitude ?: return
    val lon = customer.longitude ?: return
    val googleNavigation = Intent(
        Intent.ACTION_VIEW,
        Uri.parse("google.navigation:q=$lat,$lon&mode=d"),
    ).apply {
        setPackage("com.google.android.apps.maps")
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    try {
        context.startActivity(googleNavigation)
    } catch (_: ActivityNotFoundException) {
        val fallback = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://www.google.com/maps/dir/?api=1&destination=$lat,$lon"),
        ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        context.startActivity(fallback)
    }
}

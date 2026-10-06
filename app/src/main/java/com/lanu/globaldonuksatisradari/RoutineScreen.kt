package com.lanu.globaldonuksatisradari

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.lanu.globaldonuksatisradari.crm.CrmCustomer
import com.lanu.globaldonuksatisradari.crm.CrmRoutePlanner
import com.lanu.globaldonuksatisradari.crm.MonthlyRoutinePlan
import com.lanu.globaldonuksatisradari.crm.MonthlyRoutinePlanner
import com.lanu.globaldonuksatisradari.crm.RoutineDayPlan
import com.lanu.globaldonuksatisradari.crm.RouteStop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class RoutineComputation(
    val route: List<RouteStop>,
    val automaticPlan: MonthlyRoutinePlan,
)

@Composable
fun RoutineScreen(
    customers: List<CrmCustomer>,
    selectedCity: String,
    selectedDistrict: String,
) {
    var startId by remember(customers) { mutableStateOf<String?>(null) }
    var manualIntervalInput by remember { mutableStateOf("") }

    val scopedCustomers = remember(customers, selectedCity, selectedDistrict) {
        scopeCrmCustomers(customers, selectedCity, selectedDistrict)
    }
    val routable = remember(scopedCustomers) {
        scopedCustomers.filter { customer ->
            val latitude = customer.latitude
            val longitude = customer.longitude
            latitude != null &&
                longitude != null &&
                latitude in -90.0..90.0 &&
                longitude in -180.0..180.0
        }
    }
    var automaticComputation by remember(routable, startId) {
        mutableStateOf<RoutineComputation?>(null)
    }
    LaunchedEffect(routable, startId) {
        automaticComputation = withContext(Dispatchers.Default) {
            RoutineComputation(
                route = CrmRoutePlanner.plan(routable, startId),
                automaticPlan = MonthlyRoutinePlanner.plan(routable, startId),
            )
        }
    }
    val route = automaticComputation?.route.orEmpty()
    val automaticPlan = automaticComputation?.automaticPlan

    val manualIntervalDays = manualIntervalInput.trim().toIntOrNull()
        ?.takeIf { it in 1..365 }
    var manualPlan by remember(routable, startId, manualIntervalDays) {
        mutableStateOf<MonthlyRoutinePlan?>(null)
    }
    LaunchedEffect(routable, startId, manualIntervalDays) {
        manualPlan = if (manualIntervalDays == null) {
            null
        } else {
            withContext(Dispatchers.Default) {
                MonthlyRoutinePlanner.plan(
                    customers = routable,
                    startCustomerId = startId,
                    manualIntervalDays = manualIntervalDays,
                )
            }
        }
    }

    val missingCoordinates = scopedCustomers.size - routable.size
    val planning = automaticComputation == null && routable.isNotEmpty()
    val startName = when {
        planning -> "Plan hazırlanıyor…"
        route.isNotEmpty() -> route.first().customer.businessName
        else -> "Otomatik başlangıç"
    }

    LazyColumn(
        modifier = Modifier
            .testTag("routine_screen")
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text("Yakınlık Bazlı Rutin", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Noktalar yakınlığa göre günlük rotalara kümelenir. Otomatik plan CRM aşamasından ziyaret sıklığı üretir; manuel gün aralığı girildiğinde ayrıca tekrar planı oluşturulur.",
            )
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text("Alan: " + selectedCity + " / " + selectedDistrict)
                    Text(
                        "Rota noktası: " + routable.size +
                            " • Eksik/geçersiz koordinat: " + missingCoordinates,
                    )
                    Text("Başlangıç: " + startName)
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

        item {
            if (automaticPlan == null) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("routine_planning"),
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text("Rutin hazırlanıyor", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Büyük CRM listesi arka planda hesaplanıyor; uygulama ve sekmeler kullanılmaya devam edebilir.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            } else {
                RoutinePlanSummaryCard(
                    title = "Otomatik Aylık Ziyaret Planı",
                    plan = automaticPlan,
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
                        "Aynı kapsamdaki noktalar için 1-365 gün arasında tekrar aralığı girin. Bu plan otomatik frekansı değiştirmez.",
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
                            plan.uniquePointCount.toString() + " benzersiz nokta • " +
                                plan.totalPointCount + " aylık ziyaret • " +
                                "%.2f".format(plan.totalDistanceKm) + " km • " +
                                formatMinutes(plan.totalEstimatedMinutes) + " tahmini yol",
                        )
                        RoutineExportActions(
                            plan,
                            planLabel = "Manuel-" + manualIntervalDays + "Gun",
                        )
                    }
                }
            }
        }

        item {
            Text("Otomatik plan • 4 haftalık dağılım", style = MaterialTheme.typography.titleMedium)
        }
        automaticPlan?.let { plan ->
            items((1..MonthlyRoutinePlanner.WEEKS).toList(), key = { "auto-week-" + it }) { week ->
                WeekPlanCard(
                    week = week,
                    days = plan.days.filter { it.weekNumber == week },
                )
            }
        }

        manualPlan?.let { plan ->
            item {
                Text(
                    "Manuel " + manualIntervalDays + " günlük plan • 4 haftalık dağılım",
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            items((1..MonthlyRoutinePlanner.WEEKS).toList(), key = { "manual-week-" + it }) { week ->
                WeekPlanCard(
                    week = week,
                    days = plan.days.filter { it.weekNumber == week },
                )
            }
        }

        if (routable.size >= 2) {
            item {
                Text("Başlangıç noktası seçimi", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Aşağıdaki listeden başlangıç seçildiğinde tek rota, otomatik aylık plan ve manuel tekrar planı birlikte yeniden hesaplanır.",
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
                    "Kümülatif rota: " + "%.2f".format(last.cumulativeDistanceKm) + " km • " +
                        formatMinutes(last.cumulativeEstimatedMinutes) + " tahmini yol",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    "Mesafe koordinatlar arası Haversine hesabıdır. Süre; yol sapması için 1,25 katsayı ve 30 km/s ortalama şehir hızıyla hesaplanan offline fallback tahminidir; canlı trafik değildir.",
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
                "4 hafta • 20 iş günü • " + plan.uniquePointCount +
                    " benzersiz nokta • " + plan.totalPointCount + " planlanan ziyaret",
            )
            Text(
                "Toplam günlük rota mesafeleri: " + "%.2f".format(plan.totalDistanceKm) +
                    " km • tahmini yol süresi " + formatMinutes(plan.totalEstimatedMinutes),
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
            Text(
                stop.order.toString() + ". " + stop.customer.businessName,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                stop.customer.city + " / " + stop.customer.district +
                    (stop.customer.neighborhood?.let { " / " + it } ?: ""),
            )
            stop.customer.address?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            Text(
                "Önceki noktaya: " + "%.2f".format(stop.distanceFromPreviousKm) +
                    " km • " + formatMinutes(stop.estimatedMinutesFromPrevious) + " tahmini",
            )
            Text(
                "Kümülatif: " + "%.2f".format(stop.cumulativeDistanceKm) +
                    " km • " + formatMinutes(stop.cumulativeEstimatedMinutes),
            )
            Text(
                "X " + "%.6f".format(stop.customer.longitude) +
                    " • Y " + "%.6f".format(stop.customer.latitude),
            )
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
            Text(week.toString() + ". Hafta", style = MaterialTheme.typography.titleMedium)
            days.forEach { day ->
                val first = day.stops.firstOrNull()?.customer?.businessName
                val last = day.stops.lastOrNull()?.customer?.businessName
                Text(
                    day.weekday + " • " + day.stops.size + " ziyaret • " +
                        "%.2f".format(day.totalDistanceKm) + " km • " +
                        formatMinutes(day.totalEstimatedMinutes),
                )
                if (first != null) {
                    Text(
                        if (first == last) first else first + " → " + last,
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
        minutes.toString() + " dk"
    } else if (minutes == 0) {
        hours.toString() + " sa"
    } else {
        hours.toString() + " sa " + minutes + " dk"
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

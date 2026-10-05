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
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.lanu.globaldonuksatisradari.crm.CrmCustomer
import com.lanu.globaldonuksatisradari.crm.CrmStage
import com.lanu.globaldonuksatisradari.data.DataSourceDescriptor
import com.lanu.globaldonuksatisradari.data.VerifiedBusiness

private enum class MapLayer { CRM, RADAR, BOTH }

private val CRM_MAP_SOURCE = DataSourceDescriptor(
    id = "local-crm-map",
    name = "Yerel CRM",
    publisher = "LANU",
    licenseOrTerms = "Kullanıcı tarafından kaydedilmiş CRM verisi",
    sourceUrl = "https://github.com/kanunal99-jpg/LANU-Global-Donuk-Sat-Radar-",
    lastVerifiedAtEpochMs = 1L,
)

@Composable
fun CrmMapScreen(
    customers: List<CrmCustomer>,
    radarBusinesses: List<VerifiedBusiness>,
    selectedCity: String,
    selectedDistrict: String,
    onOpenCustomer: (String) -> Unit,
) {
    var layer by remember { mutableStateOf(MapLayer.CRM) }
    var stageFilter by remember { mutableStateOf<CrmStage?>(null) }

    val scopedCustomers = remember(customers, selectedCity, selectedDistrict) {
        scopeCrmCustomers(customers, selectedCity, selectedDistrict)
    }
    val stageScopedCustomers = remember(scopedCustomers, stageFilter) {
        scopedCustomers.filter { stageFilter == null || it.stage == stageFilter }
    }
    val routableCustomers = remember(stageScopedCustomers) {
        stageScopedCustomers.filter(::hasValidCoordinates)
    }
    val missingCoordinates = stageScopedCustomers.size - routableCustomers.size
    val crmMapBusinesses = remember(routableCustomers) {
        routableCustomers.map(::crmCustomerAsMapBusiness)
    }

    val scopedRadar = remember(radarBusinesses, selectedCity, selectedDistrict) {
        radarBusinesses.filter { business ->
            business.city.equals(selectedCity, ignoreCase = true) &&
                (selectedDistrict == "Tümü" || business.district.equals(selectedDistrict, ignoreCase = true)) &&
                business.latitude != null && business.longitude != null &&
                business.latitude in -90.0..90.0 && business.longitude in -180.0..180.0
        }
    }

    val mapBusinesses = remember(layer, crmMapBusinesses, scopedRadar) {
        when (layer) {
            MapLayer.CRM -> crmMapBusinesses
            MapLayer.RADAR -> scopedRadar
            MapLayer.BOTH -> crmMapBusinesses + scopedRadar
        }
    }

    LazyColumn(
        modifier = Modifier
            .testTag("crm_map_screen")
            .padding(horizontal = 16.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("CRM Haritası", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Kaydedilen müşteri noktalarının coğrafi dağılımı. Radar sonuçlarını ayrı katman olarak açabilirsiniz.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        item {
            Card(Modifier.fillMaxWidth()) {
                Column(
                    Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text("$selectedCity / $selectedDistrict", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "${stageScopedCustomers.size} CRM kaydı • ${routableCustomers.size} haritalanabilir • $missingCoordinates koordinatsız",
                        modifier = Modifier.testTag("crm_map_count"),
                    )
                    Text(
                        "Radar katmanı: ${scopedRadar.size} mevcut arama sonucu",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = layer == MapLayer.CRM,
                    onClick = { layer = MapLayer.CRM },
                    label = { Text("CRM") },
                )
                FilterChip(
                    selected = layer == MapLayer.RADAR,
                    onClick = { layer = MapLayer.RADAR },
                    label = { Text("Radar") },
                )
                FilterChip(
                    selected = layer == MapLayer.BOTH,
                    onClick = { layer = MapLayer.BOTH },
                    label = { Text("Birlikte") },
                )
            }
        }

        if (layer != MapLayer.RADAR) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = stageFilter == null,
                        onClick = { stageFilter = null },
                        label = { Text("Tüm aşamalar") },
                    )
                    CrmStage.entries.forEach { stage ->
                        FilterChip(
                            selected = stageFilter == stage,
                            onClick = { stageFilter = stage },
                            label = {
                                Text(
                                    "${stageLabelForMap(stage)} (" +
                                        scopedCustomers.count { it.stage == stage } + ")",
                                )
                            },
                        )
                    }
                }
            }
        }

        item {
            if (mapBusinesses.isEmpty()) {
                Card(Modifier.fillMaxWidth()) {
                    Text(
                        "Bu kapsam ve filtrelerde haritada gösterilebilecek koordinatlı nokta yok.",
                        Modifier.padding(16.dp),
                    )
                }
            } else {
                BusinessMapPreview(
                    businesses = mapBusinesses.take(MAX_MAP_POINTS),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("crm_map_preview"),
                )
                if (mapBusinesses.size > MAX_MAP_POINTS) {
                    Text(
                        "Performans için haritada ilk $MAX_MAP_POINTS nokta gösteriliyor. İlçe/aşama filtresiyle alanı daraltın.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }

        if (layer != MapLayer.RADAR && stageScopedCustomers.isNotEmpty()) {
            item {
                Text("CRM noktaları", style = MaterialTheme.typography.titleMedium)
            }
            items(stageScopedCustomers, key = { it.id }) { customer ->
                Card(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(customer.businessName, style = MaterialTheme.typography.titleMedium)
                        Text(
                            "${customer.city} • ${customer.district}" +
                                (customer.neighborhood?.let { " • $it" } ?: ""),
                        )
                        Text(
                            "Aşama: ${stageLabelForMap(customer.stage)}" +
                                if (hasValidCoordinates(customer)) "" else " • Koordinat eksik",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        OutlinedButton(
                            onClick = { onOpenCustomer(customer.id) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("crm_map_open_${customer.id}"),
                        ) {
                            Text("CRM kaydını aç")
                        }
                    }
                }
            }
        }
    }
}

internal fun hasValidCoordinates(customer: CrmCustomer): Boolean {
    val lat = customer.latitude
    val lon = customer.longitude
    return lat != null && lon != null && lat in -90.0..90.0 && lon in -180.0..180.0
}

internal fun crmCustomerAsMapBusiness(customer: CrmCustomer): VerifiedBusiness = VerifiedBusiness(
    id = "crm-map:${customer.id}",
    name = customer.signboardName?.takeIf(String::isNotBlank) ?: customer.businessName,
    city = customer.city,
    district = customer.district,
    neighborhood = customer.neighborhood,
    address = customer.address,
    source = CRM_MAP_SOURCE,
    verifiedAtEpochMs = customer.updatedAtEpochMs,
    latitude = customer.latitude,
    longitude = customer.longitude,
    phone = customer.phone,
    website = customer.website,
    category = "CRM • ${stageLabelForMap(customer.stage)}",
)

internal fun stageLabelForMap(stage: CrmStage): String = when (stage) {
    CrmStage.PROSPECT -> "Potansiyel"
    CrmStage.VISIT -> "Ziyaret"
    CrmStage.MEETING -> "Görüşme"
    CrmStage.PROPOSAL -> "Teklif"
    CrmStage.SAMPLE -> "Numune"
    CrmStage.ORDER -> "Sipariş"
    CrmStage.ACTIVE_CUSTOMER -> "Aktif müşteri"
    CrmStage.LOST -> "Kaybedildi"
}

private const val MAX_MAP_POINTS = 750

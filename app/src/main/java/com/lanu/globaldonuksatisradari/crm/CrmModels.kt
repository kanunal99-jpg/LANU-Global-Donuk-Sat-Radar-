package com.lanu.globaldonuksatisradari.crm

/** CRM pipeline is explicit so dashboard values can be derived from persisted state later. */
enum class DataQuality {
    OBSERVED,
    ESTIMATED,
    USER_ENTERED,
    UNKNOWN,
}

enum class CrmStage {
    PROSPECT,
    VISIT,
    MEETING,
    PROPOSAL,
    SAMPLE,
    ORDER,
    ACTIVE_CUSTOMER,
    LOST,
}

enum class CrmActivityType {
    VISIT,
    CALL,
    MEETING,
    SAMPLE,
    PROPOSAL,
    ORDER,
    NOTE,
}

enum class CrmNextActionType {
    CALL,
    VISIT,
    MEETING,
    SAMPLE_FOLLOW_UP,
    PROPOSAL_FOLLOW_UP,
    ORDER_FOLLOW_UP,
    NOTE,
}

enum class SyncState {
    LOCAL_ONLY,
    PENDING_UPLOAD,
    SYNCED,
    CONFLICT,
    FAILED,
}

enum class SyncOperationState {
    PENDING,
    CONFLICT,
    FAILED,
}

enum class CrmRegistryStatus {
    ACTIVE,
    INACTIVE,
    UNVERIFIED,
}

data class CrmCustomer(
    val id: String,
    val businessSourceId: String,
    val businessName: String,
    val signboardName: String? = null,
    val city: String,
    val district: String,
    val neighborhood: String?,
    val address: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val dataQuality: DataQuality = DataQuality.UNKNOWN,
    val stage: CrmStage = CrmStage.PROSPECT,
    val ownerUserId: String? = null,
    val notes: String? = null,
    val contactName: String? = null,
    val businessType: String? = null,
    val taxOrNationalId: String? = null,
    val phone: String? = null,
    val website: String? = null,
    val registryStatus: CrmRegistryStatus = CrmRegistryStatus.UNVERIFIED,
    val registrySource: String? = null,
    val registryNumber: String? = null,
    val tags: Set<String> = emptySet(),
    val mergedIntoCustomerId: String? = null,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
    val version: Long = 0L,
    val syncState: SyncState = SyncState.LOCAL_ONLY,
)

object CrmTagCodec {
    private const val SEPARATOR = "|"

    fun normalize(tags: Collection<String>): Set<String> =
        tags.asSequence()
            .map { it.trim().replace(Regex("\\s+"), " ") }
            .map { it.replace(SEPARATOR, " ") }
            .filter { it.isNotBlank() }
            .map { it.take(40) }
            .distinctBy { it.lowercase(java.util.Locale.ROOT) }
            .sortedWith(String.CASE_INSENSITIVE_ORDER)
            .take(20)
            .toCollection(linkedSetOf())

    fun encode(tags: Collection<String>): String =
        normalize(tags).joinToString(SEPARATOR)

    fun decode(value: String?): Set<String> =
        if (value.isNullOrBlank()) emptySet()
        else normalize(value.split(SEPARATOR))
}

data class CrmMergeResult(
    val targetCustomerId: String,
    val sourceCustomerId: String,
    val movedActivities: Int,
    val movedNextActions: Int,
    val movedOpportunities: Int,
    val movedContacts: Int,
    val movedQuotes: Int,
    val movedOrders: Int,
)

data class BulkCrmSaveResult(
    val inserted: Int,
    val alreadyExisting: Int,
) {
    val total: Int get() = inserted + alreadyExisting
}

data class CrmActivity(
    val id: String,
    val customerId: String,
    val type: CrmActivityType,
    val occurredAtEpochMs: Long,
    val note: String? = null,
    val createdByUserId: String? = null,
    val createdAtEpochMs: Long,
    val version: Long = 0L,
    val syncState: SyncState = SyncState.LOCAL_ONLY,
)

data class CrmNextAction(
    val id: String,
    val customerId: String,
    val type: CrmNextActionType,
    val dueAtEpochMs: Long,
    val note: String? = null,
    val createdByUserId: String? = null,
    val createdAtEpochMs: Long,
    val completedAtEpochMs: Long? = null,
    val completedByUserId: String? = null,
    val version: Long = 1L,
    val syncState: SyncState = SyncState.LOCAL_ONLY,
)

data class CrmStageTransition(
    val id: String,
    val customerId: String,
    val from: CrmStage?,
    val to: CrmStage,
    val changedAtEpochMs: Long,
    val changedByUserId: String? = null,
    val clientVersion: Long,
)

data class SyncOperation(
    val id: String,
    val entityType: String,
    val entityId: String,
    val operation: String,
    val payloadVersion: Long,
    val createdAtEpochMs: Long,
    val attemptCount: Int = 0,
    val lastError: String? = null,
    val state: SyncOperationState = SyncOperationState.PENDING,
)

/** Centralized stage-transition rules prevent UI and dashboard from inventing pipeline states. */
object CrmStageRules {
    fun canTransition(from: CrmStage, to: CrmStage): Boolean = when (from) {
        CrmStage.PROSPECT -> to in setOf(CrmStage.VISIT, CrmStage.MEETING, CrmStage.LOST)
        CrmStage.VISIT -> to in setOf(CrmStage.MEETING, CrmStage.PROPOSAL, CrmStage.LOST)
        CrmStage.MEETING -> to in setOf(CrmStage.PROPOSAL, CrmStage.SAMPLE, CrmStage.LOST)
        CrmStage.PROPOSAL -> to in setOf(CrmStage.SAMPLE, CrmStage.ORDER, CrmStage.LOST)
        CrmStage.SAMPLE -> to in setOf(CrmStage.PROPOSAL, CrmStage.ORDER, CrmStage.LOST)
        CrmStage.ORDER -> to in setOf(CrmStage.ACTIVE_CUSTOMER, CrmStage.LOST)
        CrmStage.ACTIVE_CUSTOMER -> to == CrmStage.LOST
        CrmStage.LOST -> to == CrmStage.PROSPECT
    }
}


data class RouteStop(
    val customer: CrmCustomer,
    val order: Int,
    val distanceFromPreviousKm: Double,
    val cumulativeDistanceKm: Double,
    val estimatedMinutesFromPrevious: Int = 0,
    val cumulativeEstimatedMinutes: Int = 0,
)

object CrmRoutePlanner {
    const val EXACT_NEAREST_NEIGHBOR_LIMIT = 96

    fun plan(
        customers: List<CrmCustomer>,
        startCustomerId: String? = null,
    ): List<RouteStop> {
        val candidates = customers.filter(::isRoutable)
        if (candidates.isEmpty()) return emptyList()

        val ordered = if (candidates.size <= EXACT_NEAREST_NEIGHBOR_LIMIT) {
            exactNearestNeighborOrder(candidates, startCustomerId)
        } else {
            scalableSweepOrder(candidates, startCustomerId)
        }
        return toRouteStops(ordered)
    }

    fun usesScalableFallback(customerCount: Int): Boolean =
        customerCount > EXACT_NEAREST_NEIGHBOR_LIMIT

    private fun exactNearestNeighborOrder(
        candidates: List<CrmCustomer>,
        startCustomerId: String?,
    ): List<CrmCustomer> {
        val remaining = candidates.toMutableList()
        val ordered = ArrayList<CrmCustomer>(remaining.size)
        var current = startCustomerId?.let { id -> remaining.firstOrNull { it.id == id } }
            ?: remaining.minWithOrNull(
                compareBy<CrmCustomer>(
                    { it.latitude },
                    { it.longitude },
                    { it.businessName },
                    { it.id },
                ),
            )
            ?: return emptyList()

        ordered += current
        remaining.remove(current)

        while (remaining.isNotEmpty()) {
            val next = remaining.minByOrNull { candidate ->
                distanceKm(
                    current.latitude!!,
                    current.longitude!!,
                    candidate.latitude!!,
                    candidate.longitude!!,
                )
            } ?: break
            ordered += next
            remaining.remove(next)
            current = next
        }
        return ordered
    }

    /**
     * Large-list fallback: deterministic latitude bands with alternating longitude direction.
     * Complexity is O(n log n), so thousands of CRM points do not block screen navigation.
     */
    private fun scalableSweepOrder(
        candidates: List<CrmCustomer>,
        startCustomerId: String?,
    ): List<CrmCustomer> {
        val minLat = candidates.minOf { it.latitude!! }
        val maxLat = candidates.maxOf { it.latitude!! }
        val bandCount = kotlin.math.sqrt(candidates.size.toDouble())
            .toInt()
            .coerceIn(8, 64)
        val span = (maxLat - minLat).coerceAtLeast(0.000001)

        fun band(customer: CrmCustomer): Int =
            (((customer.latitude!! - minLat) / span) * bandCount)
                .toInt()
                .coerceIn(0, bandCount - 1)

        val ordered = candidates.sortedWith { a, b ->
            val aBand = band(a)
            val bBand = band(b)
            if (aBand != bBand) {
                aBand.compareTo(bBand)
            } else {
                val lonCompare = a.longitude!!.compareTo(b.longitude!!)
                val directional = if (aBand % 2 == 0) lonCompare else -lonCompare
                if (directional != 0) directional else a.id.compareTo(b.id)
            }
        }.toMutableList()

        val startIndex = startCustomerId
            ?.let { id -> ordered.indexOfFirst { it.id == id } }
            ?.takeIf { it >= 0 }
            ?: 0
        if (startIndex > 0) {
            val rotated = ArrayList<CrmCustomer>(ordered.size)
            rotated.addAll(ordered.subList(startIndex, ordered.size))
            rotated.addAll(ordered.subList(0, startIndex))
            return rotated
        }
        return ordered
    }

    private fun toRouteStops(ordered: List<CrmCustomer>): List<RouteStop> {
        var total = 0.0
        var totalMinutes = 0
        return ordered.mapIndexed { index, customer ->
            val previous = ordered.getOrNull(index - 1)
            val segment = if (previous == null) {
                0.0
            } else {
                distanceKm(
                    previous.latitude!!,
                    previous.longitude!!,
                    customer.latitude!!,
                    customer.longitude!!,
                )
            }
            total += segment
            val segmentMinutes = RouteTimeEstimator.estimatedTravelMinutes(segment)
            totalMinutes += segmentMinutes
            RouteStop(
                customer = customer,
                order = index + 1,
                distanceFromPreviousKm = segment,
                cumulativeDistanceKm = total,
                estimatedMinutesFromPrevious = segmentMinutes,
                cumulativeEstimatedMinutes = totalMinutes,
            )
        }
    }

    private fun isRoutable(customer: CrmCustomer): Boolean =
        customer.latitude != null &&
            customer.longitude != null &&
            customer.latitude in -90.0..90.0 &&
            customer.longitude in -180.0..180.0

    fun distanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val earthRadiusKm = 6371.0088
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = kotlin.math.sin(dLat / 2) * kotlin.math.sin(dLat / 2) +
            kotlin.math.cos(Math.toRadians(lat1)) *
            kotlin.math.cos(Math.toRadians(lat2)) *
            kotlin.math.sin(dLon / 2) * kotlin.math.sin(dLon / 2)
        return 2 * earthRadiusKm * kotlin.math.asin(kotlin.math.sqrt(a))
    }
}


object RouteTimeEstimator {
    private const val ROAD_DISTANCE_FACTOR = 1.25
    private const val ASSUMED_AVERAGE_SPEED_KMH = 30.0

    /**
     * Offline-safe travel-time fallback. It converts straight-line distance to a conservative
     * urban road-distance estimate and never claims live traffic accuracy.
     */
    fun estimatedTravelMinutes(straightLineDistanceKm: Double): Int {
        if (straightLineDistanceKm <= 0.0) return 0
        val estimatedRoadKm = straightLineDistanceKm * ROAD_DISTANCE_FACTOR
        return kotlin.math.ceil(
            estimatedRoadKm / ASSUMED_AVERAGE_SPEED_KMH * 60.0,
        ).toInt().coerceAtLeast(1)
    }
}

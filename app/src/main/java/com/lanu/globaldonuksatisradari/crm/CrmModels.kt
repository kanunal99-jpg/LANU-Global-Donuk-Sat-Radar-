package com.lanu.globaldonuksatisradari.crm

/** CRM pipeline is explicit so dashboard values can be derived from persisted state later. */
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

data class CrmCustomer(
    val id: String,
    val businessSourceId: String,
    val businessName: String,
    val city: String,
    val district: String,
    val neighborhood: String?,
    val address: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val stage: CrmStage = CrmStage.PROSPECT,
    val ownerUserId: String? = null,
    val notes: String? = null,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
    val version: Long = 0L,
    val syncState: SyncState = SyncState.LOCAL_ONLY,
)

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
)

object CrmRoutePlanner {
    fun plan(
        customers: List<CrmCustomer>,
        startCustomerId: String? = null,
    ): List<RouteStop> {
        val candidates = customers.filter { it.latitude != null && it.longitude != null }
        if (candidates.isEmpty()) return emptyList()
        val remaining = candidates.toMutableList()
        val ordered = mutableListOf<CrmCustomer>()
        var current = startCustomerId?.let { id -> remaining.firstOrNull { it.id == id } }
            ?: remaining.minWithOrNull(compareBy<CrmCustomer>({ it.latitude }, { it.longitude }, { it.businessName }))
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

        var total = 0.0
        return ordered.mapIndexed { index, customer ->
            val previous = ordered.getOrNull(index - 1)
            val segment = if (previous == null) 0.0 else {
                distanceKm(
                    previous.latitude!!,
                    previous.longitude!!,
                    customer.latitude!!,
                    customer.longitude!!,
                )
            }
            total += segment
            RouteStop(
                customer = customer,
                order = index + 1,
                distanceFromPreviousKm = segment,
                cumulativeDistanceKm = total,
            )
        }
    }

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

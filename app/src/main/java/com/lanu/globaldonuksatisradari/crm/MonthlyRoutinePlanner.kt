package com.lanu.globaldonuksatisradari.crm

import kotlin.math.ceil
import kotlin.math.roundToInt

enum class VisitFrequencySource {
    AUTO,
    MANUAL,
}

data class RoutineVisitFrequency(
    val customerId: String,
    val intervalDays: Int,
    val plannedVisits: Int,
    val source: VisitFrequencySource,
)

data class RoutineDayPlan(
    val weekNumber: Int,
    val weekday: String,
    val dayIndex: Int,
    val stops: List<RouteStop>,
) {
    val totalDistanceKm: Double
        get() = stops.lastOrNull()?.cumulativeDistanceKm ?: 0.0

    val totalEstimatedMinutes: Int
        get() = stops.lastOrNull()?.cumulativeEstimatedMinutes ?: 0
}

data class MonthlyRoutinePlan(
    val days: List<RoutineDayPlan>,
    val frequencies: List<RoutineVisitFrequency> = emptyList(),
) {
    /** Visit count. A high-frequency customer can appear more than once in the month. */
    val totalPointCount: Int
        get() = days.sumOf { it.stops.size }

    val uniquePointCount: Int
        get() = days.flatMap { it.stops }.map { it.customer.id }.distinct().size

    val totalDistanceKm: Double
        get() = days.sumOf { it.totalDistanceKm }

    val totalEstimatedMinutes: Int
        get() = days.sumOf { it.totalEstimatedMinutes }

    fun frequencyFor(customerId: String): RoutineVisitFrequency? =
        frequencies.firstOrNull { it.customerId == customerId }
}

/**
 * Explicit default frequency policy. It is deterministic, offline-safe and intentionally easy to
 * audit. Manual day intervals never overwrite this policy; they create a separate preview plan.
 */
object VisitFrequencyPolicy {
    const val HORIZON_DAYS = 28

    fun automaticIntervalDays(customer: CrmCustomer): Int? = when (customer.stage) {
        CrmStage.ACTIVE_CUSTOMER,
        CrmStage.ORDER,
        -> 7

        CrmStage.PROPOSAL,
        CrmStage.SAMPLE,
        CrmStage.MEETING,
        CrmStage.VISIT,
        -> 14

        CrmStage.PROSPECT -> 28
        CrmStage.LOST -> null
    }

    fun plannedVisits(intervalDays: Int): Int {
        require(intervalDays >= 1) { "Ziyaret aralığı en az 1 gün olmalıdır." }
        return ceil(HORIZON_DAYS.toDouble() / intervalDays.toDouble())
            .toInt()
            .coerceIn(1, MonthlyRoutinePlanner.WORK_DAYS)
    }
}

object MonthlyRoutinePlanner {
    val WEEKDAYS: List<String> = listOf(
        "Pazartesi",
        "Salı",
        "Çarşamba",
        "Perşembe",
        "Cuma",
    )

    const val WEEKS = 4
    const val WORK_DAYS = WEEKS * 5

    /**
     * Generates a four-week weekday plan.
     *
     * When [manualIntervalDays] is null, each customer uses the automatic stage-based frequency.
     * When it is set, the same interval is applied to all routable customers to build a separate
     * manual-repeat preview. The automatic policy remains unchanged.
     */
    fun plan(
        customers: List<CrmCustomer>,
        startCustomerId: String? = null,
        manualIntervalDays: Int? = null,
    ): MonthlyRoutinePlan {
        require(manualIntervalDays == null || manualIntervalDays in 1..365) {
            "Manuel ziyaret aralığı 1-365 gün arasında olmalıdır."
        }

        val routable = customers.filter(::isRoutable)
        val scheduled = routable.filter { customer ->
            manualIntervalDays != null || VisitFrequencyPolicy.automaticIntervalDays(customer) != null
        }

        val frequencies = scheduled.map { customer ->
            val interval = manualIntervalDays
                ?: VisitFrequencyPolicy.automaticIntervalDays(customer)
                ?: return@map null
            RoutineVisitFrequency(
                customerId = customer.id,
                intervalDays = interval,
                plannedVisits = VisitFrequencyPolicy.plannedVisits(interval),
                source = if (manualIntervalDays == null) {
                    VisitFrequencySource.AUTO
                } else {
                    VisitFrequencySource.MANUAL
                },
            )
        }.filterNotNull()

        if (scheduled.isEmpty()) {
            return MonthlyRoutinePlan(
                days = emptyDayPlans(),
                frequencies = frequencies,
            )
        }

        val baseDays = basePlan(
            candidates = scheduled,
            startCustomerId = startCustomerId,
        )
        val assignments = baseDays
            .map { day -> day.stops.map { it.customer }.toMutableList() }
            .toMutableList()

        val firstDayByCustomer = mutableMapOf<String, Int>()
        assignments.forEachIndexed { dayIndex, customersForDay ->
            customersForDay.forEach { customer ->
                firstDayByCustomer.putIfAbsent(customer.id, dayIndex)
            }
        }

        val customerById = scheduled.associateBy { it.id }
        frequencies.forEach { frequency ->
            if (frequency.plannedVisits <= 1) return@forEach
            val customer = customerById[frequency.customerId] ?: return@forEach
            val baseDay = firstDayByCustomer[customer.id] ?: return@forEach

            for (visitIndex in 1 until frequency.plannedVisits) {
                val offset = (
                    visitIndex * WORK_DAYS.toDouble() / frequency.plannedVisits.toDouble()
                    ).roundToInt().coerceAtLeast(1)
                var targetDay = (baseDay + offset) % WORK_DAYS

                // A customer may not be scheduled twice on the same day.
                var attempts = 0
                while (
                    assignments[targetDay].any { it.id == customer.id } &&
                    attempts < WORK_DAYS
                ) {
                    targetDay = (targetDay + 1) % WORK_DAYS
                    attempts++
                }
                if (attempts < WORK_DAYS) assignments[targetDay] += customer
            }
        }

        val days = assignments.mapIndexed { dayIndex, dayCustomers ->
            val preferredStart = startCustomerId
                ?.takeIf { id -> dayCustomers.any { it.id == id } }
            RoutineDayPlan(
                weekNumber = dayIndex / 5 + 1,
                weekday = WEEKDAYS[dayIndex % 5],
                dayIndex = dayIndex,
                stops = CrmRoutePlanner.plan(dayCustomers, preferredStart),
            )
        }

        return MonthlyRoutinePlan(
            days = days,
            frequencies = frequencies,
        )
    }

    /**
     * Preserves the existing proximity clustering as the first monthly visit for every point.
     * Frequency repeats are layered onto these base day assignments afterwards.
     */
    private fun basePlan(
        candidates: List<CrmCustomer>,
        startCustomerId: String?,
    ): List<RoutineDayPlan> {
        if (candidates.isEmpty()) return emptyDayPlans()

        if (CrmRoutePlanner.usesScalableFallback(candidates.size)) {
            val ordered = CrmRoutePlanner.plan(candidates, startCustomerId)
                .map(RouteStop::customer)
            val basePerDay = ordered.size / WORK_DAYS
            val remainder = ordered.size % WORK_DAYS
            var cursor = 0
            return List(WORK_DAYS) { dayIndex ->
                val quota = basePerDay + if (dayIndex < remainder) 1 else 0
                val dayCustomers = if (quota == 0) {
                    emptyList()
                } else {
                    ordered.subList(cursor, cursor + quota).also { cursor += quota }
                }
                RoutineDayPlan(
                    weekNumber = dayIndex / 5 + 1,
                    weekday = WEEKDAYS[dayIndex % 5],
                    dayIndex = dayIndex,
                    stops = toRouteStops(dayCustomers),
                )
            }
        }

        val start = startCustomerId
            ?.let { id -> candidates.firstOrNull { it.id == id } }
            ?: candidates.minWithOrNull(
                compareBy<CrmCustomer>(
                    { it.latitude },
                    { it.longitude },
                    { it.businessName.lowercase() },
                    { it.id },
                ),
            )
            ?: return emptyDayPlans()

        val anchorLat = start.latitude!!
        val anchorLon = start.longitude!!
        val remaining = candidates.toMutableList()

        val basePerDay = candidates.size / WORK_DAYS
        val remainder = candidates.size % WORK_DAYS
        val quotas = List(WORK_DAYS) { index ->
            basePerDay + if (index < remainder) 1 else 0
        }

        val dayPlans = mutableListOf<RoutineDayPlan>()
        quotas.forEachIndexed { dayIndex, quota ->
            val weekNumber = dayIndex / 5 + 1
            val weekday = WEEKDAYS[dayIndex % 5]

            if (quota == 0 || remaining.isEmpty()) {
                dayPlans += RoutineDayPlan(
                    weekNumber = weekNumber,
                    weekday = weekday,
                    dayIndex = dayIndex,
                    stops = emptyList(),
                )
                return@forEachIndexed
            }

            val seed = if (dayIndex == 0 && remaining.any { it.id == start.id }) {
                start
            } else {
                remaining.minWithOrNull(
                    compareBy<CrmCustomer>(
                        {
                            CrmRoutePlanner.distanceKm(
                                anchorLat,
                                anchorLon,
                                it.latitude!!,
                                it.longitude!!,
                            )
                        },
                        { it.businessName.lowercase() },
                        { it.id },
                    ),
                )
            } ?: return@forEachIndexed

            val ordered = mutableListOf<CrmCustomer>()
            ordered += seed
            remaining.remove(seed)

            var current = seed
            repeat((quota - 1).coerceAtLeast(0)) {
                val next = remaining.minWithOrNull(
                    compareBy<CrmCustomer>(
                        {
                            CrmRoutePlanner.distanceKm(
                                current.latitude!!,
                                current.longitude!!,
                                it.latitude!!,
                                it.longitude!!,
                            )
                        },
                        { it.businessName.lowercase() },
                        { it.id },
                    ),
                ) ?: return@repeat
                ordered += next
                remaining.remove(next)
                current = next
            }

            dayPlans += RoutineDayPlan(
                weekNumber = weekNumber,
                weekday = weekday,
                dayIndex = dayIndex,
                stops = toRouteStops(ordered),
            )
        }

        return dayPlans
    }

    private fun toRouteStops(ordered: List<CrmCustomer>): List<RouteStop> {
        var cumulativeDistance = 0.0
        var cumulativeMinutes = 0
        return ordered.mapIndexed { index, customer ->
            val previous = ordered.getOrNull(index - 1)
            val segment = if (previous == null) {
                0.0
            } else {
                CrmRoutePlanner.distanceKm(
                    previous.latitude!!,
                    previous.longitude!!,
                    customer.latitude!!,
                    customer.longitude!!,
                )
            }
            cumulativeDistance += segment
            val segmentMinutes = RouteTimeEstimator.estimatedTravelMinutes(segment)
            cumulativeMinutes += segmentMinutes
            RouteStop(
                customer = customer,
                order = index + 1,
                distanceFromPreviousKm = segment,
                cumulativeDistanceKm = cumulativeDistance,
                estimatedMinutesFromPrevious = segmentMinutes,
                cumulativeEstimatedMinutes = cumulativeMinutes,
            )
        }
    }

    private fun isRoutable(customer: CrmCustomer): Boolean =
        customer.latitude != null &&
            customer.longitude != null &&
            customer.latitude in -90.0..90.0 &&
            customer.longitude in -180.0..180.0

    private fun emptyDayPlans(): List<RoutineDayPlan> =
        List(WORK_DAYS) { dayIndex ->
            RoutineDayPlan(
                weekNumber = dayIndex / 5 + 1,
                weekday = WEEKDAYS[dayIndex % 5],
                dayIndex = dayIndex,
                stops = emptyList(),
            )
        }
}

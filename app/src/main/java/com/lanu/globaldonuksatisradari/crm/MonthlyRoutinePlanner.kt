package com.lanu.globaldonuksatisradari.crm

data class RoutineDayPlan(
    val weekNumber: Int,
    val weekday: String,
    val dayIndex: Int,
    val stops: List<RouteStop>,
) {
    val totalDistanceKm: Double
        get() = stops.lastOrNull()?.cumulativeDistanceKm ?: 0.0
}

data class MonthlyRoutinePlan(
    val days: List<RoutineDayPlan>,
) {
    val totalPointCount: Int
        get() = days.sumOf { it.stops.size }

    val totalDistanceKm: Double
        get() = days.sumOf { it.totalDistanceKm }
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

    fun plan(
        customers: List<CrmCustomer>,
        startCustomerId: String? = null,
    ): MonthlyRoutinePlan {
        val candidates = customers.filter(::isRoutable)
        if (candidates.isEmpty()) {
            return MonthlyRoutinePlan(emptyDayPlans())
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
            ?: return MonthlyRoutinePlan(emptyDayPlans())

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

        return MonthlyRoutinePlan(dayPlans)
    }

    private fun toRouteStops(ordered: List<CrmCustomer>): List<RouteStop> {
        var cumulative = 0.0
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
            cumulative += segment
            RouteStop(
                customer = customer,
                order = index + 1,
                distanceFromPreviousKm = segment,
                cumulativeDistanceKm = cumulative,
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

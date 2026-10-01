package com.lanu.globaldonuksatisradari.crm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MonthlyRoutinePlannerTest {
    private fun customer(
        id: String,
        lat: Double,
        lon: Double,
    ) = CrmCustomer(
        id = id,
        businessSourceId = "test:$id",
        businessName = id,
        city = "İstanbul",
        district = "Kadıköy",
        neighborhood = null,
        address = "$id adres",
        latitude = lat,
        longitude = lon,
        contactName = "$id kişi",
        phone = "05550000000",
        createdAtEpochMs = 1L,
        updatedAtEpochMs = 1L,
    )

    @Test
    fun createsExactlyFourWeeksAndTwentyWeekdays() {
        val plan = MonthlyRoutinePlanner.plan(
            customers = List(43) { index ->
                customer("p$index", 40.90 + index * 0.001, 29.00 + index * 0.001)
            },
            startCustomerId = "p10",
        )

        assertEquals(20, plan.days.size)
        assertEquals(4, plan.days.maxOf { it.weekNumber })
        assertEquals(
            listOf("Pazartesi", "Salı", "Çarşamba", "Perşembe", "Cuma"),
            plan.days.take(5).map { it.weekday },
        )
        assertEquals("p10", plan.days.first().stops.first().customer.id)
        assertEquals(43, plan.totalPointCount)

        val allIds = plan.days.flatMap { it.stops }.map { it.customer.id }
        assertEquals(43, allIds.distinct().size)

        val counts = plan.days.map { it.stops.size }
        assertTrue(counts.maxOrNull()!! - counts.minOrNull()!! <= 1)
    }

    @Test
    fun nearbyPairsStayInSameDayCluster() {
        val customers = buildList {
            repeat(20) { cluster ->
                val baseLat = 40.90 + cluster * 0.02
                add(customer("c${cluster}a", baseLat, 29.00))
                add(customer("c${cluster}b", baseLat + 0.0002, 29.0002))
            }
        }

        val plan = MonthlyRoutinePlanner.plan(customers, startCustomerId = "c0a")

        assertEquals(20, plan.days.count { it.stops.isNotEmpty() })
        plan.days.forEach { day ->
            assertEquals(2, day.stops.size)
            val firstCluster = day.stops.first().customer.id.dropLast(1)
            assertTrue(day.stops.all { it.customer.id.dropLast(1) == firstCluster })
            assertEquals(0.0, day.stops.first().cumulativeDistanceKm, 0.000001)
            assertTrue(day.stops.last().cumulativeDistanceKm < 0.1)
        }
    }

    @Test
    fun cumulativeDistanceRestartsEveryDay() {
        val customers = List(40) { index ->
            customer("p$index", 40.90 + index * 0.001, 29.00 + index * 0.001)
        }

        val plan = MonthlyRoutinePlanner.plan(customers, startCustomerId = "p0")

        plan.days.filter { it.stops.isNotEmpty() }.forEach { day ->
            assertEquals(0.0, day.stops.first().cumulativeDistanceKm, 0.000001)
        }
    }

    @Test
    fun invalidCoordinatesAreExcludedFromMonthlyPlan() {
        val valid = customer("valid", 40.99, 29.03)
        val invalid = customer("invalid", 200.0, 29.03)

        val plan = MonthlyRoutinePlanner.plan(listOf(valid, invalid))

        assertEquals(1, plan.totalPointCount)
        assertEquals("valid", plan.days.first().stops.single().customer.id)
    }
}

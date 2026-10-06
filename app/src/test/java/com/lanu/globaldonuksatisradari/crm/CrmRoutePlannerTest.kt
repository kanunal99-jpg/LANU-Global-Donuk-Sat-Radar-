package com.lanu.globaldonuksatisradari.crm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CrmRoutePlannerTest {
    private fun customer(id: String, name: String, lat: Double, lon: Double) = CrmCustomer(
        id = id,
        businessSourceId = "test:$id",
        businessName = name,
        city = "İstanbul",
        district = "Kadıköy",
        neighborhood = null,
        latitude = lat,
        longitude = lon,
        createdAtEpochMs = 1L,
        updatedAtEpochMs = 1L,
    )

    @Test
    fun plansNearestNeighborOrder() {
        val a = customer("a", "A", 40.9900, 29.0200)
        val b = customer("b", "B", 40.9910, 29.0210)
        val c = customer("c", "C", 41.0100, 29.1000)
        val route = CrmRoutePlanner.plan(listOf(a, c, b), startCustomerId = "a")

        assertEquals(listOf("a", "b", "c"), route.map { it.customer.id })
        assertEquals(3, route.size)
        assertTrue(route.last().cumulativeDistanceKm > 0.0)
    }

    @Test
    fun estimatedTravelTimeIsMonotonicAndZeroForOrigin() {
        assertEquals(0, RouteTimeEstimator.estimatedTravelMinutes(0.0))
        val oneKm = RouteTimeEstimator.estimatedTravelMinutes(1.0)
        val fiveKm = RouteTimeEstimator.estimatedTravelMinutes(5.0)
        assertTrue(oneKm >= 1)
        assertTrue(fiveKm > oneKm)
    }

    @Test
    fun invalidCoordinatesAreNotRoutable() {
        val a = customer("a", "A", 200.0, 29.0200)
        assertTrue(CrmRoutePlanner.plan(listOf(a)).isEmpty())
    }

    @Test
    fun largeCustomerSetUsesScalableFallbackAndKeepsEveryPoint() {
        val customers = (0 until 1200).map { index ->
            customer(
                id = "large-$index",
                name = "Nokta $index",
                lat = 40.80 + (index % 40) * 0.003,
                lon = 28.70 + (index / 40) * 0.003,
            )
        }

        assertTrue(CrmRoutePlanner.usesScalableFallback(customers.size))
        val route = CrmRoutePlanner.plan(customers, startCustomerId = "large-777")

        assertEquals(customers.size, route.size)
        assertEquals(customers.size, route.map { it.customer.id }.toSet().size)
        assertEquals("large-777", route.first().customer.id)
        assertTrue(route.last().cumulativeDistanceKm > 0.0)
    }
}

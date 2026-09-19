package com.lanu.globaldonuksatisradari.crm

import org.junit.Assert.assertEquals
import org.junit.Test

class CrmDashboardMetricsTest {

    @Test
    fun metrics_separatePipelineStagesFromRealActivitiesAndFollowUps() {
        val customers = listOf(
            CrmCustomer(
                id = "c1",
                businessSourceId = "osm-1",
                businessName = "Kafe 1",
                city = "İstanbul",
                district = "Kadıköy",
                neighborhood = null,
                stage = CrmStage.PROPOSAL,
                createdAtEpochMs = 1L,
                updatedAtEpochMs = 2L,
            ),
            CrmCustomer(
                id = "c2",
                businessSourceId = "osm-2",
                businessName = "Kafe 2",
                city = "İstanbul",
                district = "Kadıköy",
                neighborhood = null,
                stage = CrmStage.VISIT,
                createdAtEpochMs = 1L,
                updatedAtEpochMs = 2L,
            ),
        )
        val activities = listOf(
            activity("a1", CrmActivityType.VISIT),
            activity("a2", CrmActivityType.VISIT),
            activity("a3", CrmActivityType.CALL),
        )
        val actions = listOf(
            action("n1", 900L),
            action("n2", 2_000L),
        )
        val opportunities = listOf(
            opportunity("o1", CrmOpportunityStatus.OPEN),
            opportunity("o2", CrmOpportunityStatus.WON),
            opportunity("o3", CrmOpportunityStatus.LOST),
        )

        val metrics = CrmDashboardMetrics.from(
            customers = customers,
            activities = activities,
            openNextActions = actions,
            opportunities = opportunities,
            nowEpochMs = 1_000L,
        )

        assertEquals(2, metrics.customers)
        assertEquals(0, metrics.prospects)
        assertEquals(1, metrics.visits)
        assertEquals(1, metrics.proposals)
        assertEquals(2, metrics.visitActivities)
        assertEquals(1, metrics.openOpportunities)
        assertEquals(1, metrics.wonOpportunities)
        assertEquals(1, metrics.lostOpportunities)
        assertEquals(1, metrics.callActivities)
        assertEquals(2, metrics.openNextActions)
        assertEquals(1, metrics.overdueNextActions)
    }

    private fun activity(id: String, type: CrmActivityType) = CrmActivity(
        id = id,
        customerId = "c1",
        type = type,
        occurredAtEpochMs = 100L,
        createdAtEpochMs = 100L,
    )

    private fun opportunity(id: String, status: CrmOpportunityStatus) = CrmOpportunity(
        id = id,
        customerId = "c1",
        title = "Fırsat " + id,
        status = status,
        createdAtEpochMs = 100L,
        updatedAtEpochMs = 100L,
    )

    private fun action(id: String, dueAtEpochMs: Long) = CrmNextAction(
        id = id,
        customerId = "c1",
        type = CrmNextActionType.CALL,
        dueAtEpochMs = dueAtEpochMs,
        createdAtEpochMs = 100L,
    )
}

package com.lanu.globaldonuksatisradari.crm

data class CrmDashboardMetrics(
    val customers: Int,
    val prospects: Int,
    val visits: Int,
    val meetings: Int,
    val proposals: Int,
    val samples: Int,
    val orders: Int,
    val activeCustomers: Int,
    val visitActivities: Int = 0,
    val callActivities: Int = 0,
    val meetingActivities: Int = 0,
    val sampleActivities: Int = 0,
    val proposalActivities: Int = 0,
    val orderActivities: Int = 0,
    val openNextActions: Int = 0,
    val overdueNextActions: Int = 0,
    val openOpportunities: Int = 0,
    val wonOpportunities: Int = 0,
    val lostOpportunities: Int = 0,
) {
    companion object {
        fun from(
            customers: List<CrmCustomer>,
            activities: List<CrmActivity> = emptyList(),
            openNextActions: List<CrmNextAction> = emptyList(),
            opportunities: List<CrmOpportunity> = emptyList(),
            nowEpochMs: Long = System.currentTimeMillis(),
        ): CrmDashboardMetrics {
            val open = openNextActions.filter { it.completedAtEpochMs == null }
            val activeOpportunities = opportunities.filter { it.status == CrmOpportunityStatus.OPEN }
            return CrmDashboardMetrics(
                customers = customers.size,
                prospects = customers.count { it.stage == CrmStage.PROSPECT },
                visits = customers.count { it.stage == CrmStage.VISIT },
                meetings = customers.count { it.stage == CrmStage.MEETING },
                proposals = customers.count { it.stage == CrmStage.PROPOSAL },
                samples = customers.count { it.stage == CrmStage.SAMPLE },
                orders = customers.count { it.stage == CrmStage.ORDER },
                activeCustomers = customers.count { it.stage == CrmStage.ACTIVE_CUSTOMER },
                visitActivities = activities.count { it.type == CrmActivityType.VISIT },
                callActivities = activities.count { it.type == CrmActivityType.CALL },
                meetingActivities = activities.count { it.type == CrmActivityType.MEETING },
                sampleActivities = activities.count { it.type == CrmActivityType.SAMPLE },
                proposalActivities = activities.count { it.type == CrmActivityType.PROPOSAL },
                orderActivities = activities.count { it.type == CrmActivityType.ORDER },
                openNextActions = open.size,
                overdueNextActions = open.count { it.dueAtEpochMs <= nowEpochMs },
                openOpportunities = activeOpportunities.size,
                wonOpportunities = opportunities.count { it.status == CrmOpportunityStatus.WON },
                lostOpportunities = opportunities.count { it.status == CrmOpportunityStatus.LOST },
            )
        }
    }
}

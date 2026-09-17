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
) {
    companion object {
        fun from(customers: List<CrmCustomer>): CrmDashboardMetrics = CrmDashboardMetrics(
            customers = customers.size,
            prospects = customers.count { it.stage == CrmStage.PROSPECT },
            visits = customers.count { it.stage == CrmStage.VISIT },
            meetings = customers.count { it.stage == CrmStage.MEETING },
            proposals = customers.count { it.stage == CrmStage.PROPOSAL },
            samples = customers.count { it.stage == CrmStage.SAMPLE },
            orders = customers.count { it.stage == CrmStage.ORDER },
            activeCustomers = customers.count { it.stage == CrmStage.ACTIVE_CUSTOMER },
        )
    }
}

package com.lanu.globaldonuksatisradari

import com.lanu.globaldonuksatisradari.crm.CrmCustomer
import com.lanu.globaldonuksatisradari.crm.CrmStage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutineScreenPerformanceTest {
    @Test
    fun routineCandidateLimitIsDeterministicAndPrioritizesAdvancedStages() {
        val customers = buildList {
            repeat(150) { index ->
                add(
                    CrmCustomer(
                        id = "prospect-" + index,
                        businessSourceId = "src-" + index,
                        businessName = "Prospect " + index,
                        city = "İstanbul",
                        district = "Sultanbeyli",
                        neighborhood = null,
                        latitude = 41.0 + index / 10000.0,
                        longitude = 29.2,
                        stage = CrmStage.PROSPECT,
                        createdAtEpochMs = index.toLong(),
                        updatedAtEpochMs = index.toLong(),
                    ),
                )
            }
            add(
                CrmCustomer(
                    id = "order-priority",
                    businessSourceId = "order-src",
                    businessName = "Sipariş Öncelik",
                    city = "İstanbul",
                    district = "Sultanbeyli",
                    neighborhood = null,
                    latitude = 41.1,
                    longitude = 29.3,
                    stage = CrmStage.ORDER,
                    createdAtEpochMs = 999L,
                    updatedAtEpochMs = 999L,
                ),
            )
        }

        val selected = selectRoutineCandidates(customers, 100)

        assertEquals(100, selected.size)
        assertEquals("order-priority", selected.first().id)
        assertTrue(selected.none { it.stage == CrmStage.LOST })
        assertEquals(151, selectRoutineCandidates(customers, 0).size)
    }
}

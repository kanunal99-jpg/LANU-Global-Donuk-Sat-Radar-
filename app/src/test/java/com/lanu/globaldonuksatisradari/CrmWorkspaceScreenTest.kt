package com.lanu.globaldonuksatisradari

import com.lanu.globaldonuksatisradari.crm.CrmNextAction
import com.lanu.globaldonuksatisradari.crm.CrmNextActionType
import com.lanu.globaldonuksatisradari.crm.SyncState
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class CrmWorkspaceScreenTest {
    @Test
    fun openActionsAreBucketedByLocalBusinessDay() {
        val zone = ZoneId.of("Europe/Istanbul")
        fun epoch(value: String): Long =
            LocalDateTime.parse(value).atZone(zone).toInstant().toEpochMilli()
        fun action(id: String, due: String, completed: Boolean = false) = CrmNextAction(
            id = id,
            customerId = "customer-1",
            type = CrmNextActionType.CALL,
            dueAtEpochMs = epoch(due),
            createdAtEpochMs = 1L,
            completedAtEpochMs = if (completed) epoch("2026-10-05T11:00:00") else null,
            version = 1L,
            syncState = SyncState.LOCAL_ONLY,
        )

        val buckets = bucketOpenActions(
            actions = listOf(
                action("overdue", "2026-10-04T18:00:00"),
                action("today", "2026-10-05T15:00:00"),
                action("upcoming", "2026-10-06T09:00:00"),
                action("completed", "2026-10-05T10:00:00", completed = true),
            ),
            nowEpochMs = epoch("2026-10-05T13:00:00"),
            zoneId = zone,
        )

        assertEquals(listOf("overdue"), buckets.overdue.map { it.id })
        assertEquals(listOf("today"), buckets.today.map { it.id })
        assertEquals(listOf("upcoming"), buckets.upcoming.map { it.id })
    }
}

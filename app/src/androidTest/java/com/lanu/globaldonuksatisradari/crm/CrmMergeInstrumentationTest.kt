package com.lanu.globaldonuksatisradari.crm

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CrmMergeInstrumentationTest {
    @Test
    fun mergeMovesAllCustomerChildrenAndLeavesHiddenAuditTombstone() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val database = LanuCrmDatabase.getInstance(context)
        val repository = LocalCrmRepository(database)
        val token = UUID.randomUUID().toString().take(8)

        val target = repository.addManualCustomerPoint(
            businessName = "Ana Müşteri $token",
            address = "Ana adres",
            city = "İstanbul",
            district = "Kadıköy",
            neighborhood = "Caferağa",
            latitude = 40.98,
            longitude = 29.03,
            signboardName = "Ana Tabela",
            phone = "05321110000",
        )
        val source = repository.addManualCustomerPoint(
            businessName = "Mükerrer Müşteri $token",
            address = "Kaynak adres",
            city = "İstanbul",
            district = "Kadıköy",
            neighborhood = "Caferağa",
            latitude = 40.9801,
            longitude = 29.0301,
            signboardName = "Kaynak Tabela",
            phone = "05321110000",
        )

        val now = System.currentTimeMillis()
        val activityId = "act-$token"
        val actionId = "action-$token"
        val opportunityId = "opp-$token"
        val contactId = "contact-$token"
        val quoteId = "quote-$token"
        val orderId = "order-$token"
        val transitionId = "transition-$token"

        database.activityDao().upsert(
            CrmActivityEntity(
                id = activityId,
                customerId = source.id,
                type = CrmActivityType.NOTE.name,
                occurredAtEpochMs = now,
                note = "Kaynak aktivite",
                createdByUserId = null,
                createdAtEpochMs = now,
                version = 1,
                syncState = SyncState.LOCAL_ONLY.name,
            ),
        )
        database.nextActionDao().upsert(
            CrmNextActionEntity(
                id = actionId,
                customerId = source.id,
                type = CrmNextActionType.CALL.name,
                dueAtEpochMs = now + 60_000,
                note = "Kaynak takip",
                createdByUserId = null,
                createdAtEpochMs = now,
                completedAtEpochMs = null,
                completedByUserId = null,
                version = 1,
                syncState = SyncState.LOCAL_ONLY.name,
            ),
        )
        database.opportunityDao().upsert(
            CrmOpportunityEntity(
                id = opportunityId,
                customerId = source.id,
                title = "Kaynak fırsat",
                status = CrmOpportunityStatus.OPEN.name,
                notes = null,
                estimatedValueMinor = null,
                currency = null,
                valueOrigin = CrmValueOrigin.UNKNOWN.name,
                createdAtEpochMs = now,
                updatedAtEpochMs = now,
                version = 1,
                syncState = SyncState.LOCAL_ONLY.name,
            ),
        )
        database.contactDao().upsert(
            CrmContactEntity(
                id = contactId,
                customerId = source.id,
                fullName = "Kaynak Yetkili",
                role = "Satın Alma",
                phone = "05320000000",
                email = null,
                isPrimary = true,
                createdAtEpochMs = now,
                updatedAtEpochMs = now,
                version = 1,
                syncState = SyncState.LOCAL_ONLY.name,
            ),
        )
        database.quoteDao().upsert(
            CrmQuoteEntity(
                id = quoteId,
                customerId = source.id,
                opportunityId = null,
                quoteNumber = "T-$token",
                status = CrmQuoteStatus.DRAFT.name,
                currency = "TRY",
                totalMinor = 0,
                validUntilEpochMs = null,
                notes = null,
                createdAtEpochMs = now,
                updatedAtEpochMs = now,
                version = 1,
                syncState = SyncState.LOCAL_ONLY.name,
            ),
        )
        database.orderDao().upsert(
            CrmOrderEntity(
                id = orderId,
                customerId = source.id,
                quoteId = null,
                orderNumber = "S-$token",
                status = CrmOrderStatus.DRAFT.name,
                currency = "TRY",
                totalMinor = 0,
                notes = null,
                createdAtEpochMs = now,
                updatedAtEpochMs = now,
                version = 1,
                syncState = SyncState.LOCAL_ONLY.name,
            ),
        )
        database.stageTransitionDao().insert(
            CrmStageTransitionEntity(
                id = transitionId,
                customerId = source.id,
                fromStage = null,
                toStage = CrmStage.PROSPECT.name,
                changedAtEpochMs = now,
                changedByUserId = null,
                clientVersion = 1,
            ),
        )

        val result = repository.mergeCustomers(target.id, source.id)

        assertEquals(1, result.movedActivities)
        assertEquals(1, result.movedNextActions)
        assertEquals(1, result.movedOpportunities)
        assertEquals(1, result.movedContacts)
        assertEquals(1, result.movedQuotes)
        assertEquals(1, result.movedOrders)
        assertEquals(target.id, database.activityDao().findById(activityId)?.customerId)
        assertEquals(target.id, database.nextActionDao().findById(actionId)?.customerId)
        assertEquals(target.id, database.opportunityDao().findById(opportunityId)?.customerId)
        assertEquals(target.id, database.contactDao().findById(contactId)?.customerId)
        assertEquals(target.id, database.quoteDao().findById(quoteId)?.customerId)
        assertEquals(target.id, database.orderDao().findById(orderId)?.customerId)
        assertTrue(database.stageTransitionDao().observeForCustomer(target.id).first().any { it.id == transitionId })

        val tombstone = database.customerDao().findById(source.id)
        assertEquals(target.id, tombstone?.mergedIntoCustomerId)
        val visible = repository.observeCustomers().first()
        assertTrue(visible.any { it.id == target.id })
        assertFalse(visible.any { it.id == source.id })
    }
}

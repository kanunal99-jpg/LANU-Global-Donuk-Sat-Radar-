package com.lanu.globaldonuksatisradari.crm

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.lanu.globaldonuksatisradari.data.DataSourceDescriptor
import com.lanu.globaldonuksatisradari.data.VerifiedBusiness
import com.lanu.globaldonuksatisradari.data.VerifiedBusinessValidator
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

@RunWith(AndroidJUnit4::class)
class CrmRoomInstrumentationTest {

    @Test
    fun customerSave_persistsCustomerAndSyncOperation() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val database = Room.inMemoryDatabaseBuilder(context, LanuCrmDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        try {
            val business = VerifiedBusiness(
                id = "osm-node-123",
                name = "Smoke Test Kafe",
                city = "İstanbul",
                district = "Kadıköy",
                neighborhood = "Caferağa",
                source = DataSourceDescriptor(
                    id = "osm-nominatim",
                    name = "OpenStreetMap Nominatim",
                    publisher = "OpenStreetMap",
                    licenseOrTerms = "ODbL",
                    sourceUrl = "https://nominatim.openstreetmap.org/",
                    lastVerifiedAtEpochMs = 1L,
                ),
                verifiedAtEpochMs = 1L,
                latitude = 40.99,
                longitude = 29.03,
                category = "cafe",
            )
            assertTrue(VerifiedBusinessValidator.validate(business).isSuccess)

            var idIndex = 0
            val repository = LocalCrmRepository(
                database = database,
                now = { 1_000L },
                idGenerator = { "test-id-" + (idIndex++) },
            )

            val customer = repository.addBusinessAsCustomer(
                business = business,
                ownerUserId = "instrumentation-test",
            )
            val duplicate = repository.addBusinessAsCustomer(
                business = business,
                ownerUserId = "instrumentation-test",
            )
            assertEquals(customer.id, duplicate.id)
            assertEquals(1, repository.pendingSync().size)

            val observed = repository.observeCustomers("İstanbul").first()
            val pending = repository.pendingSync()
            val transitions = database.stageTransitionDao().observeForCustomer(customer.id).first()
            val action = repository.createNextAction(
                customerId = customer.id,
                type = CrmNextActionType.PROPOSAL_FOLLOW_UP,
                dueAtEpochMs = 2_000L,
                note = "Teklif takibi",
                createdByUserId = "instrumentation-test",
            )
            val actions = repository.observeNextActions(customer.id).first()
            assertEquals(1, actions.size)
            assertEquals(CrmNextActionType.PROPOSAL_FOLLOW_UP, actions.single().type)
            assertEquals(2_000L, actions.single().dueAtEpochMs)
            assertEquals(SyncState.PENDING_UPLOAD, actions.single().syncState)

            val completed = repository.completeNextAction(action.id, "instrumentation-test")
            assertTrue(completed.completedAtEpochMs != null)
            val activitiesAfterCompletion = repository.observeActivities(customer.id).first()
            assertEquals(1, activitiesAfterCompletion.size)
            assertEquals(CrmActivityType.PROPOSAL, activitiesAfterCompletion.single().type)
            assertEquals("Teklif takibi", activitiesAfterCompletion.single().note)
            assertEquals(4, repository.pendingSync().size)

            assertEquals("Smoke Test Kafe", observed.single().businessName)
            assertEquals(SyncState.PENDING_UPLOAD, observed.single().syncState)
            assertEquals(customer.id, pending.single().entityId)
            assertEquals(LocalCrmRepository.OP_CREATE, pending.single().operation)
            assertEquals(1, transitions.size)
            assertEquals(CrmStage.PROSPECT.name, transitions.single().toStage)

            val opportunity = repository.createOpportunity(
                customerId = customer.id,
                title = "Bahar menü fırsatı",
                notes = "Müşteri kendi tahminini paylaştı.",
                estimatedValueMinor = 250_000L,
                currency = "TRY",
                valueOrigin = CrmValueOrigin.USER_ENTERED,
                createdByUserId = "instrumentation-test",
            )
            val opportunities = repository.observeOpportunities(customer.id).first()
            assertEquals(1, opportunities.size)
            assertEquals("Bahar menü fırsatı", opportunities.single().title)
            assertEquals(250_000L, opportunities.single().estimatedValueMinor)
            assertEquals("TRY", opportunities.single().currency)
            assertEquals(CrmValueOrigin.USER_ENTERED, opportunities.single().valueOrigin)
            assertEquals(CrmOpportunityStatus.OPEN, opportunities.single().status)
            assertEquals(SyncState.PENDING_UPLOAD, opportunities.single().syncState)

            val won = repository.transitionOpportunity(opportunity.id, CrmOpportunityStatus.WON)
            assertEquals(CrmOpportunityStatus.WON, won.status)
            assertEquals(6, repository.pendingSync().size)
        } finally {
            database.close()
        }
    }
}

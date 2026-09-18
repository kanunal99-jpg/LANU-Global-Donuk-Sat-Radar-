package com.lanu.globaldonuksatisradari.crm

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lanu.globaldonuksatisradari.data.DataSourceDescriptor
import com.lanu.globaldonuksatisradari.data.VerifiedBusiness
import com.lanu.globaldonuksatisradari.data.VerifiedBusinessValidator
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

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
            assertEquals(3, repository.pendingSync().size)

            assertEquals("Smoke Test Kafe", observed.single().businessName)
            assertEquals(SyncState.PENDING_UPLOAD, observed.single().syncState)
            assertEquals(customer.id, pending.single().entityId)
            assertEquals(LocalCrmRepository.OP_CREATE, pending.single().operation)
            assertEquals(1, transitions.size)
            assertEquals(CrmStage.PROSPECT.name, transitions.single().toStage)
        } finally {
            database.close()
        }
    }
}

package com.lanu.globaldonuksatisradari

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lanu.globaldonuksatisradari.crm.CommercialCrmRepository
import com.lanu.globaldonuksatisradari.crm.CommercialCrmSync
import com.lanu.globaldonuksatisradari.crm.ContactCrmRepository
import com.lanu.globaldonuksatisradari.crm.CrmActivityType
import com.lanu.globaldonuksatisradari.crm.CrmNextActionType
import com.lanu.globaldonuksatisradari.crm.CrmOrderStatus
import com.lanu.globaldonuksatisradari.crm.CrmQuoteStatus
import com.lanu.globaldonuksatisradari.crm.LanuCrmDatabase
import com.lanu.globaldonuksatisradari.crm.LocalCrmRepository
import com.lanu.globaldonuksatisradari.crm.SyncState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EnterpriseCrmPersistenceTest {
    @Test(timeout = 60_000)
    fun contactQuoteAndOrder_flowPersistsOnRealRoomDatabase() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val database = LanuCrmDatabase.getInstance(context)
        val crm = LocalCrmRepository(database)
        val contacts = ContactCrmRepository(database)
        val commercial = CommercialCrmRepository(database)
        val suffix = System.nanoTime().toString()

        val customer = crm.addManualCustomerPoint(
            businessName = "Enterprise Smoke $suffix",
            address = "Test adresi",
            city = "İstanbul",
            district = "Kadıköy",
            neighborhood = "Caferağa",
            latitude = 40.9900,
            longitude = 29.0300,
        )

        val firstContact = contacts.createContact(
            customerId = customer.id,
            fullName = "  Ayşe   Yılmaz  ",
            role = " Satın   Alma ",
            phone = "+90 532 123 45 67",
            email = "AYSE@EXAMPLE.COM",
        )
        assertTrue(firstContact.isPrimary)
        assertEquals("Ayşe Yılmaz", firstContact.fullName)
        assertEquals("ayse@example.com", firstContact.email)

        val editedContact = contacts.updateContact(
            contactId = firstContact.id,
            fullName = "  Ayşe   Demir  ",
            role = " Satın   Alma   Müdürü ",
            phone = "+90 532 765 43 21",
            email = "AYSE.DEMIR@EXAMPLE.COM",
        )
        assertEquals("Ayşe Demir", editedContact.fullName)
        assertEquals("Satın Alma Müdürü", editedContact.role)
        assertEquals("ayse.demir@example.com", editedContact.email)
        assertEquals(firstContact.version + 1L, editedContact.version)
        assertEquals(SyncState.LOCAL_ONLY, editedContact.syncState)

        val primaryAgain = contacts.makePrimary(editedContact.id)
        assertEquals(
            "Selecting the existing primary contact must be idempotent",
            editedContact.version,
            primaryAgain.version,
        )

        val secondContact = contacts.createContact(
            customerId = customer.id,
            fullName = "Mehmet Kaya",
            phone = "+90 533 123 45 67",
            makePrimary = true,
        )
        val persistedContacts = contacts.observeContacts(customer.id).first()
        assertTrue(persistedContacts.first { it.id == secondContact.id }.isPrimary)
        assertFalse(persistedContacts.first { it.id == firstContact.id }.isPrimary)
        assertEquals(
            editedContact.version + 1L,
            persistedContacts.first { it.id == firstContact.id }.version,
        )

        val quote = commercial.createQuote(
            customerId = customer.id,
            opportunityId = null,
            quoteNumber = "SMOKE-TEK-$suffix",
            currency = "TRY",
            notes = "Instrumentation commercial flow",
        )
        val line = commercial.addQuoteLine(
            quoteId = quote.id,
            productId = "smoke-product-$suffix",
            productName = "Smoke Donuk Ürün",
            unit = "Koli",
            quantityMilli = 2_000L,
            unitPriceMinor = 10_000L,
            discountBasisPoints = 500,
        )
        assertEquals(19_000L, line.lineTotalMinor)

        commercial.transitionQuoteStatus(quote.id, CrmQuoteStatus.SENT)
        val accepted = commercial.transitionQuoteStatus(quote.id, CrmQuoteStatus.ACCEPTED)
        assertEquals(19_000L, accepted.totalMinor)

        val order = commercial.createOrderFromAcceptedQuote(
            quoteId = quote.id,
            orderNumber = "SMOKE-SIP-$suffix",
        )
        assertEquals(19_000L, order.totalMinor)
        assertEquals(CrmOrderStatus.DRAFT, order.status)

        val copiedOrderLines = commercial.observeOrderLines(order.id).first()
        assertEquals(1, copiedOrderLines.size)
        assertEquals("Smoke Donuk Ürün", copiedOrderLines.single().productName)
        assertEquals(19_000L, copiedOrderLines.single().lineTotalMinor)

        val confirmed = commercial.transitionOrderStatus(order.id, CrmOrderStatus.CONFIRMED)
        assertEquals(CrmOrderStatus.CONFIRMED, confirmed.status)
    }

    @Test(timeout = 60_000)
    fun ownerlessCoreMutations_stayLocalOnlyAndNeverEnterCloudQueue() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val database = LanuCrmDatabase.getInstance(context)
        val crm = LocalCrmRepository(database)
        val suffix = System.nanoTime().toString()

        val customer = crm.addManualCustomerPoint(
            businessName = "Local Only Smoke $suffix",
            address = "Test adresi",
            city = "İstanbul",
            district = "Kadıköy",
            neighborhood = "Caferağa",
            latitude = 40.9900,
            longitude = 29.0300,
        )
        assertEquals(SyncState.LOCAL_ONLY, customer.syncState)
        assertEquals(
            0,
            database.syncOperationDao().countForEntity(LocalCrmRepository.ENTITY_CUSTOMER, customer.id),
        )

        val notesUpdated = crm.updateCustomerNotes(customer.id, "yalnız cihazda")
        assertEquals(SyncState.LOCAL_ONLY, notesUpdated.syncState)
        assertEquals(
            0,
            database.syncOperationDao().countForEntity(LocalCrmRepository.ENTITY_CUSTOMER, customer.id),
        )

        val activity = crm.recordActivity(
            customerId = customer.id,
            type = CrmActivityType.NOTE,
            note = "yerel aktivite",
        )
        assertEquals(SyncState.LOCAL_ONLY, activity.syncState)
        assertEquals(
            0,
            database.syncOperationDao().countForEntity(LocalCrmRepository.ENTITY_ACTIVITY, activity.id),
        )

        val opportunity = crm.createOpportunity(
            customerId = customer.id,
            title = "Yerel fırsat $suffix",
        )
        assertEquals(SyncState.LOCAL_ONLY, opportunity.syncState)
        assertEquals(
            0,
            database.syncOperationDao().countForEntity(LocalCrmRepository.ENTITY_OPPORTUNITY, opportunity.id),
        )

        val nextAction = crm.createNextAction(
            customerId = customer.id,
            type = CrmNextActionType.NOTE,
            dueAtEpochMs = System.currentTimeMillis() + 60_000L,
            note = "yerel takip",
        )
        assertEquals(SyncState.LOCAL_ONLY, nextAction.syncState)
        assertEquals(
            0,
            database.syncOperationDao().countForEntity(LocalCrmRepository.ENTITY_NEXT_ACTION, nextAction.id),
        )
    }

    @Test(timeout = 60_000)
    fun authenticatedOwner_commercialMutationsAreQueuedForCloudSync() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val database = LanuCrmDatabase.getInstance(context)
        val crm = LocalCrmRepository(database)
        val suffix = System.nanoTime().toString()
        val owner = "owner-$suffix"
        val contacts = ContactCrmRepository(database, ownerUserId = owner)
        val commercial = CommercialCrmRepository(database, ownerUserId = owner)

        val customer = crm.addManualCustomerPoint(
            businessName = "Cloud Queue Smoke $suffix",
            address = "Test adresi",
            city = "İstanbul",
            district = "Kadıköy",
            neighborhood = "Caferağa",
            latitude = 40.9900,
            longitude = 29.0300,
            ownerUserId = owner,
        )

        val contact = contacts.createContact(
            customerId = customer.id,
            fullName = "Cloud Contact",
            email = "cloud@example.com",
        )
        assertEquals(SyncState.PENDING_UPLOAD, contact.syncState)

        val quote = commercial.createQuote(
            customerId = customer.id,
            opportunityId = null,
            quoteNumber = "CLOUD-Q-$suffix",
            currency = "TRY",
        )
        val line = commercial.addQuoteLine(
            quoteId = quote.id,
            productId = "cloud-product-$suffix",
            productName = "Cloud Test Ürün",
            unit = "Koli",
            quantityMilli = 1_000L,
            unitPriceMinor = 10_000L,
        )
        commercial.transitionQuoteStatus(quote.id, CrmQuoteStatus.SENT)
        commercial.transitionQuoteStatus(quote.id, CrmQuoteStatus.ACCEPTED)
        val order = commercial.createOrderFromAcceptedQuote(quote.id, "CLOUD-O-$suffix")
        val orderLines = commercial.observeOrderLines(order.id).first()

        assertEquals(SyncState.PENDING_UPLOAD, line.syncState)
        assertEquals(SyncState.PENDING_UPLOAD, order.syncState)
        assertTrue(orderLines.all { it.syncState == SyncState.PENDING_UPLOAD })

        val pending = crm.pendingSync(1_000)
        assertTrue(pending.any { it.entityType == CommercialCrmSync.ENTITY_CONTACT && it.entityId == contact.id })
        assertTrue(pending.any { it.entityType == CommercialCrmSync.ENTITY_QUOTE && it.entityId == quote.id })
        assertTrue(pending.any { it.entityType == CommercialCrmSync.ENTITY_QUOTE_LINE && it.entityId == line.id })
        assertTrue(pending.any { it.entityType == CommercialCrmSync.ENTITY_ORDER && it.entityId == order.id })
        orderLines.forEach { orderLine ->
            assertTrue(
                pending.any {
                    it.entityType == CommercialCrmSync.ENTITY_ORDER_LINE && it.entityId == orderLine.id
                },
            )
        }
    }
}

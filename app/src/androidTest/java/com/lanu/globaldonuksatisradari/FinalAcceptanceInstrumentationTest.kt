package com.lanu.globaldonuksatisradari

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lanu.globaldonuksatisradari.crm.CommercialCrmRepository
import com.lanu.globaldonuksatisradari.crm.ContactCrmRepository
import com.lanu.globaldonuksatisradari.crm.CrmActivityType
import com.lanu.globaldonuksatisradari.crm.CrmNextActionType
import com.lanu.globaldonuksatisradari.crm.CrmQuoteStatus
import com.lanu.globaldonuksatisradari.crm.LanuCrmDatabase
import com.lanu.globaldonuksatisradari.crm.LocalCrmRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FinalAcceptanceInstrumentationTest {
    @Test
    fun duplicateMergeMovesEntireCustomerWorkspaceAndPreservesAuditTombstone() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val database = LanuCrmDatabase.getInstance(context)
        val crm = LocalCrmRepository(database)
        val contacts = ContactCrmRepository(database, ownerUserId = null)
        val commercial = CommercialCrmRepository(database, ownerUserId = null)
        val suffix = System.nanoTime().toString()

        val target = crm.addManualCustomerPoint(
            businessName = "Final Kabul Market $suffix",
            signboardName = "Final Kabul",
            address = "Hedef Adres $suffix",
            city = "İstanbul",
            district = "Kadıköy",
            neighborhood = "Caferağa",
            latitude = 40.9870,
            longitude = 29.0280,
            phone = null,
        )
        val source = crm.addManualCustomerPoint(
            businessName = "FINAL KABUL MARKET $suffix",
            signboardName = "Final Kabul",
            address = "Kaynak Adres $suffix",
            city = "İstanbul",
            district = "Kadıköy",
            neighborhood = "Caferağa",
            latitude = 40.9871,
            longitude = 29.0281,
            phone = "05321112233",
        )

        crm.updateCustomerTags(target.id, setOf("Otel", "Yüksek Potansiyel"))
        crm.updateCustomerTags(source.id, setOf("Sıcak Lead", "Otel"))

        val activity = crm.recordActivity(source.id, CrmActivityType.CALL, "Mükerrer taşıma aktivitesi")
        val nextAction = crm.createNextAction(
            source.id,
            CrmNextActionType.VISIT,
            System.currentTimeMillis() + 86_400_000L,
            "Mükerrer taşıma takibi",
        )
        val opportunity = crm.createOpportunity(
            customerId = source.id,
            title = "Mükerrer taşıma fırsatı",
            estimatedValueMinor = 125_000L,
            currency = "TRY",
        )
        val contact = contacts.createContact(
            customerId = source.id,
            fullName = "Test Yetkili",
            role = "Satın Alma",
            phone = "05320001122",
            makePrimary = true,
        )

        val quote = commercial.createQuote(
            customerId = source.id,
            opportunityId = opportunity.id,
            quoteNumber = "Q-$suffix",
            currency = "TRY",
        )
        commercial.addQuoteLine(
            quoteId = quote.id,
            productId = "product-$suffix",
            productName = "Test Donuk Ürün",
            unit = "Koli",
            quantityMilli = 2_000L,
            unitPriceMinor = 75_000L,
        )
        commercial.transitionQuoteStatus(quote.id, CrmQuoteStatus.SENT)
        commercial.transitionQuoteStatus(quote.id, CrmQuoteStatus.ACCEPTED)
        val order = commercial.createOrderFromAcceptedQuote(quote.id, "O-$suffix")

        val merge = crm.mergeCustomers(target.id, source.id)

        assertEquals(1, merge.movedActivities)
        assertEquals(1, merge.movedNextActions)
        assertEquals(1, merge.movedOpportunities)
        assertEquals(1, merge.movedContacts)
        assertEquals(1, merge.movedQuotes)
        assertEquals(1, merge.movedOrders)

        val active = crm.observeCustomers("İstanbul").first()
        val mergedTarget = active.first { it.id == target.id }
        assertFalse(active.any { it.id == source.id })
        assertTrue(mergedTarget.tags.contains("Otel"))
        assertTrue(mergedTarget.tags.contains("Yüksek Potansiyel"))
        assertTrue(mergedTarget.tags.contains("Sıcak Lead"))
        assertEquals("05321112233", mergedTarget.phone)

        assertTrue(crm.observeActivities(target.id).first().any { it.id == activity.id })
        assertTrue(crm.observeNextActions(target.id).first().any { it.id == nextAction.id })
        assertTrue(crm.observeOpportunities(target.id).first().any { it.id == opportunity.id })
        assertTrue(contacts.observeContacts(target.id).first().any { it.id == contact.id })
        assertTrue(commercial.observeQuotes(target.id).first().any { it.id == quote.id })
        assertTrue(commercial.observeOrders(target.id).first().any { it.id == order.id })

        val tombstone = database.customerDao().findById(source.id)
        assertEquals(target.id, tombstone?.mergedIntoCustomerId)
    }
}

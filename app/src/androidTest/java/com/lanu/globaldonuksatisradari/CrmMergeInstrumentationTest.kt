package com.lanu.globaldonuksatisradari

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
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
class CrmMergeInstrumentationTest {
    @Test
    fun tagsPersistAndMergeKeepsOneActiveCustomerWithUnionedTags() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val repository = LocalCrmRepository(LanuCrmDatabase.getInstance(context))
        val suffix = System.nanoTime().toString()

        val target = repository.addManualCustomerPoint(
            businessName = "Merge Target $suffix",
            address = "Kadıköy Test",
            city = "İstanbul",
            district = "Kadıköy",
            neighborhood = "Caferağa",
            latitude = 40.99,
            longitude = 29.03,
            phone = "05551112233",
        )
        val source = repository.addManualCustomerPoint(
            businessName = "Merge Source $suffix",
            address = "Kadıköy Test",
            city = "İstanbul",
            district = "Kadıköy",
            neighborhood = "Caferağa",
            latitude = 40.9901,
            longitude = 29.0301,
            phone = "05551112233",
        )

        repository.updateCustomerTags(target.id, listOf("VIP", "HoReCa"))
        repository.updateCustomerTags(source.id, listOf("Saha", "vip"))

        val result = repository.mergeCustomers(target.id, source.id)
        assertEquals(target.id, result.targetCustomerId)
        assertEquals(source.id, result.sourceCustomerId)

        val active = repository.observeCustomers("İstanbul").first()
        val merged = active.first { it.id == target.id }
        assertEquals(setOf("HoReCa", "Saha", "VIP"), merged.tags)
        assertFalse(active.any { it.id == source.id })

        val tombstone = LanuCrmDatabase.getInstance(context).customerDao().findById(source.id)
        assertEquals(target.id, tombstone?.mergedIntoCustomerId)
        assertTrue(tombstone != null)
    }
}

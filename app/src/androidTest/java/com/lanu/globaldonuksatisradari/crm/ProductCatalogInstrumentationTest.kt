package com.lanu.globaldonuksatisradari.crm

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

@RunWith(AndroidJUnit4::class)
class ProductCatalogInstrumentationTest {
    @Test
    fun manualProduct_persistsPriceAndDetails() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val database = Room.inMemoryDatabaseBuilder(context, LanuCrmDatabase::class.java).allowMainThreadQueries().build()
        try {
            val repository = ProductCatalogRepository(database, now = { 10_000L }, idGenerator = { "product-test-id" })
            val product = repository.addProduct(
                name = "Test Donuk Pizza",
                sku = "TEST-001",
                category = "Pizza",
                weightGrams = 400,
                packageQuantity = 10,
                unit = "adet",
                priceMinor = 125_500L,
                currency = "TRY",
                notes = "Kullanıcı girişi",
            )
            val observed = repository.observeProducts().first()
            assertEquals(1, observed.size)
            assertEquals(product.id, observed.single().id)
            assertEquals("Test Donuk Pizza", observed.single().name)
            assertEquals(125_500L, observed.single().priceMinor)
            assertEquals("TRY", observed.single().currency)
            assertEquals(400, observed.single().weightGrams)
            assertEquals(10, observed.single().packageQuantity)
            repository.deleteProduct(product.id)
            assertTrue(repository.observeProducts().first().isEmpty())
        } finally {
            database.close()
        }
    }
}

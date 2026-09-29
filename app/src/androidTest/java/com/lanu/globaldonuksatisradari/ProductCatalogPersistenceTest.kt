package com.lanu.globaldonuksatisradari

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProductCatalogPersistenceTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val preferences by lazy {
        context.getSharedPreferences("lanu_product_catalog", Context.MODE_PRIVATE)
    }

    @Before
    fun clearBefore() {
        preferences.edit().clear().commit()
    }

    @After
    fun clearAfter() {
        preferences.edit().clear().commit()
    }

    @Test
    fun legacyExternalVerifiedSource_isDowngradedOnLoad() {
        seedProduct(
            sourceUrl = "https://example.com/catalog",
            sourceVerifiedAtEpochMs = 1_000L,
        )

        val product = ProductCatalogRepository(context).products.value.single()

        assertEquals("https://example.com/catalog", product.sourceUrl)
        assertNull(product.sourceVerifiedAtEpochMs)
    }

    @Test
    fun officialGlobalDonukVerifiedSource_remainsVerifiedOnLoad() {
        seedProduct(
            sourceUrl = "https://www.globaldonukgida.com/catalog",
            sourceVerifiedAtEpochMs = 2_000L,
        )

        val product = ProductCatalogRepository(context).products.value.single()

        assertEquals("https://www.globaldonukgida.com/catalog", product.sourceUrl)
        assertEquals(2_000L, product.sourceVerifiedAtEpochMs)
    }

    private fun seedProduct(sourceUrl: String, sourceVerifiedAtEpochMs: Long) {
        val item = JSONObject().apply {
            put("id", "legacy-product")
            put("name", "Legacy ürün")
            put("category", "")
            put("unit", "Adet")
            put("priceMinor", 0L)
            put("currency", "TRY")
            put("sourceUrl", sourceUrl)
            put("sourceVerifiedAtEpochMs", sourceVerifiedAtEpochMs)
            put("updatedAtEpochMs", 1L)
        }
        preferences.edit().putString("products", JSONArray().put(item).toString()).commit()
    }
}

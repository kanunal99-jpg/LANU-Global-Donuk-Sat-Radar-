package com.lanu.globaldonuksatisradari

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProductMediaStoreInstrumentationTest {
    @Test
    fun productCatalogPersistsImageSourceType() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val repository = ProductCatalogRepository(context)
        val unique = "image-source-" + System.nanoTime()
        val product = repository.upsert(
            id = unique,
            name = "Kaynak Tipi Test",
            category = "Test",
            unit = "Adet",
            priceMinor = 100,
            currency = "TRY",
            note = null,
            imageUrl = "http://example.com/test-image.jpg",
            imageSource = ProductImageSource.URL,
        )

        assertEquals("https://example.com/test-image.jpg", product.imageUrl)
        assertEquals(ProductImageSource.URL, product.imageSource)

        val reloaded = ProductCatalogRepository(context).products.value.first { it.id == unique }
        assertEquals(ProductImageSource.URL, reloaded.imageSource)
        assertEquals("https://example.com/test-image.jpg", reloaded.imageUrl)

        repository.delete(unique)
    }

    @Test
    fun cameraTargetFinalizesAsPrivateValidatedFileReference() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val target = ProductMediaStore.createCameraTarget(context)
        target.file.writeBytes(byteArrayOf(1, 2, 3, 4, 5))

        val reference = ProductMediaStore.finalizeCameraImage(context, target)
        ProductMediaStore.validateReference(context, reference)

        val file = File(java.net.URI(reference))
        assertTrue(reference.startsWith("file:"))
        assertTrue(file.exists())

        ProductMediaStore.removeLocalImage(context, reference)
        assertFalse(file.exists())
    }
}

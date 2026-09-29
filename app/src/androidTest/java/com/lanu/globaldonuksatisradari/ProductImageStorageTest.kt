package com.lanu.globaldonuksatisradari

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class ProductImageStorageTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test
    fun galleryImportCopiesSelectedImageIntoPrivatePersistentStorage() {
        val storage = ProductImageStorage(context)
        val source = storage.createCameraCapture()
        source.file.writeBytes(byteArrayOf(0x01, 0x02, 0x03, 0x04))

        val imported = storage.importFromPicker(source.uri)

        assertTrue(imported.startsWith("file:"))
        assertTrue(storage.isOwned(imported))
        assertTrue(File(requireNotNull(android.net.Uri.parse(imported).path)).exists())
        storage.deleteOwned(imported)
        storage.discardCameraCapture(source)
    }

    @Test
    fun cameraCaptureFinalizationPersistsAndCleanupRemovesOwnedFile() {
        val storage = ProductImageStorage(context)
        val capture = storage.createCameraCapture()
        capture.file.writeBytes(byteArrayOf(0x11, 0x22, 0x33, 0x44))

        val persisted = storage.finalizeCameraCapture(capture)

        assertFalse(capture.file.exists())
        assertTrue(storage.isOwned(persisted))
        storage.deleteOwned(persisted)
        assertFalse(storage.isOwned(persisted))
    }
}

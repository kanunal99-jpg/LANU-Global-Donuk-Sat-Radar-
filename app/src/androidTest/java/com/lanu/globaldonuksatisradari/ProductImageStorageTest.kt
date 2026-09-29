package com.lanu.globaldonuksatisradari

import androidx.core.content.FileProvider
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

    @Test
    fun cameraCancellationDiscardsTemporaryCapture() {
        val storage = ProductImageStorage(context)
        val capture = storage.createCameraCapture()
        assertTrue(capture.file.exists())

        storage.discardCameraCapture(capture)

        assertFalse(capture.file.exists())
    }

    @Test
    fun unsupportedMimeIsRejectedWithoutPersistentCopy() {
        val storage = ProductImageStorage(context)
        val cameraDir = File(context.cacheDir, "product_camera").apply { mkdirs() }
        val unsupported = File(cameraDir, "unsupported.txt").apply { writeText("not-an-image") }
        val uri = FileProvider.getUriForFile(
            context,
            context.packageName + ".fileprovider",
            unsupported,
        )

        val rejected = runCatching { storage.importFromPicker(uri) }.exceptionOrNull()

        assertTrue(rejected is IllegalArgumentException)
        unsupported.delete()
    }

    @Test
    fun oversizedCameraImageIsRejectedAndDoesNotCreateOwnedFile() {
        val storage = ProductImageStorage(context)
        val capture = storage.createCameraCapture()
        capture.file.outputStream().use { output ->
            val chunk = ByteArray(1024 * 1024)
            repeat(13) { output.write(chunk) }
        }

        val rejected = runCatching { storage.finalizeCameraCapture(capture) }.exceptionOrNull()

        assertTrue(rejected is IllegalArgumentException)
        assertFalse(storage.isOwned(android.net.Uri.fromFile(capture.file).toString()))
        storage.discardCameraCapture(capture)
    }
}

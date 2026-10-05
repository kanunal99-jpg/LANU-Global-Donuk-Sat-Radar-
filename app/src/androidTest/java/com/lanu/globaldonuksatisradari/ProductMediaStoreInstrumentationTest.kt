package com.lanu.globaldonuksatisradari

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProductMediaStoreInstrumentationTest {
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

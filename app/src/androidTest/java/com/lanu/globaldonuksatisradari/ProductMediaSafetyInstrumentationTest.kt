package com.lanu.globaldonuksatisradari

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProductMediaSafetyInstrumentationTest {
    @Test(expected = IllegalArgumentException::class)
    fun emptyCameraFileIsRejected() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val target = ProductMediaStore.createCameraTarget(context)
        try {
            ProductMediaStore.finalizeCameraImage(context, target)
        } finally {
            target.file.delete()
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun oversizedCameraFileIsRejected() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val target = ProductMediaStore.createCameraTarget(context)
        try {
            java.io.RandomAccessFile(target.file, "rw").use {
                it.setLength(ProductMediaStore.MAX_IMAGE_BYTES + 1L)
            }
            ProductMediaStore.finalizeCameraImage(context, target)
        } finally {
            target.file.delete()
        }
    }
}

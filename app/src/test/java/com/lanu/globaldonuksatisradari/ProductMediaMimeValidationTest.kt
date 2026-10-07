package com.lanu.globaldonuksatisradari

import org.junit.Assert.assertEquals
import org.junit.Test

class ProductMediaMimeValidationTest {
    @Test
    fun supportedImageMimeTypesAreNormalized() {
        assertEquals("image/jpeg", ProductMediaStore.requireSupportedImageMime(" IMAGE/JPEG "))
        assertEquals("image/png", ProductMediaStore.requireSupportedImageMime("image/png"))
        assertEquals("image/webp", ProductMediaStore.requireSupportedImageMime("image/webp"))
        assertEquals("image/gif", ProductMediaStore.requireSupportedImageMime("image/gif"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun unsupportedImageMimeIsRejected() {
        ProductMediaStore.requireSupportedImageMime("image/svg+xml")
    }
}

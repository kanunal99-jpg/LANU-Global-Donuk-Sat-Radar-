package com.lanu.globaldonuksatisradari

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProductMediaValidationTest {
    @Test
    fun httpImageUrlIsNormalizedToHttps() {
        assertEquals(
            "https://example.com/image.jpg",
            ProductMediaValidation.normalizeImageReference(" http://example.com/image.jpg "),
        )
    }

    @Test
    fun httpsImageUrlStaysHttps() {
        assertEquals(
            "https://example.com/image.jpg",
            ProductMediaValidation.normalizeImageReference("https://example.com/image.jpg"),
        )
    }

    @Test
    fun blankImageReferenceBecomesNull() {
        assertNull(ProductMediaValidation.normalizeImageReference("  "))
    }

    @Test(expected = IllegalArgumentException::class)
    fun unsupportedImageSchemeIsRejected() {
        ProductMediaValidation.normalizeImageReference("ftp://example.com/image.jpg")
    }
}

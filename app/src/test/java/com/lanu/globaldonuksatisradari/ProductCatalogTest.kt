package com.lanu.globaldonuksatisradari

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ProductCatalogTest {
    @Test
    fun parsesTurkishPriceWithThousandsSeparator() {
        assertEquals(125050L, ProductPrice.parseToMinor("1.250,50"))
    }

    @Test
    fun parsesSimpleDecimalPrice() {
        assertEquals(9999L, ProductPrice.parseToMinor("99,99"))
    }

    @Test
    fun formatsPriceForCatalog() {
        assertEquals("1.250,50 TRY", ProductPrice.formatMinor(125050L, "TRY"))
    }

    @Test
    fun productSupportsOfficialDescriptionPhotoAndSourceMetadata() {
        val product = CatalogProduct(
            id = "global-demo",
            name = "Resmî katalog kaydı",
            category = "Pişmiş donuk yemek",
            unit = "Porsiyon",
            priceMinor = 0L,
            currency = "TRY",
            note = null,
            description = "Kaynakta yayınlanan ürün açıklaması.",
            imageUrl = "https://globaldonukgida.com/example.jpg",
            sourceUrl = "https://globaldonukgida.com/",
            sourceVerifiedAtEpochMs = 1_000L,
            updatedAtEpochMs = 1_000L,
        )

        assertEquals("https://globaldonukgida.com/example.jpg", product.imageUrl)
        assertEquals("https://globaldonukgida.com/", product.sourceUrl)
        assertEquals(1_000L, product.sourceVerifiedAtEpochMs)
    }

    @Test
    fun rejectsNonHttpsProductImageUrl() {
        assertThrows(IllegalArgumentException::class.java) {
            ProductMediaValidation.requireHttpsUrl("http://example.com/image.jpg", "Ürün fotoğrafı URL")
        }
    }

    @Test
    fun rejectsNonHttpsSourceUrl() {
        assertThrows(IllegalArgumentException::class.java) {
            ProductMediaValidation.requireHttpsUrl("http://globaldonukgida.com/", "Kaynak URL")
        }
    }

    @Test
    fun rejectsNegativePrice() {
        assertThrows(IllegalArgumentException::class.java) {
            ProductPrice.parseToMinor("-10")
        }
    }
}

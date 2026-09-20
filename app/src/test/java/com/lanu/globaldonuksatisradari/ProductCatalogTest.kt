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
    fun rejectsNegativePrice() {
        assertThrows(IllegalArgumentException::class.java) {
            ProductPrice.parseToMinor("-10")
        }
    }
}

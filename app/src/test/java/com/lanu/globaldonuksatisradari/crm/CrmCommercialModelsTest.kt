package com.lanu.globaldonuksatisradari.crm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class CrmCommercialModelsTest {
    @Test
    fun lineTotal_supportsFractionalQuantityAndDiscountWithoutFloatingPointDrift() {
        // 2.500 units × 125.50 TRY × 10% = 282.375 TRY -> 282.38 TRY (minor units 28238)
        assertEquals(
            28_238L,
            CrmCommercialMath.lineTotalMinor(
                quantityMilli = 2_500L,
                unitPriceMinor = 12_550L,
                discountBasisPoints = 1_000,
            ),
        )
    }

    @Test
    fun lineTotal_rejectsInvalidCommercialInputs() {
        assertThrows(IllegalArgumentException::class.java) {
            CrmCommercialMath.lineTotalMinor(0L, 100L, 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            CrmCommercialMath.lineTotalMinor(1_000L, -1L, 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            CrmCommercialMath.lineTotalMinor(1_000L, 100L, 10_001)
        }
    }

    @Test
    fun productSnapshot_requiresNameAndUnit() {
        assertThrows(IllegalArgumentException::class.java) {
            CrmCommercialMath.validateSnapshot(" ", "Adet")
        }
        assertThrows(IllegalArgumentException::class.java) {
            CrmCommercialMath.validateSnapshot("Ürün", " ")
        }
        CrmCommercialMath.validateSnapshot("Doğrulanmış Ürün", "Koli")
    }
}

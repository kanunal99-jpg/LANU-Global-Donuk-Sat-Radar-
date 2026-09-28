package com.lanu.globaldonuksatisradari.crm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class CrmContactValidatorTest {
    @Test
    fun contactFields_areNormalizedBeforePersistence() {
        assertEquals("Ayşe Yılmaz", CrmContactValidator.normalizeName("  Ayşe   Yılmaz  "))
        assertEquals("satın alma", CrmContactValidator.normalizeOptionalText("  satın   alma "))
        assertEquals("ayse@example.com", CrmContactValidator.normalizeEmail(" AYSE@Example.COM "))
        assertEquals("+90 532 123 45 67", CrmContactValidator.normalizePhone(" +90 532 123 45 67 "))
        assertNull(CrmContactValidator.normalizeOptionalText("  "))
    }

    @Test
    fun invalidContactFields_areRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            CrmContactValidator.normalizeName("   ")
        }
        assertThrows(IllegalArgumentException::class.java) {
            CrmContactValidator.normalizeEmail("not-an-email")
        }
        assertThrows(IllegalArgumentException::class.java) {
            CrmContactValidator.normalizePhone("123")
        }
    }
}

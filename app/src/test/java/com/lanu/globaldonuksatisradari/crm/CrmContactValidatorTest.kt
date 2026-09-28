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
        assertEquals("+905321234567", CrmContactValidator.normalizePhone(" +90 532 123 45 67 "))
        assertEquals("02161234567", CrmContactValidator.normalizePhone("(0216) 123 45 67"))
        assertNull(CrmContactValidator.normalizeOptionalText("  "))
    }

    @Test
    fun controlCharacters_areSanitizedBeforePersistence() {
        assertEquals("Ayşe Yılmaz", CrmContactValidator.normalizeName("Ayşe\u0000  Yılmaz"))
        assertEquals("Satın Alma", CrmContactValidator.normalizeOptionalText("Satın\nAlma"))
        assertEquals("ayse@example.com", CrmContactValidator.normalizeEmail("ayse\u0000@example.com"))
        assertEquals("+905321234567", CrmContactValidator.normalizePhone("+90 532 123 45 67\u0000"))
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
            CrmContactValidator.normalizeEmail("ayse @example.com")
        }
        assertThrows(IllegalArgumentException::class.java) {
            CrmContactValidator.normalizeEmail("ayse@@example.com")
        }
        assertThrows(IllegalArgumentException::class.java) {
            CrmContactValidator.normalizeEmail(".ayse@example.com")
        }
        assertThrows(IllegalArgumentException::class.java) {
            CrmContactValidator.normalizeEmail("ayse@example..com")
        }
        assertThrows(IllegalArgumentException::class.java) {
            CrmContactValidator.normalizeEmail("ayse@-example.com")
        }
        assertThrows(IllegalArgumentException::class.java) {
            CrmContactValidator.normalizePhone("123")
        }
        assertThrows(IllegalArgumentException::class.java) {
            CrmContactValidator.normalizePhone("+90 532 123 45 67 ext")
        }
        assertThrows(IllegalArgumentException::class.java) {
            CrmContactValidator.normalizePhone("90+5321234567")
        }
        assertThrows(IllegalArgumentException::class.java) {
            CrmContactValidator.normalizePhone("++905321234567")
        }
    }

    @Test
    fun oversizedContactFields_areRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            CrmContactValidator.normalizeName("A".repeat(121))
        }
        assertThrows(IllegalArgumentException::class.java) {
            CrmContactValidator.normalizeOptionalText("R".repeat(121))
        }
        assertThrows(IllegalArgumentException::class.java) {
            CrmContactValidator.normalizeEmail("a".repeat(245) + "@example.com")
        }
        assertThrows(IllegalArgumentException::class.java) {
            CrmContactValidator.normalizeEmail("a".repeat(65) + "@example.com")
        }
    }
}

package com.lanu.globaldonuksatisradari

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IstanbulDistrictsTest {
    @Test
    fun containsExactly39UniqueDistrictsAndSeparatesSides() {
        assertEquals(39, IstanbulDistricts.ALL.size)
        assertEquals(39, IstanbulDistricts.ALL.distinct().size)
        assertEquals(14, IstanbulDistricts.ANATOLIAN.size)
        assertEquals(25, IstanbulDistricts.EUROPEAN.size)
        assertTrue(IstanbulDistricts.ANATOLIAN.contains("Kadıköy"))
        assertTrue(IstanbulDistricts.ANATOLIAN.contains("Pendik"))
        assertTrue(IstanbulDistricts.EUROPEAN.contains("Şişli"))
        assertTrue(IstanbulDistricts.EUROPEAN.contains("Bakırköy"))
        assertFalse(IstanbulDistricts.EUROPEAN.contains("Kadıköy"))
        assertFalse(IstanbulDistricts.ANATOLIAN.contains("Şişli"))
        assertTrue(IstanbulDistricts.ANATOLIAN.toSet().intersect(IstanbulDistricts.EUROPEAN.toSet()).isEmpty())
        assertEquals(
            IstanbulDistricts.ALL.toSet(),
            (IstanbulDistricts.ANATOLIAN + IstanbulDistricts.EUROPEAN).toSet(),
        )
    }
}

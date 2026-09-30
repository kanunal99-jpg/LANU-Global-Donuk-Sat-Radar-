package com.lanu.globaldonuksatisradari

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IstanbulDistrictsTest {
    @Test
    fun containsExactly39UniqueDistricts() {
        assertEquals(39, IstanbulDistricts.ALL.size)
        assertEquals(39, IstanbulDistricts.ALL.distinct().size)
        assertTrue(
            IstanbulDistricts.ALL.containsAll(
                listOf(
                    "Adalar",
                    "Arnavutköy",
                    "Ataşehir",
                    "Kadıköy",
                    "Pendik",
                    "Üsküdar",
                    "Zeytinburnu",
                )
            )
        )
        assertTrue(IstanbulDistricts.ALL.all { it.isNotBlank() })
    }

    @Test
    fun sidesPartitionAllDistrictsWithoutOverlap() {
        assertEquals(14, IstanbulDistricts.ANATOLIAN.size)
        assertEquals(25, IstanbulDistricts.EUROPEAN.size)
        assertTrue(IstanbulDistricts.ANATOLIAN.intersect(IstanbulDistricts.EUROPEAN.toSet()).isEmpty())
        assertEquals(IstanbulDistricts.ALL.toSet(), (IstanbulDistricts.ANATOLIAN + IstanbulDistricts.EUROPEAN).toSet())
        assertEquals(IstanbulDistricts.ANATOLIAN, IstanbulDistricts.districtsFor(IstanbulDistricts.ANATOLIAN_SIDE))
        assertEquals(IstanbulDistricts.EUROPEAN, IstanbulDistricts.districtsFor(IstanbulDistricts.EUROPEAN_SIDE))
    }
}

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
}

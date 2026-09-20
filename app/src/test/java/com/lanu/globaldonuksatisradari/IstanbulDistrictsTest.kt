package com.lanu.globaldonuksatisradari

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IstanbulDistrictsTest {
    @Test
    fun containsExactly39UniqueDistricts() {
        assertEquals(39, IstanbulDistricts.all.size)
        assertEquals(39, IstanbulDistricts.all.distinct().size)
        assertTrue(
            IstanbulDistricts.all.containsAll(
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
        assertTrue(IstanbulDistricts.all.all { it.isNotBlank() })
    }
}

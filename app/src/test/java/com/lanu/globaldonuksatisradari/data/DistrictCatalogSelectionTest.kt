package com.lanu.globaldonuksatisradari.data

import org.junit.Assert.assertEquals
import org.junit.Test

class DistrictCatalogSelectionTest {
    @Test
    fun remoteListMustMatchCanonicalFallbackExactly() {
        val fallback = listOf("Kadıköy", "Pendik", "Üsküdar")

        assertEquals(
            fallback.sortedWith(String.CASE_INSENSITIVE_ORDER),
            selectVerifiedDistrictCatalog(
                candidate = listOf("Kadıköy", "Pendik"),
                fallback = fallback,
            ),
        )

        assertEquals(
            fallback.sortedWith(String.CASE_INSENSITIVE_ORDER),
            selectVerifiedDistrictCatalog(
                candidate = listOf("Kadıköy", "Pendik", "Şişli"),
                fallback = fallback,
            ),
        )

        assertEquals(
            fallback.sortedWith(String.CASE_INSENSITIVE_ORDER),
            selectVerifiedDistrictCatalog(
                candidate = listOf("Pendik", "Üsküdar", "Kadıköy"),
                fallback = fallback,
            ),
        )
    }
}

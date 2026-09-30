package com.lanu.globaldonuksatisradari.crm

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CrmRegionScopeTest {
    @Test
    fun cityOnlyScope_acceptsAnyDistrictInCanonicalCity() {
        assertTrue(matchesCrmRegion("İstanbul", "Kadıköy", "İstanbul", null))
        assertTrue(matchesCrmRegion("İstanbul", "Şişli", "İstanbul", null))
        assertFalse(matchesCrmRegion("Kocaeli", "Gebze", "İstanbul", null))
    }

    @Test
    fun districtScope_excludesOtherDistrictsAndCities() {
        assertTrue(matchesCrmRegion("İstanbul", "Kadıköy", "İstanbul", "Kadıköy"))
        assertFalse(matchesCrmRegion("İstanbul", "Pendik", "İstanbul", "Kadıköy"))
        assertFalse(matchesCrmRegion("Kocaeli", "Kadıköy", "İstanbul", "Kadıköy"))
    }
}

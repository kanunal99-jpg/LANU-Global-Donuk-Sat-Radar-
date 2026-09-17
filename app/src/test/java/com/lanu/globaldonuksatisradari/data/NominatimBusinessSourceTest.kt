package com.lanu.globaldonuksatisradari.data

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NominatimBusinessSourceTest {
    @Test
    fun `source contract is valid for user triggered search`() {
        assertTrue(NominatimBusinessSource.contract.validate().isSuccess)
        assertEquals(SourceAccessMethod.PUBLIC_SEARCH, NominatimBusinessSource.contract.accessMethod)
        assertEquals(false, NominatimBusinessSource.contract.supportsBulk)
        assertTrue("phone" in NominatimBusinessSource.contract.fieldNames)
        assertTrue("website" in NominatimBusinessSource.contract.fieldNames)
        assertTrue("opening_hours" in NominatimBusinessSource.contract.fieldNames)
    }

    @Test
    fun `query builder scopes search by city and district and requests optional source tags`() {
        val url = NominatimQueryBuilder.build("restoran", "İstanbul", "Kadıköy")
        assertContains(url, "format=jsonv2")
        assertContains(url, "addressdetails=1")
        assertContains(url, "extratags=1")
        assertContains(url, "namedetails=1")
        assertContains(url, "countrycodes=tr")
        assertContains(url, "%C4%B0stanbul")
        assertContains(url, "Kad%C4%B1k%C3%B6y")
        assertContains(url, "restoran")
    }

    @Test
    fun `blank query is rejected instead of becoming an area wide scan`() {
        kotlin.runCatching { NominatimQueryBuilder.build("", "İstanbul", null) }
            .onSuccess { error("blank query should not be accepted") }
    }
}

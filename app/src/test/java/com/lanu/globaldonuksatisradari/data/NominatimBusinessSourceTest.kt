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
    }

    @Test
    fun `query builder scopes search by city and district`() {
        val url = NominatimQueryBuilder.build("restoran", "İstanbul", "Kadıköy")
        assertContains(url, "format=jsonv2")
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

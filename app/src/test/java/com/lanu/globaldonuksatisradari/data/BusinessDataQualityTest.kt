package com.lanu.globaldonuksatisradari.data

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BusinessDataQualityTest {
    private val source = DataSourceDescriptor(
        id = "ito",
        name = "İstanbul Ticaret Odası Bilgi Bankası",
        publisher = "İstanbul Ticaret Odası",
        licenseOrTerms = "TERMS_PENDING_VERIFICATION",
        sourceUrl = "https://bilgibankasi.ito.org.tr/",
        lastVerifiedAtEpochMs = 1L,
    )

    @Test
    fun `unverified permission cannot pass source gate`() {
        val contract = BusinessSourceContract(
            descriptor = source,
            accessMethod = SourceAccessMethod.PUBLIC_SEARCH,
            scope = "ITO scope only",
            permittedUseVerified = false,
            supportsBulk = false,
            fieldNames = setOf("tradeName", "address"),
        )

        assertFalse(contract.validate().isSuccess)
    }

    @Test
    fun `verified permission requires configured access method`() {
        val contract = BusinessSourceContract(
            descriptor = source,
            accessMethod = SourceAccessMethod.NOT_CONFIGURED,
            scope = "verified scope",
            permittedUseVerified = true,
            supportsBulk = false,
            fieldNames = setOf("tradeName"),
        )

        assertFalse(contract.validate().isSuccess)
    }

    @Test
    fun `valid configured contract passes`() {
        val contract = BusinessSourceContract(
            descriptor = source,
            accessMethod = SourceAccessMethod.MANUAL_IMPORT,
            scope = "verified scope",
            permittedUseVerified = true,
            supportsBulk = false,
            fieldNames = setOf("tradeName", "address"),
        )

        assertTrue(contract.validate().isSuccess)
    }
}

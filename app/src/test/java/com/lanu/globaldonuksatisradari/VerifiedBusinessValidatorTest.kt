package com.lanu.globaldonuksatisradari

import com.lanu.globaldonuksatisradari.data.DataSourceDescriptor
import com.lanu.globaldonuksatisradari.data.VerifiedBusiness
import com.lanu.globaldonuksatisradari.data.VerifiedBusinessValidator
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VerifiedBusinessValidatorTest {
    private val source = DataSourceDescriptor(
        id = "source-1",
        name = "Test source",
        publisher = "Test publisher",
        licenseOrTerms = "Test terms",
        sourceUrl = "https://example.invalid/source",
        lastVerifiedAtEpochMs = 1L,
    )

    private fun business(source: DataSourceDescriptor = this.source) = VerifiedBusiness(
        id = "business-1",
        name = "Test business",
        city = "İstanbul",
        district = "Kadıköy",
        neighborhood = null,
        source = source,
        verifiedAtEpochMs = 1L,
    )

    @Test
    fun validRecordIsAccepted() {
        assertTrue(VerifiedBusinessValidator.validate(business()).isSuccess)
    }

    @Test
    fun missingLicenseTermsIsRejected() {
        val invalid = source.copy(licenseOrTerms = "")
        assertFalse(VerifiedBusinessValidator.validate(business(invalid)).isSuccess)
    }

    @Test
    fun nonHttpsSourceIsRejected() {
        val invalid = source.copy(sourceUrl = "http://example.invalid/source")
        assertFalse(VerifiedBusinessValidator.validate(business(invalid)).isSuccess)
    }

    @Test
    fun missingVerificationTimestampIsRejected() {
        assertFalse(VerifiedBusinessValidator.validate(business().copy(verifiedAtEpochMs = 0L)).isSuccess)
    }
}

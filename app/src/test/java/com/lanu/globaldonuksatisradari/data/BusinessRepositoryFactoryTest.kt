package com.lanu.globaldonuksatisradari.data

import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BusinessRepositoryFactoryTest {
    private val source = DataSourceDescriptor(
        id = "test-source",
        name = "Verified test source",
        publisher = "LANU test",
        licenseOrTerms = "TEST_VERIFIED",
        sourceUrl = "https://example.com/source",
        lastVerifiedAtEpochMs = 1L,
    )

    private val contract = BusinessSourceContract(
        descriptor = source,
        accessMethod = SourceAccessMethod.API,
        scope = "test scope",
        permittedUseVerified = true,
        supportsBulk = false,
        fieldNames = setOf("tradeName", "city", "district"),
    )

    private fun validBusiness() = VerifiedBusiness(
        id = "source-1",
        name = "Verified Business",
        city = "İstanbul",
        district = "Kadıköy",
        neighborhood = "Caferağa",
        source = source,
        verifiedAtEpochMs = 2L,
    )

    private fun <T> runSuspend(block: suspend () -> T): T {
        var outcome: Result<T>? = null
        block.startCoroutine(object : Continuation<T> {
            override val context = EmptyCoroutineContext
            override fun resumeWith(result: Result<T>) {
                outcome = result
            }
        })
        return requireNotNull(outcome).getOrThrow()
    }

    @Test
    fun `invalid records are filtered at adapter boundary`() {
        val invalid = validBusiness().copy(name = "")
        val adapter = object : BusinessSourceAdapter {
            override val contract = this@BusinessRepositoryFactoryTest.contract
            override suspend fun fetch(query: String, city: String, district: String?) =
                listOf(validBusiness(), invalid)
        }

        val repository = BusinessRepositoryFactory.create(contract, adapter)
        val result = runSuspend {
            repository.search("Verified", "İstanbul", "Kadıköy")
        }

        assertEquals(1, result.size)
        assertEquals("source-1", result.single().id)
    }

    @Test
    fun `unsafe contract cannot be wired to adapter`() {
        val unsafe = contract.copy(permittedUseVerified = false)
        val adapter = object : BusinessSourceAdapter {
            override val contract = unsafe
            override suspend fun fetch(query: String, city: String, district: String?) =
                listOf(validBusiness())
        }

        val repository = BusinessRepositoryFactory.create(unsafe, adapter)
        val result = runSuspend {
            repository.search("Verified", "İstanbul")
        }

        assertTrue(result.isEmpty())
    }
}

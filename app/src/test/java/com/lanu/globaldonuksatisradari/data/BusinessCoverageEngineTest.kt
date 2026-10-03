package com.lanu.globaldonuksatisradari.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class BusinessCoverageEngineTest {
    private val primary = DataSourceDescriptor(
        id = "primary",
        name = "Primary",
        publisher = "Test",
        licenseOrTerms = "Test terms",
        sourceUrl = "https://example.com/source",
        lastVerifiedAtEpochMs = 1L,
    )

    private val fallback = DataSourceDescriptor(
        id = "fallback",
        name = "Fallback",
        publisher = "Test",
        licenseOrTerms = "Test terms",
        sourceUrl = "https://example.com/fallback",
        lastVerifiedAtEpochMs = 1L,
    )

    private fun business(id: String, source: DataSourceDescriptor = primary) = VerifiedBusiness(
        id = id,
        name = id,
        city = "İstanbul",
        district = "Kadıköy",
        neighborhood = null,
        source = source,
        verifiedAtEpochMs = 2L,
    )

    private val scope = CoverageScope(
        city = "İstanbul",
        district = "Kadıköy",
        category = "restaurant",
    )

    @Test
    fun fallbackIsUsedAfterPrimaryFailure() = runTest {
        val engine = BusinessCoverageEngine(
            sources = listOf(
                CoverageSource { Result.failure(IllegalStateException("primary down")) },
                CoverageSource {
                    Result.success(CoverageSourceResult(fallback, listOf(business("b1", fallback)), 10L))
                },
            ),
            nowEpochMs = { 20L },
        )

        val result = engine.scan(scope)

        assertEquals("fallback", result.selectedSourceId)
        assertEquals(1, result.businesses.size)
        assertEquals(2, result.attemptedSourceCount)
        assertTrue(result.attempts.any { !it.success })
        assertTrue(result.attempts.any { it.success && it.sourceId == "fallback" })
    }

    @Test
    fun targetedEmptyPrimaryUsesFallbackSource() = runTest {
        val engine = BusinessCoverageEngine(
            sources = listOf(
                CoverageSource {
                    Result.success(CoverageSourceResult(primary, emptyList(), 10L))
                },
                CoverageSource {
                    Result.success(CoverageSourceResult(fallback, listOf(business("b1", fallback)), 11L))
                },
            ),
            nowEpochMs = { 20L },
        )

        val result = engine.scan(scope)

        assertEquals("fallback", result.selectedSourceId)
        assertEquals(1, result.businesses.size)
        assertEquals(2, result.successfulSourceCount)
    }

    @Test
    fun broadScanMergesComplementarySuccessfulSources() = runTest {
        val engine = BusinessCoverageEngine(
            sources = listOf(
                CoverageSource {
                    Result.success(CoverageSourceResult(primary, listOf(business("b1")), 10L))
                },
                CoverageSource {
                    Result.success(CoverageSourceResult(fallback, listOf(business("b2", fallback)), 11L))
                },
            ),
            nowEpochMs = { 20L },
        )

        val result = engine.scan(scope.copy(category = "*"))

        assertEquals("multi-source", result.selectedSourceId)
        assertEquals(setOf("b1", "b2"), result.businesses.map { it.id }.toSet())
        assertEquals(2, result.successfulSourceCount)
        assertEquals(2, result.attemptedSourceCount)
    }

    @Test
    fun broadScanStillUsesFallbackWhenFirstBulkSourceFails() = runTest {
        val engine = BusinessCoverageEngine(
            sources = listOf(
                CoverageSource { Result.failure(IllegalStateException("snapshot unavailable")) },
                CoverageSource {
                    Result.success(CoverageSourceResult(fallback, listOf(business("b1", fallback)), 11L))
                },
            ),
            nowEpochMs = { 20L },
        )

        val result = engine.scan(scope.copy(category = "*"))

        assertEquals("fallback", result.selectedSourceId)
        assertEquals(1, result.businesses.size)
        assertEquals(2, result.attemptedSourceCount)
    }

    @Test
    fun healthUsesSourceAndRecordIds() = runTest {
        val engine = BusinessCoverageEngine(
            sources = listOf(
                CoverageSource {
                    Result.success(CoverageSourceResult(primary, listOf(business("same")), 10L))
                },
            ),
            nowEpochMs = { 20L },
        )

        val results = engine.scanAll(listOf(scope, scope.copy(district = "Beşiktaş")))
        val health = engine.health(results)

        assertEquals(2, health.requestedScopes)
        assertEquals(2, health.scopesWithData)
        assertEquals(1, health.uniqueBusinessesObserved)
        assertEquals(100, health.dataCoveragePercent)
    }
}

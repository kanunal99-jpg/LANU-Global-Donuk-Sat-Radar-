package com.lanu.globaldonuksatisradari.data

data class CoverageScope(
    val country: String = "Türkiye",
    val city: String,
    val district: String,
    val neighborhood: String? = null,
    val category: String,
)

data class CoverageSourceResult(
    val source: DataSourceDescriptor,
    val businesses: List<VerifiedBusiness>,
    val completedAtEpochMs: Long,
)

data class CoverageAttempt(
    val sourceId: String,
    val success: Boolean,
    val resultCount: Int,
    val errorCode: String? = null,
)

data class CoverageScanResult(
    val scope: CoverageScope,
    val businesses: List<VerifiedBusiness>,
    val attempts: List<CoverageAttempt>,
    val selectedSourceId: String?,
    val completedAtEpochMs: Long,
) {
    val hasData: Boolean get() = businesses.isNotEmpty()
    val attemptedSourceCount: Int get() = attempts.size
    val successfulSourceCount: Int get() = attempts.count { it.success }
}

data class CoverageHealth(
    val requestedScopes: Int,
    val completedScopes: Int,
    val scopesWithData: Int,
    val scopesWithoutData: Int,
    val uniqueBusinessesObserved: Int,
    val sourceSuccessCount: Int,
) {
    val completionPercent: Int
        get() = if (requestedScopes == 0) 0 else completedScopes * 100 / requestedScopes

    val dataCoveragePercent: Int
        get() = if (requestedScopes == 0) 0 else scopesWithData * 100 / requestedScopes
}

fun interface CoverageSource {
    suspend fun scan(scope: CoverageScope): Result<CoverageSourceResult>
}

class BusinessCoverageEngine(
    private val sources: List<CoverageSource>,
    private val nowEpochMs: () -> Long,
) {
    init {
        require(sources.isNotEmpty()) { "En az bir coverage kaynağı gerekli" }
    }

    suspend fun scan(scope: CoverageScope): CoverageScanResult {
        val attempts = mutableListOf<CoverageAttempt>()
        val broadBusinesses = mutableListOf<VerifiedBusiness>()
        val broadSuccessfulSources = mutableListOf<String>()
        var broadCompletedAtEpochMs = 0L

        for (source in sources) {
            val result = try {
                source.scan(scope)
            } catch (error: kotlinx.coroutines.CancellationException) {
                throw error
            } catch (error: Exception) {
                attempts += CoverageAttempt(
                    sourceId = "unknown",
                    success = false,
                    resultCount = 0,
                    errorCode = error::class.simpleName ?: "SOURCE_EXCEPTION",
                )
                continue
            }

            if (result.isSuccess) {
                val value = result.getOrThrow()
                attempts += CoverageAttempt(
                    sourceId = value.source.id,
                    success = true,
                    resultCount = value.businesses.size,
                )

                if (scope.category == "*") {
                    broadSuccessfulSources += value.source.id
                    broadBusinesses += value.businesses
                    broadCompletedAtEpochMs = maxOf(
                        broadCompletedAtEpochMs,
                        value.completedAtEpochMs,
                    )
                    // A broad inventory must not stop at the first provider:
                    // Overture, OSM and future bulk sources are complementary.
                    continue
                }

                if (value.businesses.isNotEmpty()) {
                    return CoverageScanResult(
                        scope = scope,
                        businesses = value.businesses,
                        attempts = attempts,
                        selectedSourceId = value.source.id,
                        completedAtEpochMs = value.completedAtEpochMs,
                    )
                }

                // A targeted empty result may be incomplete. Try the next real source
                // instead of treating zero matches from one provider as authoritative.
                continue
            }

            attempts += CoverageAttempt(
                sourceId = "source-${attempts.size + 1}",
                success = false,
                resultCount = 0,
                errorCode = result.exceptionOrNull()?.javaClass?.simpleName ?: "SOURCE_ERROR",
            )
        }

        if (scope.category == "*" && broadSuccessfulSources.isNotEmpty()) {
            return CoverageScanResult(
                scope = scope,
                businesses = BusinessDeduplication.deduplicateCrossSource(broadBusinesses),
                attempts = attempts,
                selectedSourceId = broadSuccessfulSources.singleOrNull() ?: "multi-source",
                completedAtEpochMs = broadCompletedAtEpochMs.takeIf { it > 0L } ?: nowEpochMs(),
            )
        }

        return CoverageScanResult(
            scope = scope,
            businesses = emptyList(),
            attempts = attempts,
            selectedSourceId = null,
            completedAtEpochMs = nowEpochMs(),
        )
    }

    suspend fun scanAll(scopes: List<CoverageScope>): List<CoverageScanResult> =
        scopes.map { scan(it) }

    fun health(results: List<CoverageScanResult>): CoverageHealth {
        val uniqueIds = results.flatMap { it.businesses }
            .map { it.source.id + ":" + it.id }
            .toSet()
        return CoverageHealth(
            requestedScopes = results.size,
            completedScopes = results.count { it.completedAtEpochMs > 0 },
            scopesWithData = results.count { it.hasData },
            scopesWithoutData = results.count { !it.hasData },
            uniqueBusinessesObserved = uniqueIds.size,
            sourceSuccessCount = results.sumOf { it.successfulSourceCount },
        )
    }
}

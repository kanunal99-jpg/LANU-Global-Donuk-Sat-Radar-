package com.lanu.globaldonuksatisradari.data

data class CoverageScope(
    val country: String = "Türkiye",
    val city: String,
    val district: String,
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
                return CoverageScanResult(
                    scope = scope,
                    businesses = value.businesses,
                    attempts = attempts,
                    selectedSourceId = value.source.id,
                    completedAtEpochMs = value.completedAtEpochMs,
                )
            }

            attempts += CoverageAttempt(
                sourceId = "source-${attempts.size + 1}",
                success = false,
                resultCount = 0,
                errorCode = result.exceptionOrNull()?.javaClass?.simpleName ?: "SOURCE_ERROR",
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

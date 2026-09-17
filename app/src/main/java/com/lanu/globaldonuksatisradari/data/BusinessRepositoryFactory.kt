package com.lanu.globaldonuksatisradari.data

/**
 * Single composition point for business-data access.
 * Until an official source contract is verified and configured, the app must stay empty rather than
 * silently falling back to fabricated or unlicensed records.
 */
object BusinessRepositoryFactory {
    fun create(contract: BusinessSourceContract?): BusinessRepository =
        if (contract == null || !contract.validate().isSuccess) {
            EmptyBusinessRepository()
        } else {
            EmptyBusinessRepository()
        }

    /**
     * Production wiring point for a verified source adapter.
     * The adapter is never trusted directly: contract validation and per-record domain validation
     * remain mandatory at this boundary.
     */
    fun create(
        contract: BusinessSourceContract?,
        adapter: BusinessSourceAdapter?,
    ): BusinessRepository {
        if (contract == null || adapter == null) return EmptyBusinessRepository()
        if (adapter.contract != contract) return EmptyBusinessRepository()
        if (!contract.validate().isSuccess) return EmptyBusinessRepository()
        return AdapterBusinessRepository(adapter)
    }

    private class AdapterBusinessRepository(
        private val adapter: BusinessSourceAdapter,
    ) : BusinessRepository {
        override suspend fun search(
            query: String,
            city: String,
            district: String?,
        ): List<VerifiedBusiness> = adapter.fetchValidated(query, city, district)
    }
}

/** Normalized ingestion boundary. Adapters may only emit domain records through validation. */
interface BusinessSourceAdapter {
    val contract: BusinessSourceContract

    suspend fun fetch(query: String, city: String, district: String? = null): List<VerifiedBusiness>
}

/**
 * Defensive adapter wrapper: invalid records are rejected instead of entering the app domain.
 * The source contract itself must also pass validation before production wiring.
 */
suspend fun BusinessSourceAdapter.fetchValidated(
    query: String,
    city: String,
    district: String? = null,
): List<VerifiedBusiness> {
    if (!contract.validate().isSuccess) return emptyList()
    return fetch(query, city, district).mapNotNull { business ->
        VerifiedBusinessValidator.validate(business).getOrNull()
    }
}

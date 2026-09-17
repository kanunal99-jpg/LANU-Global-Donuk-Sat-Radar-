package com.lanu.globaldonuksatisradari.data

/**
 * Single composition point for business-data access.
 * Until an official source contract is verified and configured, the app must stay empty rather than
 * silently falling back to fabricated or unlicensed records.
 */
object BusinessRepositoryFactory {
    fun create(contract: BusinessSourceContract?): BusinessRepository {
        if (contract == null) return EmptyBusinessRepository()
        if (!contract.permittedUseVerified) return EmptyBusinessRepository()
        if (contract.accessMethod == SourceAccessMethod.NOT_CONFIGURED) return EmptyBusinessRepository()
        return EmptyBusinessRepository()
    }
}

/** Normalized ingestion boundary; adapters must validate before creating domain records. */
interface BusinessSourceAdapter {
    val contract: BusinessSourceContract

    suspend fun fetch(query: String, city: String, district: String? = null): List<VerifiedBusiness>
}

fun BusinessSourceAdapter.fetchValidated(
    query: String,
    city: String,
    district: String? = null,
): List<VerifiedBusiness> {
    error("fetchValidated must be implemented with a suspend-aware adapter wrapper")
}

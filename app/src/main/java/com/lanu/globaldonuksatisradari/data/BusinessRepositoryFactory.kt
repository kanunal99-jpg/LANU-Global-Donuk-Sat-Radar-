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

/** Normalized ingestion boundary. Adapters may only emit validated domain records. */
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
    contract.validate().getOrElse { return emptyList() }
    return fetch(query, city, district).mapNotNull { business ->
        VerifiedBusinessValidator.validate(business).getOrNull()
    }
}

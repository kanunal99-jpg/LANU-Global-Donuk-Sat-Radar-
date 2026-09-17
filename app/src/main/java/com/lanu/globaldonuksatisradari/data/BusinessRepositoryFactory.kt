package com.lanu.globaldonuksatisradari.data

/** Single composition point for business-data access. */
object BusinessRepositoryFactory {
    fun create(contract: BusinessSourceContract?): BusinessRepository = EmptyBusinessRepository()

    /** Production wiring for a verified adapter; invalid records never enter the domain. */
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
        ): List<VerifiedBusiness> = BusinessDeduplication.deduplicate(adapter.fetchValidated(query, city, district))
    }
}

interface BusinessSourceAdapter {
    val contract: BusinessSourceContract
    suspend fun fetch(query: String, city: String, district: String? = null): List<VerifiedBusiness>
}

/** Defensive ingestion boundary: source contract + per-record validation are mandatory. */
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

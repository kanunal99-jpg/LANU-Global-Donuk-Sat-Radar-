package com.lanu.globaldonuksatisradari.data

enum class BusinessFreshnessState {
    FRESH,
    STALE,
    INVALID,
}

/**
 * Deterministic freshness policy. Freshness never changes the source quality classification.
 */
object BusinessFreshness {
    fun classify(
        business: VerifiedBusiness,
        nowEpochMs: Long,
        maxAgeMs: Long,
    ): BusinessFreshnessState {
        if (nowEpochMs <= 0L || maxAgeMs <= 0L) return BusinessFreshnessState.INVALID
        if (business.verifiedAtEpochMs > nowEpochMs) return BusinessFreshnessState.INVALID
        return if (nowEpochMs - business.verifiedAtEpochMs > maxAgeMs) {
            BusinessFreshnessState.STALE
        } else {
            BusinessFreshnessState.FRESH
        }
    }
}

/**
 * Removes exact duplicates from one verified source without guessing cross-source identity.
 * Business ids are required by VerifiedBusinessValidator, so the key is deterministic.
 */
object BusinessDeduplicator {
    fun deduplicate(records: List<VerifiedBusiness>): List<VerifiedBusiness> =
        records.distinctBy { record ->
            "${record.source.id}:${record.id}"
        }
}

/**
 * Applies ingestion gates in a fixed order before records reach repository consumers.
 */
object BusinessIngestionGate {
    fun accept(
        records: List<VerifiedBusiness>,
        nowEpochMs: Long,
        maxAgeMs: Long,
    ): List<VerifiedBusiness> {
        return BusinessDeduplicator.deduplicate(records).mapNotNull { business ->
            if (VerifiedBusinessValidator.validate(business).isFailure) return@mapNotNull null
            if (BusinessFreshness.classify(business, nowEpochMs, maxAgeMs) == BusinessFreshnessState.INVALID) {
                return@mapNotNull null
            }
            business
        }
    }
}

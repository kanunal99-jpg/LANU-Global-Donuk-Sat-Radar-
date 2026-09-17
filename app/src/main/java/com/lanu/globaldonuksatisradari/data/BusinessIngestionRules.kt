package com.lanu.globaldonuksatisradari.data

/**
 * Deterministic freshness policy. A record is stale only by age; no business fact is inferred.
 */
object BusinessFreshness {
    fun classify(
        business: VerifiedBusiness,
        nowEpochMs: Long,
        maxAgeMs: Long,
    ): DataQuality {
        if (nowEpochMs <= 0L || maxAgeMs <= 0L) return DataQuality.UNVERIFIED
        if (business.verifiedAtEpochMs > nowEpochMs) return DataQuality.UNVERIFIED
        return if (nowEpochMs - business.verifiedAtEpochMs > maxAgeMs) {
            DataQuality.STALE
        } else {
            DataQuality.VERIFIED_OFFICIAL
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
            if (BusinessFreshness.classify(business, nowEpochMs, maxAgeMs) == DataQuality.UNVERIFIED) {
                return@mapNotNull null
            }
            business
        }
    }
}

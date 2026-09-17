package com.lanu.globaldonuksatisradari.data

/** A source that has been explicitly reviewed for permitted use. */
data class DataSourceDescriptor(
    val id: String,
    val name: String,
    val publisher: String,
    val licenseOrTerms: String,
    val sourceUrl: String,
    val lastVerifiedAtEpochMs: Long,
)

data class VerifiedBusiness(
    val id: String,
    val name: String,
    val city: String,
    val district: String,
    val neighborhood: String?,
    val source: DataSourceDescriptor,
    val verifiedAtEpochMs: Long,
)

/**
 * Keeps unverified external records out of the application domain.
 * A record is accepted only when source metadata and verification timestamps exist.
 */
object VerifiedBusinessValidator {
    fun validate(business: VerifiedBusiness): Result<VerifiedBusiness> {
        val errors = buildList {
            if (business.id.isBlank()) add("business.id boş olamaz")
            if (business.name.isBlank()) add("business.name boş olamaz")
            if (business.city.isBlank()) add("business.city boş olamaz")
            if (business.district.isBlank()) add("business.district boş olamaz")
            if (business.source.id.isBlank()) add("source.id boş olamaz")
            if (business.source.name.isBlank()) add("source.name boş olamaz")
            if (business.source.publisher.isBlank()) add("source.publisher boş olamaz")
            if (business.source.licenseOrTerms.isBlank()) add("source.licenseOrTerms boş olamaz")
            if (!business.source.sourceUrl.startsWith("https://")) add("source.sourceUrl HTTPS olmalı")
            if (business.source.lastVerifiedAtEpochMs <= 0) add("source.lastVerifiedAtEpochMs doğrulanmalı")
            if (business.verifiedAtEpochMs <= 0) add("verifiedAtEpochMs doğrulanmalı")
        }
        return if (errors.isEmpty()) Result.success(business) else Result.failure(IllegalArgumentException(errors.joinToString("; ")))
    }
}

interface BusinessRepository {
    /** Returns only records that passed source and verification validation. */
    suspend fun search(query: String, city: String, district: String? = null): List<VerifiedBusiness>
}

/** Safe default until a real, licensed source is configured and verified. */
class EmptyBusinessRepository : BusinessRepository {
    override suspend fun search(query: String, city: String, district: String?): List<VerifiedBusiness> = emptyList()
}

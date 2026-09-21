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

/** Field-level provenance: which reviewed source actually supplied a value. */
data class BusinessFieldEvidence(
    val field: String,
    val source: DataSourceDescriptor,
    val observedAtEpochMs: Long,
)

/** Normalized operational/legal state; UNKNOWN is the safe default. */
enum class BusinessOperationalStatus {
    UNKNOWN,
    ACTIVE,
    INACTIVE,
    CLOSED,
    SUSPENDED,
}

data class VerifiedBusiness(
    val id: String,
    val name: String,
    val city: String,
    val district: String,
    val neighborhood: String?,
    val source: DataSourceDescriptor,
    val verifiedAtEpochMs: Long,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val category: String? = null,
    val address: String? = null,
    /** Present only when the verified source actually provides a phone number. */
    val phone: String? = null,
    /** Present only when the verified source actually provides a website URL. */
    val website: String? = null,
    /** Present only when the verified source actually provides opening-hours data. */
    val openingHours: String? = null,
    val menuUrl: String? = null,
    val menuText: String? = null,
    /** NACE code is optional until a verified business-registry source supplies it. */
    val naceCode: String? = null,
    /** Legal/trade name is optional until a verified registry source supplies it. */
    val legalName: String? = null,
    /** Trade registry number is optional until a verified registry source supplies it. */
    val tradeRegistryNumber: String? = null,
    val operationalStatus: BusinessOperationalStatus = BusinessOperationalStatus.UNKNOWN,
    /** Explicit provenance for populated fields; never inferred from missing data. */
    val fieldEvidence: List<BusinessFieldEvidence> = emptyList(),
) {
    fun evidenceFor(field: String): List<BusinessFieldEvidence> =
        fieldEvidence.filter { it.field == field }

    fun effectiveEvidence(): List<BusinessFieldEvidence> =
        if (fieldEvidence.isNotEmpty()) fieldEvidence else {
            buildList {
                fun addIfPresent(field: String, value: Any?) {
                    if (value != null && value.toString().isNotBlank()) {
                        add(BusinessFieldEvidence(field, source, verifiedAtEpochMs))
                    }
                }
                addIfPresent("name", name)
                addIfPresent("city", city)
                addIfPresent("district", district)
                addIfPresent("neighborhood", neighborhood)
                addIfPresent("latitude", latitude)
                addIfPresent("longitude", longitude)
                addIfPresent("category", category)
                addIfPresent("address", address)
                addIfPresent("phone", phone)
                addIfPresent("website", website)
                addIfPresent("openingHours", openingHours)
                addIfPresent("menuUrl", menuUrl)
                addIfPresent("menuText", menuText)
                addIfPresent("naceCode", naceCode)
                addIfPresent("legalName", legalName)
                addIfPresent("tradeRegistryNumber", tradeRegistryNumber)
                addIfPresent("operationalStatus", operationalStatus.takeUnless { it == BusinessOperationalStatus.UNKNOWN })
            }
        }
}

/** Keeps unverified external records out of the application domain. */
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
            business.latitude?.let { if (it !in -90.0..90.0) add("latitude geçersiz") }
            business.longitude?.let { if (it !in -180.0..180.0) add("longitude geçersiz") }
            business.website?.let {
                if (!(it.startsWith("https://") || it.startsWith("http://"))) {
                    add("business.website yalnızca HTTP(S) olabilir")
                }
            }
            business.fieldEvidence.forEach { evidence ->
                if (evidence.field.isBlank()) add("fieldEvidence.field boş olamaz")
                if (evidence.observedAtEpochMs <= 0) add("fieldEvidence.observedAtEpochMs doğrulanmalı")
                if (evidence.source.id.isBlank()) add("fieldEvidence.source.id boş olamaz")
            }
            val duplicateEvidence = business.fieldEvidence
                .map { it.field + "|" + it.source.id + "|" + it.observedAtEpochMs }
                .groupingBy { it }
                .eachCount()
                .any { it.value > 1 }
            if (duplicateEvidence) add("fieldEvidence mükerrer kayıt içeriyor")
        }
        return if (errors.isEmpty()) Result.success(business) else Result.failure(IllegalArgumentException(errors.joinToString("; ")))
    }
}

interface BusinessRepository {
    /** Returns only records that passed source and verification validation. */
    suspend fun search(query: String, city: String, district: String? = null): List<VerifiedBusiness>
}

/** Safe default until a real source is configured and verified. */
class EmptyBusinessRepository : BusinessRepository {
    override suspend fun search(query: String, city: String, district: String?): List<VerifiedBusiness> = emptyList()
}

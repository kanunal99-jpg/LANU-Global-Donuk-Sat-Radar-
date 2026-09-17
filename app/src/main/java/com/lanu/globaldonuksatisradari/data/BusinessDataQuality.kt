package com.lanu.globaldonuksatisradari.data

/**
 * Describes how a business field was obtained. These states must never be
 * silently treated as equivalent in the UI or analytics layer.
 */
enum class DataQuality {
    VERIFIED_OFFICIAL,
    VERIFIED_EXTERNAL,
    ESTIMATED,
    USER_ENTERED,
    STALE,
    UNVERIFIED,
}

/**
 * Describes how a source may be consumed. It intentionally does not grant
 * permission; the configured source terms must be verified separately.
 */
enum class SourceAccessMethod {
    PUBLIC_SEARCH,
    OFFICIAL_BULK_REQUEST,
    AUTHENTICATED_EXPORT,
    API,
    MANUAL_IMPORT,
    NOT_CONFIGURED,
}

data class BusinessField<T>(
    val value: T,
    val quality: DataQuality,
    val sourceId: String? = null,
    val verifiedAtEpochMs: Long? = null,
)

/**
 * Machine-readable source contract used before an external business source
 * can be connected to the application domain.
 */
data class BusinessSourceContract(
    val descriptor: DataSourceDescriptor,
    val accessMethod: SourceAccessMethod,
    val scope: String,
    val permittedUseVerified: Boolean,
    val supportsBulk: Boolean,
    val fieldNames: Set<String>,
) {
    fun validate(): Result<BusinessSourceContract> {
        val errors = buildList {
            if (descriptor.id.isBlank()) add("source.id boş olamaz")
            if (descriptor.name.isBlank()) add("source.name boş olamaz")
            if (descriptor.publisher.isBlank()) add("source.publisher boş olamaz")
            if (descriptor.licenseOrTerms.isBlank()) add("source.licenseOrTerms boş olamaz")
            if (!descriptor.sourceUrl.startsWith("https://")) add("source.sourceUrl HTTPS olmalı")
            if (descriptor.lastVerifiedAtEpochMs <= 0) add("source.lastVerifiedAtEpochMs doğrulanmalı")
            if (scope.isBlank()) add("source.scope boş olamaz")
            if (!permittedUseVerified) add("kaynağın kullanım izni doğrulanmadan bağlanamaz")
            if (fieldNames.any { it.isBlank() }) add("fieldNames boş alan içeremez")
            if (accessMethod == SourceAccessMethod.NOT_CONFIGURED && permittedUseVerified) {
                add("kullanım izni doğrulanmış bir kaynak için erişim yöntemi tanımlanmalı")
            }
        }
        return if (errors.isEmpty()) Result.success(this) else Result.failure(IllegalArgumentException(errors.joinToString("; ")))
    }
}

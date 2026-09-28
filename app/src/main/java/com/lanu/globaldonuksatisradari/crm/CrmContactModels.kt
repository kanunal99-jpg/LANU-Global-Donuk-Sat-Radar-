package com.lanu.globaldonuksatisradari.crm

data class CrmContact(
    val id: String,
    val customerId: String,
    val fullName: String,
    val role: String?,
    val phone: String?,
    val email: String?,
    val isPrimary: Boolean,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
    val version: Long,
    val syncState: SyncState,
)

object CrmContactValidator {
    fun normalizeName(value: String): String = value.trim().replace(Regex("\\s+"), " ").also {
        require(it.isNotEmpty()) { "Yetkili kişi adı boş olamaz." }
    }

    fun normalizeOptionalText(value: String?): String? =
        value?.trim()?.replace(Regex("\\s+"), " ")?.takeIf(String::isNotEmpty)

    fun normalizeEmail(value: String?): String? {
        val normalized = value?.trim()?.lowercase()?.takeIf(String::isNotEmpty) ?: return null
        require('@' in normalized && normalized.substringAfter('@').contains('.')) {
            "Geçerli bir e-posta adresi girin."
        }
        return normalized
    }

    fun normalizePhone(value: String?): String? {
        val normalized = value?.trim()?.takeIf(String::isNotEmpty) ?: return null
        val digits = normalized.filter(Char::isDigit)
        require(digits.length in 7..15) { "Telefon numarası 7-15 rakam içermelidir." }
        return normalized
    }
}

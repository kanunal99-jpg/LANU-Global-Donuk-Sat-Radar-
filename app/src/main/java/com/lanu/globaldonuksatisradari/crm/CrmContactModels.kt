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
    private const val MAX_NAME_LENGTH = 120
    private const val MAX_ROLE_LENGTH = 120
    private const val MAX_EMAIL_LENGTH = 254

    private fun sanitizeHumanText(value: String): String =
        value
            .filterNot { it.isISOControl() && !it.isWhitespace() }
            .trim()
            .replace(Regex("\\s+"), " ")

    fun normalizeName(value: String): String = sanitizeHumanText(value).also {
        require(it.isNotEmpty()) { "Yetkili kişi adı boş olamaz." }
        require(it.length <= MAX_NAME_LENGTH) { "Yetkili kişi adı en fazla $MAX_NAME_LENGTH karakter olabilir." }
    }

    fun normalizeOptionalText(value: String?): String? =
        value?.let(::sanitizeHumanText)?.takeIf(String::isNotEmpty)?.also {
            require(it.length <= MAX_ROLE_LENGTH) { "Görev/rol en fazla $MAX_ROLE_LENGTH karakter olabilir." }
        }

    fun normalizeEmail(value: String?): String? {
        val normalized = value
            ?.filterNot(Char::isISOControl)
            ?.trim()
            ?.lowercase()
            ?.takeIf(String::isNotEmpty)
            ?: return null
        require(normalized.length <= MAX_EMAIL_LENGTH) { "E-posta adresi en fazla $MAX_EMAIL_LENGTH karakter olabilir." }
        require(normalized.none(Char::isWhitespace)) { "E-posta adresi boşluk içeremez." }
        val local = normalized.substringBefore('@', missingDelimiterValue = "")
        val domain = normalized.substringAfter('@', missingDelimiterValue = "")
        require(local.isNotEmpty() && domain.contains('.') && !domain.startsWith('.') && !domain.endsWith('.')) {
            "Geçerli bir e-posta adresi girin."
        }
        return normalized
    }

    fun normalizePhone(value: String?): String? {
        val sanitized = value
            ?.filterNot(Char::isISOControl)
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?: return null
        val digits = sanitized.filter(Char::isDigit)
        require(digits.length in 7..15) { "Telefon numarası 7-15 rakam içermelidir." }
        require(sanitized.all { it.isDigit() || it in "+()-. /" }) {
            "Telefon numarası geçersiz karakter içeriyor."
        }
        return sanitized
    }
}

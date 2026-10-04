package com.lanu.globaldonuksatisradari.data

import android.content.Context
import org.json.JSONObject
import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.util.Locale
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

enum class OfficialRegistrySource {
    ITO,
    CHAMBER,
    TOBB,
    MERSIS,
    ESBIS;

    val descriptor: DataSourceDescriptor
        get() = when (this) {
            ITO -> DataSourceDescriptor(
                id = "official-ito",
                name = "İTO Resmî Üye/Firma Kaydı",
                publisher = "İstanbul Ticaret Odası",
                licenseOrTerms = "https://bilgibankasi.ito.org.tr/tr/bilgi-bankasi/toplu-bilgi-talebi/meslek-gruplari",
                sourceUrl = "https://bilgibankasi.ito.org.tr/",
                lastVerifiedAtEpochMs = SOURCE_POLICY_REVIEWED_AT,
            )
            CHAMBER -> DataSourceDescriptor(
                id = "official-chamber",
                name = "Yerel Ticaret / Ticaret ve Sanayi Odası Resmî Üye Kaydı",
                publisher = "TOBB'a bağlı yerel Ticaret / Ticaret ve Sanayi Odası",
                licenseOrTerms = "https://www.tobb.org.tr/OdaveBorsalarDB/Sayfalar/oda--borsa-sorgulama.php",
                sourceUrl = "https://www.tobb.org.tr/OdaveBorsalarDB/Sayfalar/oda--borsa-sorgulama.php",
                lastVerifiedAtEpochMs = SOURCE_POLICY_REVIEWED_AT,
            )
            TOBB -> DataSourceDescriptor(
                id = "official-tobb",
                name = "TOBB Resmî Üye / Sanayi Kaydı",
                publisher = "Türkiye Odalar ve Borsalar Birliği",
                licenseOrTerms = "https://webservistest.tobb.org.tr/Kilavuz2.pdf",
                sourceUrl = "https://webservistest.tobb.org.tr/",
                lastVerifiedAtEpochMs = SOURCE_POLICY_REVIEWED_AT,
            )
            MERSIS -> DataSourceDescriptor(
                id = "official-mersis",
                name = "MERSİS Resmî Firma Kaydı",
                publisher = "T.C. Ticaret Bakanlığı",
                licenseOrTerms = "https://www.ticaret.gov.tr/ic-ticaret/ticaret-sicili/merkezi-sicil-kayit-sistemi-mersis",
                sourceUrl = "https://mersis.ticaret.gov.tr/",
                lastVerifiedAtEpochMs = SOURCE_POLICY_REVIEWED_AT,
            )
            ESBIS -> DataSourceDescriptor(
                id = "official-esbis",
                name = "ESBİS Resmî Esnaf Kaydı",
                publisher = "T.C. Ticaret Bakanlığı",
                licenseOrTerms = "https://ticaret.gov.tr/esnaf-sanatkarlar/esbis/genel-bilgiler",
                sourceUrl = "https://esbis.ticaret.gov.tr/",
                lastVerifiedAtEpochMs = SOURCE_POLICY_REVIEWED_AT,
            )
        }

    val contract: BusinessSourceContract
        get() = BusinessSourceContract(
            descriptor = descriptor,
            accessMethod = when (this) {
                ITO, CHAMBER -> SourceAccessMethod.OFFICIAL_BULK_REQUEST
                TOBB -> SourceAccessMethod.API
                MERSIS, ESBIS -> SourceAccessMethod.AUTHENTICATED_EXPORT
            },
            scope = when (this) {
                TOBB -> "Yetkili Oda/Borsa web servisinden veya resmî exporttan alınan işletme kimliği, sicil durumu, MERSİS/oda sicil, NACE, adres ve kurumsal iletişim alanları"
                else -> "Kullanıcının resmî kanaldan temin ettiği firma/esnaf çıktısındaki işletme adı, sicil durumu, adres, telefon ve web alanları"
            },
            permittedUseVerified = true,
            supportsBulk = true,
            fieldNames = setOf(
                "registration_number",
                "business_name",
                "status",
                "city",
                "district",
                "neighborhood",
                "address",
                "phone",
                "website",
                "nace_code",
                "mersis_number",
                "signboard_name",
                "business_type",
                "tax_number",
                "chamber_code",
                "source_record_id",
            ),
        )

    companion object {
        private const val SOURCE_POLICY_REVIEWED_AT = 1790802000000L
    }
}

data class OfficialRegistryEvidence(
    val source: DataSourceDescriptor,
    val registrationNumber: String?,
    val status: String?,
    val importedAtEpochMs: Long,
    val fieldsUsed: Set<String>,
    val mersisNumber: String? = null,
    val signboardName: String? = null,
    val businessType: String? = null,
    val taxNumber: String? = null,
) {
    val explicitlyActive: Boolean
        get() = status?.let(OfficialRegistryStatus::isActive) == true

    val explicitlyInactive: Boolean
        get() = status?.let(OfficialRegistryStatus::isInactive) == true
}

data class OfficialRegistryRecord(
    val source: OfficialRegistrySource,
    val registrationNumber: String?,
    val businessName: String,
    val status: String?,
    val city: String?,
    val district: String?,
    val neighborhood: String?,
    val address: String?,
    val phone: String?,
    val website: String?,
    val importedAtEpochMs: Long,
    val naceCode: String? = null,
    val mersisNumber: String? = null,
    val signboardName: String? = null,
    val businessType: String? = null,
    val email: String? = null,
    val chamberCode: String? = null,
    val sourceRecordId: String? = null,
    val taxNumber: String? = null,
)

data class OfficialRegistryImportSummary(
    val source: OfficialRegistrySource,
    val importedCount: Int,
    val activeCount: Int,
    val inactiveCount: Int,
    val unknownStatusCount: Int,
    val fileName: String,
    val verifiedIdentityCount: Int = 0,
    val phoneCount: Int = 0,
    val addressCount: Int = 0,
)

object OfficialRegistryTrust {
    /**
     * Selecting an İTO/ODA/TOBB/MERSİS/ESBİS source is only a source declaration.
     * A record is treated as official identity evidence only when the imported
     * file contains a registry identifier (oda sicil, MERSİS no, ESBİS sicil, etc.).
     */
    fun isIdentityVerified(record: OfficialRegistryRecord): Boolean =
        !record.registrationNumber.isNullOrBlank() || !record.mersisNumber.isNullOrBlank()

    fun verified(records: List<OfficialRegistryRecord>): List<OfficialRegistryRecord> =
        records.filter(::isIdentityVerified)
}

internal object OfficialRegistryStatus {
    fun isActive(value: String): Boolean {
        val normalized = normalize(value)
        return normalized == "faal" ||
            normalized == "aktif" ||
            normalized.contains("faal uye") ||
            normalized.contains("faal kayit")
    }

    fun isInactive(value: String): Boolean {
        val normalized = normalize(value)
        return listOf(
            "terkin",
            "kapali",
            "kapanmis",
            "pasif",
            "askida",
            "aski",
            "tasfiye",
            "silinmis",
        ).any { token -> normalized == token || normalized.contains(token) }
    }

    private fun normalize(value: String): String =
        OfficialRegistryNormalizer.text(value)
}

class OfficialRegistryStore(
    context: Context,
) {
    private val directory = File(context.applicationContext.filesDir, DIRECTORY_NAME).apply { mkdirs() }

    @Synchronized
    fun importDocument(
        source: OfficialRegistrySource,
        fileName: String,
        bytes: ByteArray,
        importedAtEpochMs: Long = System.currentTimeMillis(),
        defaultCity: String? = null,
    ): OfficialRegistryImportSummary {
        require(source.contract.validate().isSuccess) { "Resmî kaynak sözleşmesi doğrulanamadı." }
        require(bytes.isNotEmpty()) { "İçe aktarılacak dosya boş." }
        require(bytes.size <= MAX_IMPORT_BYTES) { "Resmî sicil dosyası 25 MB sınırını aşıyor." }
        require(importedAtEpochMs > 0L) { "İçe aktarma zamanı geçersiz." }

        val parsed = OfficialRegistryImportParser.parse(
            bytes = bytes,
            fileName = fileName,
            source = source,
            importedAtEpochMs = importedAtEpochMs,
            defaultCity = defaultCity,
        ).distinctBy(::recordIdentityKey)

        require(parsed.isNotEmpty()) {
            "Dosyada işletme adı içeren kullanılabilir resmî kayıt bulunamadı."
        }

        migrateLegacyFile(source)

        parsed.groupBy { partitionToken(it.city ?: defaultCity) }
            .forEach { (partition, records) ->
                val target = partitionFile(source, partition)
                val existing = if (target.exists()) readFile(target) else emptyList()
                // Official exports are often partial (one chamber, one date range, one
                // authorized query). Never erase previously observed identities merely
                // because they are absent from a later partial file. A newer record with
                // the same stable identity replaces the older record.
                val combined = deduplicateNewest(existing + records)
                require(combined.size <= MAX_RECORDS_PER_PARTITION) {
                    "Resmî sicil şehir partition'ı güvenli kayıt sınırını aşıyor; kaynak oda/district bazında bölünmeli."
                }
                writeRecordsAtomically(target, combined)
            }

        return OfficialRegistryImportSummary(
            source = source,
            importedCount = parsed.size,
            activeCount = parsed.count { it.status?.let(OfficialRegistryStatus::isActive) == true },
            inactiveCount = parsed.count { it.status?.let(OfficialRegistryStatus::isInactive) == true },
            unknownStatusCount = parsed.count { record ->
                val status = record.status
                status.isNullOrBlank() ||
                    (!OfficialRegistryStatus.isActive(status) &&
                        !OfficialRegistryStatus.isInactive(status))
            },
            fileName = fileName,
            verifiedIdentityCount = parsed.count(OfficialRegistryTrust::isIdentityVerified),
            phoneCount = parsed.count { !it.phone.isNullOrBlank() },
            addressCount = parsed.count { !it.address.isNullOrBlank() },
        )
    }

    fun recordsFor(
        city: String,
        district: String?,
    ): List<OfficialRegistryRecord> =
        OfficialRegistrySource.entries.flatMap { source ->
            recordsFor(source, city, district)
        }

    fun recordsFor(
        source: OfficialRegistrySource,
        city: String,
        district: String?,
    ): List<OfficialRegistryRecord> {
        val normalizedCity = OfficialRegistryNormalizer.text(city)
        val normalizedDistrict = district
            ?.takeUnless { it.isBlank() || it.equals("Tümü", ignoreCase = true) }
            ?.let(OfficialRegistryNormalizer::text)

        return readSource(source, city).filter { record ->
            val cityMatches = record.city.isNullOrBlank() ||
                OfficialRegistryNormalizer.text(record.city) == normalizedCity
            val districtMatches = normalizedDistrict == null ||
                record.district.isNullOrBlank() ||
                OfficialRegistryNormalizer.text(record.district) == normalizedDistrict
            cityMatches && districtMatches
        }
    }

    fun records(source: OfficialRegistrySource): List<OfficialRegistryRecord> = readSource(source)

    fun allRecords(): List<OfficialRegistryRecord> =
        OfficialRegistrySource.entries.flatMap(::readSource)

    fun count(source: OfficialRegistrySource): Int =
        sourceFiles(source, null).sumOf { file ->
            if (!file.exists()) {
                0
            } else {
                file.useLines(Charsets.UTF_8) { lines ->
                    lines.count(String::isNotBlank)
                }
            }
        }

    private fun readSource(
        source: OfficialRegistrySource,
        city: String? = null,
    ): List<OfficialRegistryRecord> {
        val records = sourceFiles(source, city)
            .flatMap(::readFile)
        return deduplicateNewest(records)
    }

    private fun sourceFiles(
        source: OfficialRegistrySource,
        city: String?,
    ): List<File> {
        val legacy = legacySourceFile(source)
        if (city != null) {
            return listOf(
                partitionFile(source, partitionToken(city)),
                partitionFile(source, UNKNOWN_PARTITION),
                legacy,
            ).filter(File::exists).distinctBy(File::getAbsolutePath)
        }

        val prefix = partitionPrefix(source)
        val partitioned = directory.listFiles()
            ?.filter { file ->
                file.isFile &&
                    file.name.startsWith(prefix) &&
                    file.name.endsWith(PARTITION_SUFFIX)
            }
            ?.sortedBy(File::getName)
            .orEmpty()
        return (partitioned + listOf(legacy).filter(File::exists))
            .distinctBy(File::getAbsolutePath)
    }

    private fun readFile(file: File): List<OfficialRegistryRecord> {
        if (!file.exists()) return emptyList()
        val source = sourceFromFileName(file.name) ?: return emptyList()
        return file.useLines(Charsets.UTF_8) { lines ->
            lines.mapNotNull { line ->
                line.takeIf(String::isNotBlank)?.let { raw ->
                    runCatching { decode(JSONObject(raw), source) }.getOrNull()
                }
            }.toList()
        }
    }

    private fun migrateLegacyFile(source: OfficialRegistrySource) {
        val legacy = legacySourceFile(source)
        if (!legacy.exists()) return

        val legacyRecords = readFile(legacy)
        legacyRecords.groupBy { partitionToken(it.city) }
            .forEach { (partition, records) ->
                val target = partitionFile(source, partition)
                val combined = if (target.exists()) {
                    deduplicateNewest(readFile(target) + records)
                } else {
                    records
                }
                writeRecordsAtomically(target, combined.take(MAX_RECORDS_PER_PARTITION))
            }

        if (!legacy.delete()) {
            throw IOException("Eski resmî sicil deposu partition yapısına taşındı ancak eski dosya silinemedi.")
        }
    }

    private fun writeRecordsAtomically(
        target: File,
        records: List<OfficialRegistryRecord>,
    ) {
        val temp = File(directory, target.name + ".tmp")
        temp.bufferedWriter(Charsets.UTF_8).use { writer ->
            records.forEach { record ->
                writer.appendLine(encode(record).toString())
            }
        }
        val backup = File(directory, target.name + ".bak")
        if (backup.exists()) backup.delete()
        if (target.exists() && !target.renameTo(backup)) {
            temp.delete()
            throw IOException("Eski resmî sicil önbelleği güvenli yedeğe taşınamadı.")
        }
        if (!temp.renameTo(target)) {
            temp.delete()
            if (backup.exists()) backup.renameTo(target)
            throw IOException("Resmî sicil verisi güvenli şekilde kaydedilemedi; önceki kayıt korundu.")
        }
        if (backup.exists()) backup.delete()
    }

    private fun deduplicateNewest(
        records: List<OfficialRegistryRecord>,
    ): List<OfficialRegistryRecord> {
        val byIdentity = LinkedHashMap<String, OfficialRegistryRecord>()
        records.forEach { record ->
            val key = recordIdentityKey(record)
            val previous = byIdentity[key]
            if (previous == null || record.importedAtEpochMs >= previous.importedAtEpochMs) {
                byIdentity[key] = record
            }
        }
        return byIdentity.values.toList()
    }

    private fun recordIdentityKey(record: OfficialRegistryRecord): String =
        listOf(
            record.source.name,
            record.registrationNumber.orEmpty(),
            record.mersisNumber.orEmpty(),
            record.sourceRecordId.orEmpty(),
            OfficialRegistryNormalizer.text(record.businessName),
            OfficialRegistryNormalizer.text(record.city.orEmpty()),
            OfficialRegistryNormalizer.text(record.district.orEmpty()),
            OfficialRegistryNormalizer.phones(record.phone).sorted().joinToString(","),
        ).joinToString("|")

    private fun partitionToken(city: String?): String {
        val normalized = city
            ?.takeIf(String::isNotBlank)
            ?.let(OfficialRegistryNormalizer::text)
            .orEmpty()
        if (normalized.isBlank()) return UNKNOWN_PARTITION
        return normalized
            .map { char -> if (char.isLetterOrDigit()) char else '_' }
            .joinToString("")
            .trim('_')
            .take(80)
            .ifBlank { UNKNOWN_PARTITION }
    }

    private fun partitionPrefix(source: OfficialRegistrySource): String =
        "registry_${source.name.lowercase(Locale.ROOT)}__"

    private fun partitionFile(
        source: OfficialRegistrySource,
        partition: String,
    ): File = File(directory, partitionPrefix(source) + partition + PARTITION_SUFFIX)

    private fun legacySourceFile(source: OfficialRegistrySource): File =
        File(directory, "registry_${source.name.lowercase(Locale.ROOT)}.jsonl")

    private fun sourceFromFileName(fileName: String): OfficialRegistrySource? =
        OfficialRegistrySource.entries.firstOrNull { source ->
            fileName == legacySourceFile(source).name ||
                fileName.startsWith(partitionPrefix(source))
        }

    private fun encode(record: OfficialRegistryRecord): JSONObject =
        JSONObject().apply {
            put("registrationNumber", record.registrationNumber ?: JSONObject.NULL)
            put("businessName", record.businessName)
            put("status", record.status ?: JSONObject.NULL)
            put("city", record.city ?: JSONObject.NULL)
            put("district", record.district ?: JSONObject.NULL)
            put("neighborhood", record.neighborhood ?: JSONObject.NULL)
            put("address", record.address ?: JSONObject.NULL)
            put("phone", record.phone ?: JSONObject.NULL)
            put("website", record.website ?: JSONObject.NULL)
            put("naceCode", record.naceCode ?: JSONObject.NULL)
            put("mersisNumber", record.mersisNumber ?: JSONObject.NULL)
            put("signboardName", record.signboardName ?: JSONObject.NULL)
            put("businessType", record.businessType ?: JSONObject.NULL)
            put("email", record.email ?: JSONObject.NULL)
            put("chamberCode", record.chamberCode ?: JSONObject.NULL)
            put("sourceRecordId", record.sourceRecordId ?: JSONObject.NULL)
            put("taxNumber", record.taxNumber ?: JSONObject.NULL)
            put("importedAtEpochMs", record.importedAtEpochMs)
        }

    private fun decode(item: JSONObject, source: OfficialRegistrySource): OfficialRegistryRecord =
        OfficialRegistryRecord(
            source = source,
            registrationNumber = optionalString(item, "registrationNumber"),
            businessName = item.getString("businessName"),
            status = optionalString(item, "status"),
            city = optionalString(item, "city"),
            district = optionalString(item, "district"),
            neighborhood = optionalString(item, "neighborhood"),
            address = optionalString(item, "address"),
            phone = optionalString(item, "phone"),
            website = optionalString(item, "website"),
            importedAtEpochMs = item.getLong("importedAtEpochMs"),
            naceCode = optionalString(item, "naceCode"),
            mersisNumber = optionalString(item, "mersisNumber"),
            signboardName = optionalString(item, "signboardName"),
            businessType = optionalString(item, "businessType"),
            email = optionalString(item, "email"),
            chamberCode = optionalString(item, "chamberCode"),
            sourceRecordId = optionalString(item, "sourceRecordId"),
            taxNumber = optionalString(item, "taxNumber"),
        )

    private fun optionalString(item: JSONObject, key: String): String? =
        item.optString(key).trim().takeIf { it.isNotBlank() && it != "null" }

    private companion object {
        const val DIRECTORY_NAME = "official_registry"
        const val MAX_IMPORT_BYTES = 25 * 1024 * 1024
        const val MAX_RECORDS_PER_PARTITION = 1_000_000
        const val UNKNOWN_PARTITION = "unknown"
        const val PARTITION_SUFFIX = ".jsonl"
    }
}

object OfficialRegistryEnricher {
    fun enrich(
        businesses: List<VerifiedBusiness>,
        records: List<OfficialRegistryRecord>,
    ): List<VerifiedBusiness> {
        if (businesses.isEmpty() || records.isEmpty()) return businesses
        val verifiedRecords = OfficialRegistryTrust.verified(records)
        if (verifiedRecords.isEmpty()) return businesses

        val result = mutableListOf<VerifiedBusiness>()
        businesses.forEach { business ->
            val match = OfficialRegistryMatcher.bestMatch(
                name = business.name,
                city = business.city,
                district = business.district,
                address = business.address,
                phone = business.phone,
                records = verifiedRecords,
            )
            if (match == null) {
                result += business
                return@forEach
            }

            val fieldsUsed = linkedSetOf<String>()
            val inactive = match.status?.let(OfficialRegistryStatus::isInactive) == true
            val officialAddress = if (inactive) null else {
                match.address?.trim()?.takeIf(String::isNotEmpty)
            }
            val officialPhone = if (inactive) null else {
                match.phone?.trim()?.takeIf(String::isNotEmpty)
            }
            val officialWebsite = if (inactive) null else {
                match.website?.trim()?.takeIf(String::isNotEmpty)
            }
            val officialDistrict = if (inactive) null else {
                match.district?.trim()?.takeIf(String::isNotEmpty)
            }
            val officialNeighborhood = if (inactive) null else {
                match.neighborhood?.trim()?.takeIf(String::isNotEmpty)
            }

            if (!match.status.isNullOrBlank()) fieldsUsed += "status"
            if (!match.registrationNumber.isNullOrBlank()) fieldsUsed += "registration_number"
            if (officialAddress != null) fieldsUsed += "address"
            if (officialPhone != null) fieldsUsed += "phone"
            if (officialWebsite != null) fieldsUsed += "website"
            if (officialDistrict != null) fieldsUsed += "district"
            if (officialNeighborhood != null) fieldsUsed += "neighborhood"

            result += business.copy(
                district = officialDistrict ?: business.district,
                neighborhood = officialNeighborhood ?: business.neighborhood,
                address = officialAddress ?: business.address,
                phone = officialPhone ?: business.phone,
                website = officialWebsite ?: business.website,
                officialRegistryEvidence = OfficialRegistryEvidence(
                    source = match.source.descriptor,
                    registrationNumber = match.registrationNumber
                        ?: match.mersisNumber,
                    status = match.status,
                    importedAtEpochMs = match.importedAtEpochMs,
                    fieldsUsed = fieldsUsed,
                    mersisNumber = match.mersisNumber,
                    signboardName = match.signboardName,
                    businessType = match.businessType,
                    taxNumber = match.taxNumber,
                ),
            )
        }
        return result
    }

}
 
object OfficialRegistryDiscovery {
    /**
     * Broad inventory must not depend on OSM already knowing a business.
     * Identity-verified official registry rows can therefore become standalone
     * radar records. Records without a registry id are still allowed to enrich
     * an OSM point, but they are not promoted to standalone verified inventory.
     */
    fun mergeIntoBroadInventory(
        discovered: List<VerifiedBusiness>,
        records: List<OfficialRegistryRecord>,
        selectedCity: String,
        selectedDistrict: String?,
    ): List<VerifiedBusiness> {
        val enriched = OfficialRegistryEnricher.enrich(discovered, records)
        if (records.isEmpty()) return enriched

        val matchedOfficialKeys = enriched.mapNotNull { business ->
            val evidence = business.officialRegistryEvidence ?: return@mapNotNull null
            val registrationNumber = evidence.registrationNumber
                ?.trim()
                ?.takeIf(String::isNotEmpty)
                ?: return@mapNotNull null
            evidence.source.id + "|" + registrationNumber
        }.toSet()

        val officialOnly = OfficialRegistryTrust.verified(records)
            .asSequence()
            .filterNot { record -> officialIdentityKey(record) in matchedOfficialKeys }
            .mapNotNull { record ->
                toVerifiedBusiness(
                    record = record,
                    selectedCity = selectedCity,
                    selectedDistrict = selectedDistrict,
                )
            }
            .toList()

        return BusinessDeduplication.deduplicateCrossSource(enriched + officialOnly)
    }

    internal fun toVerifiedBusiness(
        record: OfficialRegistryRecord,
        selectedCity: String,
        selectedDistrict: String?,
    ): VerifiedBusiness? {
        if (!OfficialRegistryTrust.isIdentityVerified(record)) return null
        val registrationNumber = (record.registrationNumber ?: record.mersisNumber)
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?: return null
        val name = record.businessName.trim().takeIf(String::isNotEmpty) ?: return null
        val city = record.city?.trim()?.takeIf(String::isNotEmpty) ?: selectedCity.trim()
        if (city.isEmpty()) return null
        val district = record.district?.trim()?.takeIf(String::isNotEmpty)
            ?: selectedDistrict?.trim()?.takeIf(String::isNotEmpty)
            ?: "Bilinmiyor"

        val fieldsUsed = linkedSetOf("registration_number")
        if (!record.status.isNullOrBlank()) fieldsUsed += "status"
        if (!record.address.isNullOrBlank()) fieldsUsed += "address"
        if (!record.phone.isNullOrBlank()) fieldsUsed += "phone"
        if (!record.website.isNullOrBlank()) fieldsUsed += "website"
        if (!record.district.isNullOrBlank()) fieldsUsed += "district"
        if (!record.neighborhood.isNullOrBlank()) fieldsUsed += "neighborhood"
        if (!record.naceCode.isNullOrBlank()) fieldsUsed += "nace_code"
        if (!record.mersisNumber.isNullOrBlank()) fieldsUsed += "mersis_number"
        if (!record.signboardName.isNullOrBlank()) fieldsUsed += "signboard_name"
        if (!record.businessType.isNullOrBlank()) fieldsUsed += "business_type"
        if (!record.taxNumber.isNullOrBlank()) fieldsUsed += "tax_number"

        return VerifiedBusiness(
            id = record.source.name.lowercase(Locale.ROOT) + ":" + registrationNumber,
            name = name,
            city = city,
            district = district,
            neighborhood = record.neighborhood?.trim()?.takeIf(String::isNotEmpty),
            source = record.source.descriptor,
            verifiedAtEpochMs = record.importedAtEpochMs,
            category = record.naceCode
                ?.trim()
                ?.takeIf(String::isNotEmpty)
                ?.let { "NACE " + it }
                ?: "Resmî Sicil Kaydı",
            address = record.address?.trim()?.takeIf(String::isNotEmpty),
            phone = record.phone?.trim()?.takeIf(String::isNotEmpty),
            website = record.website?.trim()?.takeIf(String::isNotEmpty),
            officialRegistryEvidence = OfficialRegistryEvidence(
                source = record.source.descriptor,
                registrationNumber = registrationNumber,
                status = record.status,
                importedAtEpochMs = record.importedAtEpochMs,
                fieldsUsed = fieldsUsed,
            ),
        )
    }

    private fun officialIdentityKey(record: OfficialRegistryRecord): String {
        val registrationNumber = record.registrationNumber?.trim().orEmpty()
        return record.source.descriptor.id + "|" + registrationNumber
    }
}

object OfficialRegistryMatcher {
    fun bestMatch(
        name: String,
        city: String?,
        district: String?,
        address: String?,
        phone: String?,
        records: List<OfficialRegistryRecord>,
    ): OfficialRegistryRecord? {
        var bestRecord: OfficialRegistryRecord? = null
        var bestScore = Int.MIN_VALUE
        var ambiguous = false

        records.forEach { record ->
            val score = matchScore(name, city, district, address, phone, record)
            if (score < MIN_MATCH_SCORE) return@forEach

            when {
                score > bestScore -> {
                    bestRecord = record
                    bestScore = score
                    ambiguous = false
                }
                score == bestScore && bestRecord != null && !equivalent(bestRecord!!, record) -> {
                    ambiguous = true
                }
            }
        }

        return if (ambiguous) null else bestRecord
    }

    private fun matchScore(
        name: String,
        city: String?,
        district: String?,
        address: String?,
        phone: String?,
        record: OfficialRegistryRecord,
    ): Int {
        var score = 0
        val subjectPhones = OfficialRegistryNormalizer.phones(phone)
        val officialPhones = OfficialRegistryNormalizer.phones(record.phone)
        if (subjectPhones.isNotEmpty() && officialPhones.isNotEmpty() &&
            subjectPhones.intersect(officialPhones).isNotEmpty()
        ) {
            score += 100
        }

        val subjectName = OfficialRegistryNormalizer.text(name)
        val officialName = OfficialRegistryNormalizer.text(record.businessName)
        if (subjectName.isNotEmpty() && officialName.isNotEmpty()) {
            when {
                subjectName == officialName -> score += 60
                (subjectName.length >= 5 && officialName.contains(subjectName)) ||
                    (officialName.length >= 5 && subjectName.contains(officialName)) -> score += 50
                else -> {
                    val subjectTokens = subjectName.split(" ").filter { it.length >= 3 }.toSet()
                    val officialTokens = officialName.split(" ").filter { it.length >= 3 }.toSet()
                    val overlap = subjectTokens.intersect(officialTokens).size
                    val required = minOf(2, subjectTokens.size, officialTokens.size)
                    if (required > 0 && overlap >= required) score += 45
                }
            }
        }

        val subjectCity = OfficialRegistryNormalizer.text(city.orEmpty())
        val officialCity = OfficialRegistryNormalizer.text(record.city.orEmpty())
        if (officialCity.isNotEmpty() && subjectCity == officialCity) score += 10

        val subjectDistrict = OfficialRegistryNormalizer.text(district.orEmpty())
        val officialDistrict = OfficialRegistryNormalizer.text(record.district.orEmpty())
        if (officialDistrict.isNotEmpty() && subjectDistrict == officialDistrict) score += 20

        val subjectAddress = OfficialRegistryNormalizer.text(address.orEmpty())
        val officialAddress = OfficialRegistryNormalizer.text(record.address.orEmpty())
        if (subjectAddress.isNotEmpty() && officialAddress.isNotEmpty()) {
            val subjectTokens = subjectAddress.split(" ").filter { it.length >= 4 }.toSet()
            val officialTokens = officialAddress.split(" ").filter { it.length >= 4 }.toSet()
            if (subjectTokens.isNotEmpty() && officialTokens.isNotEmpty() &&
                subjectTokens.intersect(officialTokens).size >= 2
            ) {
                score += 10
            }
        }
        return score
    }

    private fun equivalent(
        left: OfficialRegistryRecord,
        right: OfficialRegistryRecord,
    ): Boolean =
        left.registrationNumber == right.registrationNumber &&
            OfficialRegistryNormalizer.text(left.businessName) ==
                OfficialRegistryNormalizer.text(right.businessName) &&
            OfficialRegistryNormalizer.text(left.address.orEmpty()) ==
                OfficialRegistryNormalizer.text(right.address.orEmpty())

    private const val MIN_MATCH_SCORE = 80
}

internal object OfficialRegistryNormalizer {
    fun text(value: String): String =
        value.trim()
            .replace('İ', 'I')
            .lowercase(Locale.ROOT)
            .replace("\u0307", "")
            .replace('ı', 'i')
            .replace('ğ', 'g')
            .replace('ü', 'u')
            .replace('ş', 's')
            .replace('ö', 'o')
            .replace('ç', 'c')
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()
            .replace(Regex("\\s+"), " ")

    fun phones(value: String?): Set<String> =
        value.orEmpty()
            .split(Regex("[|;/,\\n]+"))
            .map { part -> part.filter(Char::isDigit) }
            .mapNotNull { digits ->
                when {
                    digits.length >= 10 -> digits.takeLast(10)
                    digits.length >= 7 -> digits
                    else -> null
                }
            }
            .toSet()

    fun phone(value: String?): String = phones(value).firstOrNull().orEmpty()
}

object OfficialRegistryImportParser {
    fun parse(
        bytes: ByteArray,
        fileName: String,
        source: OfficialRegistrySource,
        importedAtEpochMs: Long,
        defaultCity: String? = null,
    ): List<OfficialRegistryRecord> {
        if (fileName.endsWith(".json", ignoreCase = true)) {
            return parseAuthorizedJson(
                text = bytes.toString(Charsets.UTF_8),
                source = source,
                importedAtEpochMs = importedAtEpochMs,
                defaultCity = defaultCity,
            )
        }

        val rows = when {
            fileName.endsWith(".xlsx", ignoreCase = true) -> parseXlsx(bytes)
            fileName.endsWith(".csv", ignoreCase = true) ||
                fileName.endsWith(".txt", ignoreCase = true) ||
                fileName.endsWith(".tsv", ignoreCase = true) -> parseDelimited(bytes.toString(Charsets.UTF_8))
            else -> {
                val asText = bytes.toString(Charsets.UTF_8)
                when {
                    asText.trimStart().startsWith("[") || asText.trimStart().startsWith("{") ->
                        return parseAuthorizedJson(
                            text = asText,
                            source = source,
                            importedAtEpochMs = importedAtEpochMs,
                            defaultCity = defaultCity,
                        )
                    asText.contains('\n') && (asText.contains(';') || asText.contains(',') || asText.contains('\t')) ->
                        parseDelimited(asText)
                    else -> throw IllegalArgumentException(
                        "Yalnızca CSV, TSV, TXT, JSON veya XLSX resmî sicil çıktıları destekleniyor.",
                    )
                }
            }
        }
        return rowsToRecords(rows, source, importedAtEpochMs, defaultCity)
    }

    internal fun parseDelimited(text: String): List<List<String>> {
        val clean = text.removePrefix("\uFEFF")
        val lines = clean.lineSequence().filter { it.isNotBlank() }.toList()
        if (lines.isEmpty()) return emptyList()
        val delimiter = detectDelimiter(lines.take(5))
        return lines.map { parseDelimitedLine(it, delimiter) }
    }

    internal fun rowsToRecords(
        rows: List<List<String>>,
        source: OfficialRegistrySource,
        importedAtEpochMs: Long,
        defaultCity: String? = null,
    ): List<OfficialRegistryRecord> {
        if (rows.isEmpty()) return emptyList()
        val headerIndex = rows.indexOfFirst { row ->
            row.any { cell ->
                val normalized = normalizeHeader(cell)
                normalized in NAME_HEADERS || normalized in ADDRESS_HEADERS || normalized in PHONE_HEADERS
            }
        }
        if (headerIndex < 0) return emptyList()

        val headers = rows[headerIndex].map(::normalizeHeader)
        fun value(row: List<String>, aliases: Set<String>): String? {
            val index = headers.indexOfFirst { it in aliases }
            if (index < 0) return null
            return row.getOrNull(index)?.let(::sanitizeCell)?.takeIf(String::isNotBlank)
        }

        fun values(row: List<String>, aliases: Set<String>): List<String> =
            headers.mapIndexedNotNull { index, header ->
                if (header !in aliases) return@mapIndexedNotNull null
                row.getOrNull(index)?.let(::sanitizeCell)?.takeIf(String::isNotBlank)
            }

        fun phoneValue(row: List<String>): String? {
            val seen = linkedSetOf<String>()
            val display = mutableListOf<String>()
            values(row, PHONE_HEADERS)
                .flatMap { raw -> raw.split(Regex("[|;/,\\n]+")) }
                .mapNotNull(::sanitizePhone)
                .forEach { cleaned ->
                    val normalized = OfficialRegistryNormalizer.phone(cleaned)
                    if (normalized.isNotBlank() && seen.add(normalized)) {
                        display += cleaned
                    }
                }
            return display.takeIf { it.isNotEmpty() }?.joinToString(" / ")
        }

        return rows.drop(headerIndex + 1).mapNotNull { row ->
            val name = value(row, NAME_HEADERS) ?: return@mapNotNull null
            OfficialRegistryRecord(
                source = source,
                registrationNumber = value(row, REGISTRATION_HEADERS)
                    ?: value(row, MERSIS_HEADERS),
                businessName = name,
                status = value(row, STATUS_HEADERS),
                city = value(row, CITY_HEADERS)
                    ?: when (source) {
                        OfficialRegistrySource.ITO -> "İstanbul"
                        OfficialRegistrySource.CHAMBER -> defaultCity?.trim()?.takeIf(String::isNotBlank)
                        else -> null
                    },
                district = value(row, DISTRICT_HEADERS) ?: value(row, SEMT_HEADERS),
                neighborhood = value(row, NEIGHBORHOOD_HEADERS),
                address = value(row, ADDRESS_HEADERS),
                phone = phoneValue(row),
                website = value(row, WEBSITE_HEADERS)?.let(::sanitizeWebsite),
                importedAtEpochMs = importedAtEpochMs,
                naceCode = value(row, NACE_HEADERS),
                mersisNumber = value(row, MERSIS_HEADERS),
                signboardName = value(row, SIGNBOARD_HEADERS),
                businessType = value(row, BUSINESS_TYPE_HEADERS),
                email = value(row, EMAIL_HEADERS)?.let(::sanitizeEmail),
                chamberCode = value(row, CHAMBER_CODE_HEADERS),
                sourceRecordId = value(row, SOURCE_RECORD_ID_HEADERS),
                taxNumber = value(row, TAX_NUMBER_HEADERS),
            )
        }
    }

    internal fun parseAuthorizedJson(
        text: String,
        source: OfficialRegistrySource,
        importedAtEpochMs: Long,
        defaultCity: String? = null,
    ): List<OfficialRegistryRecord> {
        require(importedAtEpochMs > 0L) { "İçe aktarma zamanı geçersiz." }
        val trimmed = text.trim().removePrefix("\uFEFF")
        if (trimmed.isBlank()) return emptyList()

        val root: Any = if (trimmed.startsWith("[")) {
            org.json.JSONArray(trimmed)
        } else {
            JSONObject(trimmed)
        }
        val array = unwrapAuthorizedArray(root)
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val businessItem = item.optJSONObject("uyelikTemelBilgileri") ?: item
                val addressItem = preferredTobbAddress(item.optJSONArray("adresList"))
                val name = jsonValue(businessItem, JSON_NAME_KEYS)
                    ?: jsonValue(item, JSON_NAME_KEYS)
                    ?: continue
                val city = jsonValue(businessItem, JSON_CITY_KEYS)
                    ?: addressItem?.let { jsonValue(it, JSON_CITY_KEYS) }
                    ?: jsonValue(item, JSON_CITY_KEYS)
                    ?: when (source) {
                        OfficialRegistrySource.ITO -> "İstanbul"
                        OfficialRegistrySource.CHAMBER -> defaultCity?.trim()?.takeIf(String::isNotBlank)
                        else -> defaultCity?.trim()?.takeIf(String::isNotBlank)
                    }
                val phoneValues = buildList {
                    addAll(jsonValues(businessItem, JSON_PHONE_KEYS))
                    addAll(jsonValues(item, JSON_PHONE_KEYS))
                    addAll(tobbPhoneValues(item.optJSONArray("telefonList")))
                }
                add(
                    OfficialRegistryRecord(
                        source = source,
                        registrationNumber = jsonValue(businessItem, JSON_REGISTRATION_KEYS)
                            ?: jsonValue(item, JSON_REGISTRATION_KEYS)
                            ?: jsonValue(businessItem, JSON_MERSIS_KEYS)
                            ?: jsonValue(item, JSON_MERSIS_KEYS),
                        businessName = name,
                        status = jsonValue(businessItem, JSON_STATUS_KEYS)
                            ?: jsonValue(item, JSON_STATUS_KEYS),
                        city = city,
                        district = jsonValue(businessItem, JSON_DISTRICT_KEYS)
                            ?: addressItem?.let { jsonValue(it, JSON_DISTRICT_KEYS) }
                            ?: jsonValue(item, JSON_DISTRICT_KEYS),
                        neighborhood = jsonValue(businessItem, JSON_NEIGHBORHOOD_KEYS)
                            ?: addressItem?.let { jsonValue(it, JSON_NEIGHBORHOOD_KEYS) }
                            ?: jsonValue(item, JSON_NEIGHBORHOOD_KEYS),
                        address = jsonValue(businessItem, JSON_ADDRESS_KEYS)
                            ?: addressItem?.let(::tobbAddressText)
                            ?: jsonValue(item, JSON_ADDRESS_KEYS),
                        phone = phoneValues
                            .mapNotNull(::sanitizePhone)
                            .distinctBy(OfficialRegistryNormalizer::phone)
                            .takeIf { it.isNotEmpty() }
                            ?.joinToString(" / "),
                        website = (
                            jsonValue(businessItem, JSON_WEBSITE_KEYS)
                                ?: jsonValue(item, JSON_WEBSITE_KEYS)
                            )?.let(::sanitizeWebsite),
                        importedAtEpochMs = importedAtEpochMs,
                        naceCode = jsonValue(businessItem, JSON_NACE_KEYS)
                            ?: jsonValue(item, JSON_NACE_KEYS),
                        mersisNumber = jsonValue(businessItem, JSON_MERSIS_KEYS)
                            ?: jsonValue(item, JSON_MERSIS_KEYS),
                        signboardName = jsonValue(businessItem, JSON_SIGNBOARD_KEYS)
                            ?: jsonValue(item, JSON_SIGNBOARD_KEYS),
                        businessType = jsonValue(businessItem, JSON_BUSINESS_TYPE_KEYS)
                            ?: jsonValue(item, JSON_BUSINESS_TYPE_KEYS),
                        email = (
                            jsonValue(businessItem, JSON_EMAIL_KEYS)
                                ?: jsonValue(item, JSON_EMAIL_KEYS)
                            )?.let(::sanitizeEmail),
                        chamberCode = jsonValue(businessItem, JSON_CHAMBER_CODE_KEYS)
                            ?: jsonValue(item, JSON_CHAMBER_CODE_KEYS),
                        sourceRecordId = jsonValue(businessItem, JSON_SOURCE_RECORD_ID_KEYS)
                            ?: jsonValue(item, JSON_SOURCE_RECORD_ID_KEYS),
                        taxNumber = jsonValue(businessItem, JSON_TAX_NUMBER_KEYS)
                            ?: jsonValue(item, JSON_TAX_NUMBER_KEYS),
                    ),
                )
            }
        }
    }

    private fun unwrapAuthorizedArray(root: Any): org.json.JSONArray {
        if (root is org.json.JSONArray) return root
        val obj = root as? JSONObject
            ?: throw IllegalArgumentException("Resmî JSON kökü nesne veya dizi olmalı.")

        obj.optJSONArray("donusDegeri")?.let { return it }
        obj.optJSONObject("obResult")?.let { result ->
            result.optJSONArray("donusDegeri")?.let { return it }
            val nested = result.opt("donusDegeri")
            if (nested is String && nested.isNotBlank()) return unwrapAuthorizedArray(parseJsonValue(nested))
        }

        val direct = obj.opt("donusDegeri")
        if (direct is String && direct.isNotBlank()) return unwrapAuthorizedArray(parseJsonValue(direct))

        obj.optJSONArray("records")?.let { return it }
        obj.optJSONArray("data")?.let { return it }
        if (obj.optJSONObject("uyelikTemelBilgileri") != null) {
            return org.json.JSONArray().put(obj)
        }
        throw IllegalArgumentException(
            "JSON içinde TOBB obResult/donusDegeri veya records/data dizisi bulunamadı.",
        )
    }

    private fun preferredTobbAddress(
        array: org.json.JSONArray?,
    ): JSONObject? {
        if (array == null || array.length() == 0) return null
        val candidates = buildList {
            for (index in 0 until array.length()) {
                array.optJSONObject(index)?.let(::add)
            }
        }
        return candidates.firstOrNull { address ->
            jsonValue(address, setOf("bitisTarihi", "bitis tarihi")).isNullOrBlank() &&
                jsonValue(address, setOf("yazismaAdresi", "yazisma adresi")) == "1"
        } ?: candidates.firstOrNull { address ->
            jsonValue(address, setOf("bitisTarihi", "bitis tarihi")).isNullOrBlank()
        } ?: candidates.firstOrNull()
    }

    private fun tobbAddressText(item: JSONObject): String? =
        jsonValue(
            item,
            setOf("butunlesikAdres", "butunlesik adres", "serbestMetin", "serbest metin", "adres"),
        ) ?: listOfNotNull(
            jsonValue(item, setOf("mahalle")),
            jsonValue(item, setOf("cadde")),
            jsonValue(item, setOf("sokak")),
            jsonValue(item, setOf("bulvar")),
            jsonValue(item, setOf("meydan")),
            jsonValue(item, setOf("siteAdi", "site adi")),
            jsonValue(item, setOf("apartmanAdi", "apartman adi")),
            jsonValue(item, setOf("dkno", "dis kapi no")),
            jsonValue(item, setOf("ikno", "ic kapi no")),
            jsonValue(item, setOf("postaKodu", "posta kodu")),
        ).filter(String::isNotBlank)
            .joinToString(" ")
            .takeIf(String::isNotBlank)

    private fun tobbPhoneValues(array: org.json.JSONArray?): List<String> {
        if (array == null) return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val phone = jsonValue(item, setOf("telefonNo", "telefon no", "telefon"))
                    ?: continue
                val country = jsonValue(item, setOf("ulkeTelKodu", "ulke tel kodu"))
                    ?.takeIf { it.isNotBlank() && it != "90" }
                add(listOfNotNull(country, phone).joinToString(" "))
            }
        }
    }

    private fun parseJsonValue(value: String): Any {
        val trimmed = value.trim()
        return when {
            trimmed.startsWith("[") -> org.json.JSONArray(trimmed)
            trimmed.startsWith("{") -> JSONObject(trimmed)
            else -> throw IllegalArgumentException("JSON dönüş değeri geçersiz.")
        }
    }

    private fun jsonValue(item: JSONObject, keys: Set<String>): String? {
        val normalizedKeys = keys.map(OfficialRegistryNormalizer::text).toSet()
        item.keys().forEach { rawKey ->
            if (OfficialRegistryNormalizer.text(rawKey) !in normalizedKeys) return@forEach
            val value = item.opt(rawKey)
            if (value == null || value == JSONObject.NULL) return@forEach
            return sanitizeCell(value.toString()).takeIf(String::isNotBlank)
        }
        return null
    }

    private fun jsonValues(item: JSONObject, keys: Set<String>): List<String> {
        val normalizedKeys = keys.map(OfficialRegistryNormalizer::text).toSet()
        return buildList {
            item.keys().forEach { rawKey ->
                if (OfficialRegistryNormalizer.text(rawKey) !in normalizedKeys) return@forEach
                val value = item.opt(rawKey)
                when (value) {
                    null, JSONObject.NULL -> Unit
                    is org.json.JSONArray -> {
                        for (index in 0 until value.length()) {
                            val child = value.opt(index)?.toString()?.let(::sanitizeCell)
                            if (!child.isNullOrBlank()) add(child)
                        }
                    }
                    else -> {
                        val child = sanitizeCell(value.toString())
                        if (child.isNotBlank()) add(child)
                    }
                }
            }
        }
    }

    internal fun parseXlsx(bytes: ByteArray): List<List<String>> {
        var sharedStringsXml: ByteArray? = null
        var sheetXml: ByteArray? = null
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                when {
                    entry.name == "xl/sharedStrings.xml" -> sharedStringsXml = zip.readBytes()
                    sheetXml == null &&
                        entry.name.startsWith("xl/worksheets/") &&
                        entry.name.endsWith(".xml") -> sheetXml = zip.readBytes()
                }
                zip.closeEntry()
            }
        }
        val sheet = sheetXml ?: throw IllegalArgumentException("XLSX içinde çalışma sayfası bulunamadı.")
        val sharedStrings = sharedStringsXml?.let(::parseSharedStrings).orEmpty()
        val document = secureFactory().newDocumentBuilder().parse(ByteArrayInputStream(sheet))
        val rowNodes = document.getElementsByTagNameNS("*", "row")
        val rows = mutableListOf<List<String>>()

        for (rowIndex in 0 until rowNodes.length) {
            val rowElement = rowNodes.item(rowIndex) as? Element ?: continue
            val cellNodes = rowElement.getElementsByTagNameNS("*", "c")
            val values = linkedMapOf<Int, String>()
            var maxIndex = -1
            for (cellIndex in 0 until cellNodes.length) {
                val cell = cellNodes.item(cellIndex) as? Element ?: continue
                val ref = cell.getAttribute("r")
                val index = columnIndex(ref)
                if (index < 0) continue
                maxIndex = maxOf(maxIndex, index)
                val type = cell.getAttribute("t")
                val value = when (type) {
                    "s" -> {
                        val raw = firstChildText(cell, "v")
                        raw.toIntOrNull()?.let(sharedStrings::getOrNull).orEmpty()
                    }
                    "inlineStr" -> descendantText(cell, "t")
                    else -> firstChildText(cell, "v")
                }
                values[index] = value
            }
            if (maxIndex >= 0) {
                rows += (0..maxIndex).map { values[it].orEmpty() }
            }
        }
        return rows
    }

    private fun parseSharedStrings(xml: ByteArray): List<String> {
        val document = secureFactory().newDocumentBuilder().parse(ByteArrayInputStream(xml))
        val nodes = document.getElementsByTagNameNS("*", "si")
        return buildList {
            for (index in 0 until nodes.length) {
                val element = nodes.item(index) as? Element ?: continue
                add(descendantText(element, "t"))
            }
        }
    }

    private fun secureFactory(): DocumentBuilderFactory =
        DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            isExpandEntityReferences = false
            runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
            runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
            runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
            runCatching { setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false) }
            runCatching { isXIncludeAware = false }
        }

    private fun firstChildText(element: Element, tagName: String): String =
        element.getElementsByTagNameNS("*", tagName).item(0)?.textContent.orEmpty()

    private fun descendantText(element: Element, tagName: String): String {
        val nodes = element.getElementsByTagNameNS("*", tagName)
        return buildString {
            for (index in 0 until nodes.length) append(nodes.item(index)?.textContent.orEmpty())
        }
    }

    private fun columnIndex(ref: String): Int {
        val letters = ref.takeWhile(Char::isLetter)
        if (letters.isEmpty()) return -1
        var result = 0
        letters.uppercase(Locale.ROOT).forEach { char ->
            result = result * 26 + (char - 'A' + 1)
        }
        return result - 1
    }

    private fun detectDelimiter(lines: List<String>): Char {
        val candidates = listOf(';', '\t', ',')
        return candidates.maxByOrNull { delimiter ->
            lines.sumOf { line -> line.count { it == delimiter } }
        } ?: ';'
    }

    private fun parseDelimitedLine(line: String, delimiter: Char): List<String> {
        val values = mutableListOf<String>()
        val current = StringBuilder()
        var quoted = false
        var index = 0
        while (index < line.length) {
            val char = line[index]
            when {
                char == '"' && quoted && index + 1 < line.length && line[index + 1] == '"' -> {
                    current.append('"')
                    index++
                }
                char == '"' -> quoted = !quoted
                char == delimiter && !quoted -> {
                    values += current.toString()
                    current.clear()
                }
                else -> current.append(char)
            }
            index++
        }
        values += current.toString()
        return values
    }

    private fun normalizeHeader(value: String): String =
        OfficialRegistryNormalizer.text(value)

    private fun sanitizeCell(value: String): String =
        value.replace(Regex("[\\u0000-\\u0008\\u000B\\u000C\\u000E-\\u001F]"), "")
            .trim()
            .take(1_000)

    private fun sanitizePhone(value: String): String? {
        val trimmed = sanitizeCell(value)
        val digits = trimmed.filter(Char::isDigit)
        if (digits.length < 7) return null
        return trimmed.take(64)
    }

    private fun sanitizeWebsite(value: String): String? {
        val trimmed = sanitizeCell(value)
        if (trimmed.isBlank()) return null
        val normalized = when {
            trimmed.startsWith("https://", ignoreCase = true) -> trimmed
            trimmed.startsWith("http://", ignoreCase = true) -> trimmed
            trimmed.contains('.') && !trimmed.contains(' ') -> "https://$trimmed"
            else -> return null
        }
        return normalized.take(512)
    }

    private fun sanitizeEmail(value: String): String? {
        val trimmed = sanitizeCell(value).lowercase(Locale.ROOT)
        if (!trimmed.matches(Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$"))) return null
        return trimmed.take(320)
    }

    private val NAME_HEADERS = setOf(
        "firma unvani",
        "ticaret unvani",
        "unvan",
        "firma adi",
        "isletme adi",
        "isyeri unvani",
        "nokta adi",
        "firmaunvani",
    )
    private val REGISTRATION_HEADERS = setOf(
        "sicil no",
        "sicil numarasi",
        "oda sicil no",
        "oda sicil numarasi",
        "ticaret sicil no",
        "ticaret sicil numarasi",
        "esnaf sicil no",
        "esnaf sicil numarasi",
        "sicil kayit no",
        "sicil kayit numarasi",
        "kayit no",
        "kayit numarasi",
        "uyeodasicilno",
        "ticaretsicilno",
        "esnafsicilno",
    )
    private val STATUS_HEADERS = setOf(
        "durum",
        "uyelik durumu",
        "uyelik durum",
        "tescil durumu",
        "faaliyet durumu",
        "sicil durumu",
        "uyelikdurum",
        "durumkodu",
    )
    private val CITY_HEADERS = setOf("il", "sehir", "city")
    private val DISTRICT_HEADERS = setOf("ilce", "district")
    private val SEMT_HEADERS = setOf("semt", "bolge")
    private val NACE_HEADERS = setOf(
        "nace", "nace kodu", "nace kod", "nace code",
        "nacekod", "anafaaliyetkodu", "faaliyetkodu",
    )
    private val NEIGHBORHOOD_HEADERS = setOf("mahalle", "mah", "neighborhood")
    private val ADDRESS_HEADERS = setOf(
        "adres",
        "acik adres",
        "is yeri adresi",
        "isyeri adresi",
        "merkez adresi",
        "firma adresi",
        "tescilli adresi",
        "isyeri adres",
        "is yeri adres",
        "buro adresi",
        "buro adres",
        "uretim yeri adresi",
        "uretim adresi",
    )
    private val PHONE_HEADERS = setOf(
        "telefon",
        "telefon no",
        "telefon numarasi",
        "telefon 1",
        "telefon 2",
        "telefon1",
        "telefon2",
        "tel",
        "tel 1",
        "tel 2",
        "firma tel",
        "firma telefonu",
        "is telefonu",
        "isyeri tel",
        "is yeri tel",
        "isyeri telefonu",
        "is yeri telefonu",
        "buro tel",
        "buro telefonu",
        "iletisim telefonu",
        "gsm",
        "gsm no",
        "gsm numarasi",
        "cep",
        "cep telefonu",
        "cep telefon",
        "mobil",
        "mobil telefon",
        "mobile",
        "telefon kodlu",
        "isyeri tel kodlu",
        "buro tel kodlu",
        "telefonno",
        "telefonnumarasi",
    )
    private val WEBSITE_HEADERS = setOf(
        "web",
        "website",
        "web sitesi",
        "internet sitesi",
        "webadresi",
    )

    private val MERSIS_HEADERS = setOf(
        "mersis no", "mersis numarasi", "mersisno",
    )
    private val SIGNBOARD_HEADERS = setOf(
        "tabela unvani", "tabela adi", "signboard name", "tabelaunvani",
    )
    private val BUSINESS_TYPE_HEADERS = setOf(
        "firma tipi", "isletme turu", "firma turu", "ana faaliyet aciklamasi",
        "firmatipi", "anafaaliyetaciklamasi", "faaliyetdetay",
    )
    private val EMAIL_HEADERS = setOf(
        "e posta", "eposta", "email", "e mail", "epostaadres", "epostaadresi",
    )
    private val CHAMBER_CODE_HEADERS = setOf(
        "oda borsa no", "oda kodu", "oda no", "odaborsano", "odakodu",
    )
    private val SOURCE_RECORD_ID_HEADERS = setOf(
        "uye oid", "uyeoid", "firma oid", "firmaoid", "kayit oid", "kayitoid",
    )
    private val TAX_NUMBER_HEADERS = setOf(
        "vergi no", "vergi numarasi", "vergi kimlik no", "vergi kimlik numarasi",
        "vergino", "vergikimlikno",
    )

    private val JSON_NAME_KEYS = NAME_HEADERS + setOf("unvan", "Unvan")
    private val JSON_REGISTRATION_KEYS = REGISTRATION_HEADERS
    private val JSON_STATUS_KEYS = STATUS_HEADERS
    private val JSON_CITY_KEYS = CITY_HEADERS
    private val JSON_DISTRICT_KEYS = DISTRICT_HEADERS + setOf("ilce")
    private val JSON_NEIGHBORHOOD_KEYS = NEIGHBORHOOD_HEADERS
    private val JSON_ADDRESS_KEYS = ADDRESS_HEADERS
    private val JSON_PHONE_KEYS = PHONE_HEADERS + setOf("telefonList", "telefonNo")
    private val JSON_WEBSITE_KEYS = WEBSITE_HEADERS
    private val JSON_NACE_KEYS = NACE_HEADERS
    private val JSON_MERSIS_KEYS = MERSIS_HEADERS
    private val JSON_SIGNBOARD_KEYS = SIGNBOARD_HEADERS
    private val JSON_BUSINESS_TYPE_KEYS = BUSINESS_TYPE_HEADERS
    private val JSON_EMAIL_KEYS = EMAIL_HEADERS
    private val JSON_CHAMBER_CODE_KEYS = CHAMBER_CODE_HEADERS
    private val JSON_SOURCE_RECORD_ID_KEYS = SOURCE_RECORD_ID_HEADERS
    private val JSON_TAX_NUMBER_KEYS = TAX_NUMBER_HEADERS
}

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
    MERSIS,
    ESBIS;

    val descriptor: DataSourceDescriptor
        get() = when (this) {
            ITO -> DataSourceDescriptor(
                id = "official-ito",
                name = "İTO Resmî Üye/Firma Kaydı",
                publisher = "İstanbul Ticaret Odası",
                licenseOrTerms = "https://www.ito.org.tr/tr/iletisim/sikca-sorulan-sorular",
                sourceUrl = "https://bilgibankasi.ito.org.tr/",
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
                ITO -> SourceAccessMethod.OFFICIAL_BULK_REQUEST
                MERSIS, ESBIS -> SourceAccessMethod.AUTHENTICATED_EXPORT
            },
            scope = "Kullanıcının resmî kanaldan temin ettiği firma/esnaf çıktısındaki işletme adı, sicil durumu, adres, telefon ve web alanları",
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
)

data class OfficialRegistryImportSummary(
    val source: OfficialRegistrySource,
    val importedCount: Int,
    val activeCount: Int,
    val inactiveCount: Int,
    val unknownStatusCount: Int,
    val fileName: String,
)

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
        ).distinctBy { record ->
            listOf(
                record.source.name,
                record.registrationNumber.orEmpty(),
                OfficialRegistryNormalizer.text(record.businessName),
                OfficialRegistryNormalizer.text(record.district.orEmpty()),
                OfficialRegistryNormalizer.phone(record.phone),
            ).joinToString("|")
        }.take(MAX_RECORDS)

        require(parsed.isNotEmpty()) {
            "Dosyada işletme adı içeren kullanılabilir resmî kayıt bulunamadı."
        }

        val target = sourceFile(source)
        val temp = File(directory, target.name + ".tmp")
        temp.bufferedWriter(Charsets.UTF_8).use { writer ->
            parsed.forEach { record ->
                writer.appendLine(encode(record).toString())
            }
        }
        if (target.exists() && !target.delete()) {
            temp.delete()
            throw IOException("Eski resmî sicil önbelleği değiştirilemedi.")
        }
        if (!temp.renameTo(target)) {
            temp.delete()
            throw IOException("Resmî sicil verisi güvenli şekilde kaydedilemedi.")
        }

        return OfficialRegistryImportSummary(
            source = source,
            importedCount = parsed.size,
            activeCount = parsed.count { it.status?.let(OfficialRegistryStatus::isActive) == true },
            inactiveCount = parsed.count { it.status?.let(OfficialRegistryStatus::isInactive) == true },
            unknownStatusCount = parsed.count { status ->
                status.status.isNullOrBlank() ||
                    (!OfficialRegistryStatus.isActive(status.status) &&
                        !OfficialRegistryStatus.isInactive(status.status))
            },
            fileName = fileName,
        )
    }

    fun recordsFor(
        city: String,
        district: String?,
    ): List<OfficialRegistryRecord> {
        val normalizedCity = OfficialRegistryNormalizer.text(city)
        val normalizedDistrict = district
            ?.takeUnless { it.isBlank() || it.equals("Tümü", ignoreCase = true) }
            ?.let(OfficialRegistryNormalizer::text)

        return OfficialRegistrySource.entries.flatMap { source ->
            readSource(source).filter { record ->
                val cityMatches = record.city.isNullOrBlank() ||
                    OfficialRegistryNormalizer.text(record.city) == normalizedCity
                val districtMatches = normalizedDistrict == null ||
                    record.district.isNullOrBlank() ||
                    OfficialRegistryNormalizer.text(record.district) == normalizedDistrict
                cityMatches && districtMatches
            }
        }
    }

    fun count(source: OfficialRegistrySource): Int = readSource(source).size

    private fun readSource(source: OfficialRegistrySource): List<OfficialRegistryRecord> {
        val file = sourceFile(source)
        if (!file.exists()) return emptyList()
        return file.useLines(Charsets.UTF_8) { lines ->
            lines.mapNotNull { line ->
                line.takeIf(String::isNotBlank)?.let { raw ->
                    runCatching { decode(JSONObject(raw), source) }.getOrNull()
                }
            }.take(MAX_RECORDS).toList()
        }
    }

    private fun sourceFile(source: OfficialRegistrySource): File =
        File(directory, "registry_${source.name.lowercase(Locale.ROOT)}.jsonl")

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
        )

    private fun optionalString(item: JSONObject, key: String): String? =
        item.optString(key).trim().takeIf { it.isNotBlank() && it != "null" }

    private companion object {
        const val DIRECTORY_NAME = "official_registry"
        const val MAX_IMPORT_BYTES = 25 * 1024 * 1024
        const val MAX_RECORDS = 100_000
    }
}

object OfficialRegistryEnricher {
    fun enrich(
        businesses: List<VerifiedBusiness>,
        records: List<OfficialRegistryRecord>,
    ): List<VerifiedBusiness> {
        if (businesses.isEmpty() || records.isEmpty()) return businesses

        val result = mutableListOf<VerifiedBusiness>()
        businesses.forEach { business ->
            val match = bestMatch(business, records)
            if (match == null) {
                result += business
                return@forEach
            }

            if (match.status?.let(OfficialRegistryStatus::isInactive) == true) {
                return@forEach
            }

            val fieldsUsed = linkedSetOf<String>()
            val officialAddress = match.address?.trim()?.takeIf(String::isNotEmpty)
            val officialPhone = match.phone?.trim()?.takeIf(String::isNotEmpty)
            val officialWebsite = match.website?.trim()?.takeIf(String::isNotEmpty)
            val officialDistrict = match.district?.trim()?.takeIf(String::isNotEmpty)
            val officialNeighborhood = match.neighborhood?.trim()?.takeIf(String::isNotEmpty)

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
                    registrationNumber = match.registrationNumber,
                    status = match.status,
                    importedAtEpochMs = match.importedAtEpochMs,
                    fieldsUsed = fieldsUsed,
                ),
            )
        }
        return result
    }

    private fun bestMatch(
        business: VerifiedBusiness,
        records: List<OfficialRegistryRecord>,
    ): OfficialRegistryRecord? {
        val scored = records.mapNotNull { record ->
            val score = matchScore(business, record)
            score.takeIf { it >= MIN_MATCH_SCORE }?.let { record to it }
        }.sortedByDescending { it.second }

        val best = scored.firstOrNull() ?: return null
        val runnerUp = scored.getOrNull(1)
        if (runnerUp != null && runnerUp.second == best.second &&
            !equivalent(best.first, runnerUp.first)
        ) {
            return null
        }
        return best.first
    }

    private fun matchScore(
        business: VerifiedBusiness,
        record: OfficialRegistryRecord,
    ): Int {
        var score = 0
        val businessPhone = OfficialRegistryNormalizer.phone(business.phone)
        val officialPhone = OfficialRegistryNormalizer.phone(record.phone)
        if (businessPhone.isNotEmpty() && officialPhone.isNotEmpty() && businessPhone == officialPhone) {
            score += 100
        }

        val businessName = OfficialRegistryNormalizer.text(business.name)
        val officialName = OfficialRegistryNormalizer.text(record.businessName)
        if (businessName.isNotEmpty() && businessName == officialName) score += 60

        val businessCity = OfficialRegistryNormalizer.text(business.city)
        val officialCity = OfficialRegistryNormalizer.text(record.city.orEmpty())
        if (officialCity.isNotEmpty() && businessCity == officialCity) score += 10

        val businessDistrict = OfficialRegistryNormalizer.text(business.district)
        val officialDistrict = OfficialRegistryNormalizer.text(record.district.orEmpty())
        if (officialDistrict.isNotEmpty() && businessDistrict == officialDistrict) score += 20

        val businessAddress = OfficialRegistryNormalizer.text(business.address.orEmpty())
        val officialAddress = OfficialRegistryNormalizer.text(record.address.orEmpty())
        if (businessAddress.isNotEmpty() && officialAddress.isNotEmpty()) {
            val businessTokens = businessAddress.split(" ").filter { it.length >= 4 }.toSet()
            val officialTokens = officialAddress.split(" ").filter { it.length >= 4 }.toSet()
            if (businessTokens.isNotEmpty() && officialTokens.isNotEmpty() &&
                businessTokens.intersect(officialTokens).size >= 2
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

    fun phone(value: String?): String {
        val digits = value.orEmpty().filter(Char::isDigit)
        return if (digits.length >= 10) digits.takeLast(10) else digits
    }
}

object OfficialRegistryImportParser {
    fun parse(
        bytes: ByteArray,
        fileName: String,
        source: OfficialRegistrySource,
        importedAtEpochMs: Long,
    ): List<OfficialRegistryRecord> {
        val rows = when {
            fileName.endsWith(".xlsx", ignoreCase = true) -> parseXlsx(bytes)
            fileName.endsWith(".csv", ignoreCase = true) ||
                fileName.endsWith(".txt", ignoreCase = true) ||
                fileName.endsWith(".tsv", ignoreCase = true) -> parseDelimited(bytes.toString(Charsets.UTF_8))
            else -> {
                val asText = bytes.toString(Charsets.UTF_8)
                if (asText.contains('\n') && (asText.contains(';') || asText.contains(',') || asText.contains('\t'))) {
                    parseDelimited(asText)
                } else {
                    throw IllegalArgumentException("Yalnızca CSV, TSV, TXT veya XLSX resmî sicil çıktıları destekleniyor.")
                }
            }
        }
        return rowsToRecords(rows, source, importedAtEpochMs)
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

        return rows.drop(headerIndex + 1).mapNotNull { row ->
            val name = value(row, NAME_HEADERS) ?: return@mapNotNull null
            OfficialRegistryRecord(
                source = source,
                registrationNumber = value(row, REGISTRATION_HEADERS),
                businessName = name,
                status = value(row, STATUS_HEADERS),
                city = value(row, CITY_HEADERS),
                district = value(row, DISTRICT_HEADERS),
                neighborhood = value(row, NEIGHBORHOOD_HEADERS),
                address = value(row, ADDRESS_HEADERS),
                phone = value(row, PHONE_HEADERS)?.let(::sanitizePhone),
                website = value(row, WEBSITE_HEADERS)?.let(::sanitizeWebsite),
                importedAtEpochMs = importedAtEpochMs,
            )
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

    private val NAME_HEADERS = setOf(
        "firma unvani",
        "ticaret unvani",
        "unvan",
        "firma adi",
        "isletme adi",
        "isyeri unvani",
        "nokta adi",
    )
    private val REGISTRATION_HEADERS = setOf(
        "sicil no",
        "sicil numarasi",
        "oda sicil no",
        "oda sicil numarasi",
        "ticaret sicil no",
        "ticaret sicil numarasi",
        "mersis no",
        "mersis numarasi",
        "esnaf sicil no",
        "esnaf sicil numarasi",
    )
    private val STATUS_HEADERS = setOf(
        "durum",
        "uyelik durumu",
        "tescil durumu",
        "faaliyet durumu",
        "sicil durumu",
    )
    private val CITY_HEADERS = setOf("il", "sehir", "city")
    private val DISTRICT_HEADERS = setOf("ilce", "district")
    private val NEIGHBORHOOD_HEADERS = setOf("mahalle", "mah", "neighborhood")
    private val ADDRESS_HEADERS = setOf(
        "adres",
        "acik adres",
        "is yeri adresi",
        "isyeri adresi",
        "merkez adresi",
        "firma adresi",
    )
    private val PHONE_HEADERS = setOf(
        "telefon",
        "telefon no",
        "telefon numarasi",
        "tel",
        "is telefonu",
        "firma telefonu",
    )
    private val WEBSITE_HEADERS = setOf(
        "web",
        "website",
        "web sitesi",
        "internet sitesi",
    )
}

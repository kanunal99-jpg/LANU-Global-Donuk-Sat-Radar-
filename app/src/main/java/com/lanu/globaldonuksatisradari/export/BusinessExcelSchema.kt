package com.lanu.globaldonuksatisradari.export

import com.lanu.globaldonuksatisradari.crm.CrmCustomer
import com.lanu.globaldonuksatisradari.crm.CrmRegistryStatus
import com.lanu.globaldonuksatisradari.crm.CrmStage
import com.lanu.globaldonuksatisradari.crm.DataQuality
import com.lanu.globaldonuksatisradari.data.BusinessCategoryLabels
import com.lanu.globaldonuksatisradari.data.OfficialRegistryMatcher
import com.lanu.globaldonuksatisradari.data.OfficialRegistryRecord
import com.lanu.globaldonuksatisradari.data.OfficialRegistrySource
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

data class BusinessExcelOfficialSnapshot(
    val tradeTitle: String? = null,
    val status: String? = null,
    val sources: String? = null,
    val registryOffice: String? = null,
    val registrationNumber: String? = null,
    val mersisNumber: String? = null,
    val registryEvent: String? = null,
    val publicationDate: String? = null,
    val registrationDate: String? = null,
    val gazetteNumber: String? = null,
    val gazettePage: String? = null,
    val sourceReference: String? = null,
    val naceCode: String? = null,
    val taxOrNationalId: String? = null,
    val reportedAddress: String? = null,
)

object BusinessExcelSchema {
    val commonHeaders: List<String> = listOf(
        "Ad Soyad",
        "Nokta Adı",
        "Tabela Adı",
        "Ticari Unvan",
        "İşletme Türü",
        "CRM Aşaması",
        "Güncel Durum",
        "Sicil Kaynağı",
        "Sicil Müdürlüğü",
        "Sicil No",
        "MERSİS No",
        "Sicil Olayı",
        "Yayın Tarihi",
        "Tescil Tarihi",
        "Gazete Sayı",
        "Gazete Sayfa",
        "Kaynak Referansı",
        "NACE",
        "TC/Vergi No",
        "Telefon No",
        "Web Sitesi",
        "İl",
        "İlçe",
        "Mahalle",
        "Açık Adres",
        "Sicil/İlan Adresi",
        "X",
        "Y",
        "Konum Bilgileri",
        "Kayıt Kaynağı",
        "Veri Kalitesi",
    )

    fun customerValues(
        customer: CrmCustomer,
        officialRecords: List<OfficialRegistryRecord> = emptyList(),
    ): List<String> {
        val official = BusinessExcelOfficialResolver.resolve(customer, officialRecords)
        val mapLink = if (customer.latitude != null && customer.longitude != null) {
            "https://maps.google.com/?q=${customer.latitude},${customer.longitude}"
        } else {
            ""
        }
        return listOf(
            customer.contactName.orEmpty(),
            customer.businessName,
            customer.signboardName.orEmpty(),
            official.tradeTitle.orEmpty(),
            BusinessCategoryLabels.displayName(customer.businessType)
                ?: customer.businessType.orEmpty(),
            stageLabel(customer.stage),
            official.status ?: registryStatusLabel(customer.registryStatus),
            official.sources ?: customer.registrySource.orEmpty(),
            official.registryOffice.orEmpty(),
            official.registrationNumber ?: customer.registryNumber.orEmpty(),
            official.mersisNumber.orEmpty(),
            official.registryEvent.orEmpty(),
            official.publicationDate.orEmpty(),
            official.registrationDate.orEmpty(),
            official.gazetteNumber.orEmpty(),
            official.gazettePage.orEmpty(),
            official.sourceReference.orEmpty(),
            official.naceCode.orEmpty(),
            customer.taxOrNationalId ?: official.taxOrNationalId.orEmpty(),
            customer.phone.orEmpty(),
            customer.website.orEmpty(),
            customer.city,
            customer.district,
            customer.neighborhood.orEmpty(),
            customer.address.orEmpty(),
            official.reportedAddress.orEmpty(),
            customer.longitude?.toString().orEmpty(),
            customer.latitude?.toString().orEmpty(),
            mapLink,
            customer.businessSourceId,
            dataQualityLabel(customer.dataQuality),
        )
    }

    fun registryValues(record: OfficialRegistryRecord): List<String> {
        val currentAddress = if (record.source == OfficialRegistrySource.TTSG) "" else record.address.orEmpty()
        return listOf(
            "",
            record.businessName,
            "",
            record.businessName,
            record.naceCode?.let { "NACE $it" }.orEmpty(),
            "",
            officialStatusLabel(record),
            sourceLabel(record.source),
            record.registryOffice.orEmpty(),
            record.registrationNumber.orEmpty(),
            record.mersisNumber.orEmpty(),
            record.registryEvent.orEmpty(),
            record.publicationDate.orEmpty(),
            record.registrationDate.orEmpty(),
            record.gazetteNumber.orEmpty(),
            record.gazettePage.orEmpty(),
            record.sourceReference.orEmpty(),
            record.naceCode.orEmpty(),
            record.taxOrNationalId.orEmpty(),
            record.phone.orEmpty(),
            record.website.orEmpty(),
            record.city.orEmpty(),
            record.district.orEmpty(),
            record.neighborhood.orEmpty(),
            currentAddress,
            record.address.orEmpty(),
            "",
            "",
            "",
            "Resmî Sicil",
            if (!record.registrationNumber.isNullOrBlank() || !record.mersisNumber.isNullOrBlank()) {
                "Resmî kimlik doğrulandı"
            } else {
                "Sicil kimliği eksik"
            },
        )
    }

    fun sourceLabel(source: OfficialRegistrySource): String = when (source) {
        OfficialRegistrySource.ITO -> "İTO"
        OfficialRegistrySource.CHAMBER -> "ODA"
        OfficialRegistrySource.TOBB -> "TOBB"
        OfficialRegistrySource.MERSIS -> "MERSİS"
        OfficialRegistrySource.ESBIS -> "ESBİS"
        OfficialRegistrySource.TTSG -> "TTSG"
    }

    private fun registryStatusLabel(status: CrmRegistryStatus): String = when (status) {
        CrmRegistryStatus.ACTIVE -> "AKTİF"
        CrmRegistryStatus.INACTIVE -> "PASİF"
        CrmRegistryStatus.UNVERIFIED -> "DOĞRULANMADI"
    }

    private fun stageLabel(stage: CrmStage): String = when (stage) {
        CrmStage.PROSPECT -> "Potansiyel"
        CrmStage.VISIT -> "Ziyaret"
        CrmStage.MEETING -> "Görüşme"
        CrmStage.PROPOSAL -> "Teklif"
        CrmStage.SAMPLE -> "Numune"
        CrmStage.ORDER -> "Sipariş"
        CrmStage.ACTIVE_CUSTOMER -> "Aktif Müşteri"
        CrmStage.LOST -> "Kaybedildi"
    }

    private fun dataQualityLabel(quality: DataQuality): String = when (quality) {
        DataQuality.OBSERVED -> "Gözlemlendi"
        DataQuality.ESTIMATED -> "Tahmini"
        DataQuality.USER_ENTERED -> "Kullanıcı Girişi"
        DataQuality.UNKNOWN -> "Bilinmiyor"
    }
}

object BusinessExcelOfficialResolver {
    fun resolve(
        customer: CrmCustomer,
        records: List<OfficialRegistryRecord>,
    ): BusinessExcelOfficialSnapshot {
        if (records.isEmpty()) {
            return BusinessExcelOfficialSnapshot(
                status = statusFromCustomer(customer),
                sources = customer.registrySource,
                registrationNumber = customer.registryNumber,
                taxOrNationalId = customer.taxOrNationalId,
            )
        }

        val matched = OfficialRegistrySource.entries.mapNotNull { source ->
            val sourceRecords = records.filter { it.source == source }
            if (sourceRecords.isEmpty()) return@mapNotNull null
            exactMatch(customer, sourceRecords)
                ?: OfficialRegistryMatcher.bestMatch(
                    name = customer.businessName,
                    city = customer.city,
                    district = customer.district,
                    address = customer.address,
                    phone = customer.phone,
                    records = if (source == OfficialRegistrySource.TTSG) {
                        sourceRecords.sortedByDescending(::publicationSortKey)
                    } else {
                        sourceRecords
                    },
                )
        }

        val ttsg = matched.firstOrNull { it.source == OfficialRegistrySource.TTSG }
        val currentRegistry = matched.firstOrNull {
            it.source != OfficialRegistrySource.TTSG && !it.status.isNullOrBlank()
        }
        val preferredIdentity = matched.firstOrNull {
            !it.registrationNumber.isNullOrBlank() || !it.mersisNumber.isNullOrBlank()
        }

        val sources = (
            listOfNotNull(customer.registrySource?.trim()?.takeIf(String::isNotBlank)) +
                matched.map { BusinessExcelSchema.sourceLabel(it.source) }
            )
            .distinct()
            .joinToString(" / ")
            .takeIf(String::isNotBlank)

        return BusinessExcelOfficialSnapshot(
            tradeTitle = matched
                .firstOrNull { it.source != OfficialRegistrySource.TTSG }
                ?.businessName
                ?: ttsg?.businessName,
            status = statusFromCustomer(customer)
                .takeUnless { it == "DOĞRULANMADI" }
                ?: currentRegistry?.let(::officialStatusLabel),
            sources = sources,
            registryOffice = matched.firstNotNullOfOrNull { it.registryOffice?.takeIf(String::isNotBlank) },
            registrationNumber = preferredIdentity?.registrationNumber
                ?: customer.registryNumber,
            mersisNumber = matched.firstNotNullOfOrNull { it.mersisNumber?.takeIf(String::isNotBlank) },
            registryEvent = ttsg?.registryEvent,
            publicationDate = ttsg?.publicationDate,
            registrationDate = ttsg?.registrationDate,
            gazetteNumber = ttsg?.gazetteNumber,
            gazettePage = ttsg?.gazettePage,
            sourceReference = ttsg?.sourceReference,
            naceCode = matched.firstNotNullOfOrNull { it.naceCode?.takeIf(String::isNotBlank) },
            taxOrNationalId = customer.taxOrNationalId
                ?: matched.firstNotNullOfOrNull { it.taxOrNationalId?.takeIf(String::isNotBlank) },
            reportedAddress = ttsg?.address
                ?: matched.firstNotNullOfOrNull { it.address?.takeIf(String::isNotBlank) },
        )
    }

    private fun exactMatch(
        customer: CrmCustomer,
        records: List<OfficialRegistryRecord>,
    ): OfficialRegistryRecord? {
        val registryNumber = customer.registryNumber?.trim()?.takeIf(String::isNotBlank)
        val taxId = customer.taxOrNationalId?.filter(Char::isDigit)?.takeIf(String::isNotBlank)

        val exact = records.filter { record ->
            (registryNumber != null && (
                record.registrationNumber?.trim() == registryNumber ||
                    record.mersisNumber?.trim() == registryNumber
                )) ||
                (taxId != null && record.taxOrNationalId?.filter(Char::isDigit) == taxId)
        }
        if (exact.isEmpty()) return null
        return if (records.firstOrNull()?.source == OfficialRegistrySource.TTSG) {
            exact.maxByOrNull(::publicationSortKey)
        } else {
            exact.maxByOrNull(OfficialRegistryRecord::importedAtEpochMs)
        }
    }

    private fun publicationSortKey(record: OfficialRegistryRecord): Long =
        record.publicationDate
            ?.trim()
            ?.let { value ->
                runCatching {
                    LocalDate.parse(value, DATE_FORMATTER).toEpochDay()
                }.getOrNull()
            }
            ?: Long.MIN_VALUE

    private fun officialStatusLabel(record: OfficialRegistryRecord): String {
        if (record.status.isNullOrBlank()) {
            return if (record.source == OfficialRegistrySource.TTSG) {
                "Güncel durum doğrulanmadı"
            } else {
                "Durum belirtilmemiş"
            }
        }
        val normalized = record.status
            .trim()
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
        return when {
            normalized == "faal" ||
                normalized == "aktif" ||
                normalized.contains("faal uye") ||
                normalized.contains("faal kayit") -> "FAAL"
            listOf(
                "terkin",
                "kapali",
                "kapanmis",
                "kapanis",
                "pasif",
                "askida",
                "aski",
                "silinmis",
                "tasfiye sonu",
                "tasfiyenin sona ermesi",
                "tasfiyenin sonu",
            ).any { token -> normalized == token || normalized.contains(token) } -> "AKTİF DEĞİL"
            else -> record.status.trim()
        }
    }

    private fun statusFromCustomer(customer: CrmCustomer): String = when (customer.registryStatus) {
        CrmRegistryStatus.ACTIVE -> "AKTİF"
        CrmRegistryStatus.INACTIVE -> "PASİF"
        CrmRegistryStatus.UNVERIFIED -> "DOĞRULANMADI"
    }

    private val DATE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.uuuu", Locale.forLanguageTag("tr-TR"))
}

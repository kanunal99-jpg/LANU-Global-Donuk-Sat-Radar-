package com.lanu.globaldonuksatisradari.export

import com.lanu.globaldonuksatisradari.crm.CrmCustomer
import com.lanu.globaldonuksatisradari.crm.CrmRegistryStatus
import com.lanu.globaldonuksatisradari.crm.CrmStage
import com.lanu.globaldonuksatisradari.crm.DataQuality
import com.lanu.globaldonuksatisradari.data.BusinessCategoryLabels
import com.lanu.globaldonuksatisradari.data.OfficialRegistryMatcher
import com.lanu.globaldonuksatisradari.data.OfficialRegistryRecord
import com.lanu.globaldonuksatisradari.data.OfficialRegistrySource
import com.lanu.globaldonuksatisradari.data.VerifiedBusiness
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

class BusinessExcelOfficialIndex private constructor(
    private val records: List<OfficialRegistryRecord>,
) {
    private val bySource = records.groupBy { it.source }
    private val byIdentity = buildMap<String, MutableList<OfficialRegistryRecord>> {
        records.forEach { record ->
            listOf(record.registrationNumber, record.mersisNumber)
                .mapNotNull { it?.trim()?.takeIf(String::isNotBlank) }
                .map(::identityKey)
                .distinct()
                .forEach { key -> getOrPut(key) { mutableListOf() }.add(record) }
        }
    }
    private val byTax = buildMap<String, MutableList<OfficialRegistryRecord>> {
        records.forEach { record ->
            record.taxOrNationalId
                ?.filter(Char::isDigit)
                ?.takeIf(String::isNotBlank)
                ?.let { key -> getOrPut(key) { mutableListOf() }.add(record) }
        }
    }
    private val bySourceCityDistrict = records.groupBy { record ->
        Triple(
            record.source,
            locationKey(record.city),
            locationKey(record.district),
        )
    }
    private val bySourceCity = records.groupBy { record ->
        record.source to locationKey(record.city)
    }

    fun exact(customer: CrmCustomer, source: OfficialRegistrySource): List<OfficialRegistryRecord> {
        val candidates = linkedSetOf<OfficialRegistryRecord>()
        customer.registryNumber
            ?.trim()
            ?.takeIf(String::isNotBlank)
            ?.let(::identityKey)
            ?.let { key -> byIdentity[key].orEmpty().filterTo(candidates) { it.source == source } }
        customer.taxOrNationalId
            ?.filter(Char::isDigit)
            ?.takeIf(String::isNotBlank)
            ?.let { key -> byTax[key].orEmpty().filterTo(candidates) { it.source == source } }
        return candidates.toList()
    }

    fun candidates(customer: CrmCustomer, source: OfficialRegistrySource): List<OfficialRegistryRecord> {
        val cityKey = locationKey(customer.city)
        val districtKey = locationKey(customer.district)
        if (cityKey.isNotBlank()) {
            if (districtKey.isNotBlank()) {
                val scoped = (
                    bySourceCityDistrict[Triple(source, cityKey, districtKey)].orEmpty() +
                        bySourceCityDistrict[Triple(source, cityKey, "")].orEmpty()
                    ).distinct()
                if (scoped.isNotEmpty()) return scoped
            }
            val cityScoped = bySourceCity[source to cityKey].orEmpty()
            if (cityScoped.isNotEmpty()) return cityScoped
        }
        return bySource[source].orEmpty()
    }

    companion object {
        val EMPTY = BusinessExcelOfficialIndex(emptyList())

        fun from(records: List<OfficialRegistryRecord>): BusinessExcelOfficialIndex =
            if (records.isEmpty()) EMPTY else BusinessExcelOfficialIndex(records)

        private fun identityKey(value: String): String =
            value.trim().uppercase(Locale.ROOT)

        private fun locationKey(value: String?): String =
            value.orEmpty()
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
    }
}

class BusinessExcelContextIndex private constructor(
    private val customers: List<CrmCustomer>,
    private val businesses: List<VerifiedBusiness>,
) {
    private val customersByIdentity = buildMap<String, MutableList<CrmCustomer>> {
        customers.forEach { customer ->
            listOf(customer.registryNumber, customer.taxOrNationalId)
                .mapNotNull { it?.trim()?.takeIf(String::isNotBlank) }
                .map(::identityKey)
                .distinct()
                .forEach { key -> getOrPut(key) { mutableListOf() }.add(customer) }
        }
    }
    private val businessesByIdentity = buildMap<String, MutableList<VerifiedBusiness>> {
        businesses.forEach { business ->
            val evidence = business.officialRegistryEvidence
            listOf(
                evidence?.registrationNumber,
                evidence?.mersisNumber,
                evidence?.taxOrNationalId,
            )
                .mapNotNull { it?.trim()?.takeIf(String::isNotBlank) }
                .map(::identityKey)
                .distinct()
                .forEach { key -> getOrPut(key) { mutableListOf() }.add(business) }
        }
    }
    private val customersByRegion = customers.groupBy {
        locationKey(it.city) to locationKey(it.district)
    }
    private val businessesByRegion = businesses.groupBy {
        locationKey(it.city) to locationKey(it.district)
    }

    fun customerFor(record: OfficialRegistryRecord): CrmCustomer? {
        recordIdentityKeys(record).forEach { key ->
            customersByIdentity[key]?.firstOrNull()?.let { return it }
        }
        val regional = customersByRegion[locationKey(record.city) to locationKey(record.district)].orEmpty()
        return regional.firstOrNull { customer ->
            OfficialRegistryMatcher.bestMatch(
                name = customer.businessName,
                city = customer.city,
                district = customer.district,
                address = customer.address,
                phone = customer.phone,
                records = listOf(record),
            ) != null
        }
    }

    fun businessFor(record: OfficialRegistryRecord): VerifiedBusiness? {
        recordIdentityKeys(record).forEach { key ->
            businessesByIdentity[key]?.firstOrNull()?.let { return it }
        }
        val regional = businessesByRegion[locationKey(record.city) to locationKey(record.district)].orEmpty()
        return regional.firstOrNull { business ->
            OfficialRegistryMatcher.bestMatch(
                name = business.name,
                city = business.city,
                district = business.district,
                address = business.address,
                phone = business.phone,
                records = listOf(record),
            ) != null
        }
    }

    companion object {
        val EMPTY = BusinessExcelContextIndex(emptyList(), emptyList())

        fun from(
            customers: List<CrmCustomer>,
            businesses: List<VerifiedBusiness>,
        ): BusinessExcelContextIndex =
            if (customers.isEmpty() && businesses.isEmpty()) {
                EMPTY
            } else {
                BusinessExcelContextIndex(customers, businesses)
            }

        private fun recordIdentityKeys(record: OfficialRegistryRecord): List<String> =
            listOf(
                record.registrationNumber,
                record.mersisNumber,
                record.taxOrNationalId,
            )
                .mapNotNull { it?.trim()?.takeIf(String::isNotBlank) }
                .map(::identityKey)
                .distinct()

        private fun identityKey(value: String): String =
            value.filter(Char::isLetterOrDigit).uppercase(Locale.ROOT)

        private fun locationKey(value: String?): String =
            value.orEmpty()
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
    }
}

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
    ): List<String> = customerValues(
        customer = customer,
        officialIndex = BusinessExcelOfficialIndex.from(officialRecords),
    )

    fun customerValues(
        customer: CrmCustomer,
        officialIndex: BusinessExcelOfficialIndex,
    ): List<String> {
        val official = BusinessExcelOfficialResolver.resolve(customer, officialIndex)
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
            sourceOriginLabel(customer.businessSourceId),
            dataQualityLabel(customer.dataQuality),
        )
    }

    fun registryValues(
        record: OfficialRegistryRecord,
        customer: CrmCustomer? = null,
        business: VerifiedBusiness? = null,
    ): List<String> {
        val contextualCustomer = customer ?: business?.toExportCustomer()
        val values = if (contextualCustomer != null) {
            customerValues(contextualCustomer, listOf(record)).toMutableList()
        } else {
            MutableList(commonHeaders.size) { "" }
        }

        fun set(header: String, value: String?) {
            val index = commonHeaders.indexOf(header)
            if (index >= 0 && !value.isNullOrBlank()) values[index] = value
        }

        if (customer == null && business != null) {
            set("Ad Soyad", "")
            set("CRM Aşaması", "")
            set("Tabela Adı", business.name)
            set("Veri Kalitesi", "Radar / açık kaynak")
        }

        set("Nokta Adı", customer?.businessName ?: business?.name ?: record.businessName)
        set("Ticari Unvan", record.businessName)
        if (values[commonHeaders.indexOf("İşletme Türü")].isBlank()) {
            set("İşletme Türü", record.naceCode?.let { "NACE $it" })
        }
        if (customer == null || customer.registryStatus == CrmRegistryStatus.UNVERIFIED) {
            set("Güncel Durum", officialStatusLabel(record))
        }
        set("Sicil Kaynağı", sourceLabel(record.source))
        set("Sicil Müdürlüğü", record.registryOffice)
        set("Sicil No", record.registrationNumber)
        set("MERSİS No", record.mersisNumber)
        set("Sicil Olayı", record.registryEvent)
        set("Yayın Tarihi", record.publicationDate)
        set("Tescil Tarihi", record.registrationDate)
        set("Gazete Sayı", record.gazetteNumber)
        set("Gazete Sayfa", record.gazettePage)
        set("Kaynak Referansı", record.sourceReference)
        set("NACE", record.naceCode)
        if (values[commonHeaders.indexOf("TC/Vergi No")].isBlank()) {
            set("TC/Vergi No", record.taxOrNationalId)
        }
        if (values[commonHeaders.indexOf("Telefon No")].isBlank()) {
            set("Telefon No", record.phone)
        }
        if (values[commonHeaders.indexOf("Web Sitesi")].isBlank()) {
            set("Web Sitesi", record.website)
        }
        if (values[commonHeaders.indexOf("İl")].isBlank()) set("İl", record.city)
        if (values[commonHeaders.indexOf("İlçe")].isBlank()) set("İlçe", record.district)
        if (values[commonHeaders.indexOf("Mahalle")].isBlank()) set("Mahalle", record.neighborhood)

        if (values[commonHeaders.indexOf("Açık Adres")].isBlank() &&
            record.source != OfficialRegistrySource.TTSG
        ) {
            set("Açık Adres", record.address)
        }
        set("Sicil/İlan Adresi", record.address)

        if (values[commonHeaders.indexOf("Kayıt Kaynağı")].isBlank()) {
            set("Kayıt Kaynağı", "Resmî Sicil")
        }
        if (values[commonHeaders.indexOf("Veri Kalitesi")].isBlank()) {
            set(
                "Veri Kalitesi",
                if (!record.registrationNumber.isNullOrBlank() || !record.mersisNumber.isNullOrBlank()) {
                    "Resmî Kimlik: Doğrulandı"
                } else {
                    "Resmî Kimlik: Sicil/MERSİS No Yok"
                },
            )
        }
        return values
    }

    private fun VerifiedBusiness.toExportCustomer(): CrmCustomer {
        val evidence = officialRegistryEvidence
        val registryStatus = when {
            evidence?.explicitlyActive == true -> CrmRegistryStatus.ACTIVE
            evidence?.explicitlyInactive == true -> CrmRegistryStatus.INACTIVE
            else -> CrmRegistryStatus.UNVERIFIED
        }
        return CrmCustomer(
            id = id,
            businessSourceId = source.id + ":" + id,
            businessName = name,
            signboardName = name,
            city = city,
            district = district,
            neighborhood = neighborhood,
            address = address,
            latitude = latitude,
            longitude = longitude,
            dataQuality = DataQuality.OBSERVED,
            businessType = category,
            taxOrNationalId = evidence?.taxOrNationalId,
            phone = phone,
            website = website,
            registryStatus = registryStatus,
            registrySource = evidence?.source?.name,
            registryNumber = evidence?.registrationNumber ?: evidence?.mersisNumber,
            createdAtEpochMs = verifiedAtEpochMs,
            updatedAtEpochMs = verifiedAtEpochMs,
        )
    }

    fun officialStatusLabel(record: OfficialRegistryRecord): String {
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

    private fun sourceOriginLabel(sourceId: String): String = when {
        sourceId.startsWith("manual:", ignoreCase = true) -> "Manuel ($sourceId)"
        sourceId.isBlank() -> ""
        else -> "Radar ($sourceId)"
    }

    private fun dataQualityLabel(quality: DataQuality): String = when (quality) {
        DataQuality.OBSERVED -> "Gözlemlendi"
        DataQuality.ESTIMATED -> "Tahmini"
        DataQuality.USER_ENTERED -> "Kullanıcı Girişi"
        DataQuality.UNKNOWN -> "Belirtilmedi"
    }
}

object BusinessExcelOfficialResolver {
    fun resolve(
        customer: CrmCustomer,
        records: List<OfficialRegistryRecord>,
    ): BusinessExcelOfficialSnapshot =
        resolve(customer, BusinessExcelOfficialIndex.from(records))

    fun resolve(
        customer: CrmCustomer,
        index: BusinessExcelOfficialIndex,
    ): BusinessExcelOfficialSnapshot {
        if (index === BusinessExcelOfficialIndex.EMPTY) {
            return BusinessExcelOfficialSnapshot(
                status = statusFromCustomer(customer),
                sources = customer.registrySource,
                registrationNumber = customer.registryNumber,
                taxOrNationalId = customer.taxOrNationalId,
            )
        }

        val matched = OfficialRegistrySource.entries.mapNotNull { source ->
            val exactRecords = index.exact(customer, source)
            val sourceRecords = index.candidates(customer, source)
            if (sourceRecords.isEmpty()) return@mapNotNull null
            exactMatch(exactRecords, source)
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
                ?: currentRegistry?.let(BusinessExcelSchema::officialStatusLabel),
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
        exactRecords: List<OfficialRegistryRecord>,
        source: OfficialRegistrySource,
    ): OfficialRegistryRecord? {
        if (exactRecords.isEmpty()) return null
        return if (source == OfficialRegistrySource.TTSG) {
            exactRecords.maxByOrNull(::publicationSortKey)
        } else {
            exactRecords.maxByOrNull(OfficialRegistryRecord::importedAtEpochMs)
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

    private fun statusFromCustomer(customer: CrmCustomer): String = when (customer.registryStatus) {
        CrmRegistryStatus.ACTIVE -> "AKTİF"
        CrmRegistryStatus.INACTIVE -> "PASİF"
        CrmRegistryStatus.UNVERIFIED -> "DOĞRULANMADI"
    }

    private val DATE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.uuuu", Locale.forLanguageTag("tr-TR"))
}

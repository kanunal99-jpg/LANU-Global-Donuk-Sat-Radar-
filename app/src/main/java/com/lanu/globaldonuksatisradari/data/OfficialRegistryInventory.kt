package com.lanu.globaldonuksatisradari.data

object OfficialRegistryInventory {
    fun mergeIntoInventory(
        discoveredBusinesses: List<VerifiedBusiness>,
        records: List<OfficialRegistryRecord>,
        city: String,
        district: String?,
        neighborhood: String?,
    ): List<VerifiedBusiness> {
        val verifiedRecords = OfficialRegistryTrust.verified(records)
            .filterNot { it.status?.let(OfficialRegistryStatus::isInactive) == true }

        val enriched = OfficialRegistryEnricher.enrich(
            businesses = discoveredBusinesses,
            records = verifiedRecords,
        )
        if (verifiedRecords.isEmpty()) return enriched

        val existingPhones = enriched
            .flatMap { OfficialRegistryNormalizer.phones(it.phone) }
            .toSet()
        val existingNameDistrictKeys = enriched
            .map { business ->
                normalizedNameDistrict(
                    name = business.name,
                    district = business.district,
                )
            }
            .toSet()

        val standalone = verifiedRecords
            .asSequence()
            .filter { record ->
                recordMatchesScope(
                    record = record,
                    city = city,
                    district = district,
                    neighborhood = neighborhood,
                )
            }
            .filterNot { record ->
                val recordPhones = OfficialRegistryNormalizer.phones(record.phone)
                val phoneMatch = recordPhones.isNotEmpty() &&
                    recordPhones.any(existingPhones::contains)
                val nameDistrictMatch = normalizedNameDistrict(
                    name = record.businessName,
                    district = record.district.orEmpty(),
                ) in existingNameDistrictKeys
                phoneMatch || nameDistrictMatch
            }
            .map(::toVerifiedBusiness)
            .toList()

        return BusinessDeduplication.deduplicateCrossSource(enriched + standalone)
    }

    private fun toVerifiedBusiness(record: OfficialRegistryRecord): VerifiedBusiness {
        val registration = record.registrationNumber.orEmpty().trim()
        val fieldsUsed = buildSet {
            if (!record.address.isNullOrBlank()) add("address")
            if (!record.phone.isNullOrBlank()) add("phone")
            if (!record.website.isNullOrBlank()) add("website")
            if (!record.district.isNullOrBlank()) add("district")
            if (!record.neighborhood.isNullOrBlank()) add("neighborhood")
        }
        return VerifiedBusiness(
            id = "registry:${record.source.name.lowercase()}:$registration",
            name = record.businessName,
            city = record.city.orEmpty(),
            district = record.district?.takeIf(String::isNotBlank) ?: "Bilinmiyor",
            neighborhood = record.neighborhood,
            source = record.source.descriptor,
            verifiedAtEpochMs = record.importedAtEpochMs,
            latitude = null,
            longitude = null,
            category = record.naceCode?.takeIf(String::isNotBlank)?.let { "NACE $it" }
                ?: "Resmî Sicil",
            address = record.address,
            phone = record.phone,
            website = record.website,
            officialRegistryEvidence = OfficialRegistryEvidence(
                source = record.source.descriptor,
                registrationNumber = record.registrationNumber,
                status = record.status,
                importedAtEpochMs = record.importedAtEpochMs,
                fieldsUsed = fieldsUsed,
            ),
        )
    }

    private fun recordMatchesScope(
        record: OfficialRegistryRecord,
        city: String,
        district: String?,
        neighborhood: String?,
    ): Boolean {
        val recordCity = OfficialRegistryNormalizer.text(record.city.orEmpty())
        if (recordCity.isEmpty() || recordCity != OfficialRegistryNormalizer.text(city)) {
            return false
        }

        val selectedDistrict = district
            ?.takeUnless { it.isBlank() || it.equals("Tümü", ignoreCase = true) }
            ?.let(OfficialRegistryNormalizer::text)
        if (selectedDistrict != null) {
            val recordDistrict = OfficialRegistryNormalizer.text(record.district.orEmpty())
            if (recordDistrict.isEmpty() || recordDistrict != selectedDistrict) {
                return false
            }
        }

        val selectedNeighborhood = neighborhood
            ?.takeUnless { it.isBlank() || it.equals("Tümü", ignoreCase = true) }
            ?.let(::normalizeNeighborhood)
        if (selectedNeighborhood != null) {
            val recordNeighborhood = normalizeNeighborhood(record.neighborhood.orEmpty())
            val address = OfficialRegistryNormalizer.text(record.address.orEmpty())
            val matchesNeighborhood = recordNeighborhood == selectedNeighborhood ||
                (recordNeighborhood.isEmpty() && address.contains(selectedNeighborhood))
            if (!matchesNeighborhood) return false
        }

        return true
    }

    private fun normalizedNameDistrict(name: String, district: String): String =
        OfficialRegistryNormalizer.text(name) + "|" +
            OfficialRegistryNormalizer.text(district)

    private fun normalizeNeighborhood(value: String): String =
        OfficialRegistryNormalizer.text(value)
            .removeSuffix(" mahallesi")
            .removeSuffix(" mah")
            .trim()
}

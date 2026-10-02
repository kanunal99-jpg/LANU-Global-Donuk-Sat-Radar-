package com.lanu.globaldonuksatisradari.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OfficialRegistryInventoryTest {
    private fun business(
        name: String = "Harita Market",
        phone: String? = null,
        district: String = "Sultanbeyli",
        neighborhood: String? = "Mimar Sinan",
    ) = VerifiedBusiness(
        id = "node:1",
        name = name,
        city = "İstanbul",
        district = district,
        neighborhood = neighborhood,
        source = OverpassBusinessSource.descriptor,
        verifiedAtEpochMs = 100L,
        latitude = 40.99,
        longitude = 29.26,
        category = "supermarket",
        address = "Özgürlük Caddesi",
        phone = phone,
    )

    private fun record(
        name: String,
        registrationNumber: String? = "123",
        status: String? = "Faal",
        district: String? = "Sultanbeyli",
        neighborhood: String? = "Mimar Sinan Mahallesi",
        phone: String? = null,
        address: String? = "Özgürlük Caddesi No:76/A",
    ) = OfficialRegistryRecord(
        source = OfficialRegistrySource.CHAMBER,
        registrationNumber = registrationNumber,
        businessName = name,
        status = status,
        city = "İstanbul",
        district = district,
        neighborhood = neighborhood,
        address = address,
        phone = phone,
        website = null,
        importedAtEpochMs = 200L,
        naceCode = "56.10.19",
    )

    @Test
    fun unmatchedVerifiedRegistryBusinessIsAddedEvenWithoutOsmCoordinates() {
        val merged = OfficialRegistryInventory.mergeIntoInventory(
            discoveredBusinesses = listOf(business()),
            records = listOf(record(name = "Sandora Fast Food & Cafe", phone = "0537 519 74 53")),
            city = "İstanbul",
            district = "Sultanbeyli",
            neighborhood = null,
        )

        val sandora = merged.single { it.name == "Sandora Fast Food & Cafe" }
        assertEquals("0537 519 74 53", sandora.phone)
        assertEquals("Özgürlük Caddesi No:76/A", sandora.address)
        assertEquals("NACE 56.10.19", sandora.category)
        assertNull(sandora.latitude)
        assertNull(sandora.longitude)
        assertTrue(sandora.officialRegistryEvidence != null)
    }

    @Test
    fun existingOsmBusinessIsEnrichedInsteadOfDuplicated() {
        val merged = OfficialRegistryInventory.mergeIntoInventory(
            discoveredBusinesses = listOf(
                business(
                    name = "Sandora Fast Food & Cafe",
                    phone = null,
                ),
            ),
            records = listOf(
                record(
                    name = "Sandora Fast Food & Cafe",
                    phone = "0537 519 74 53",
                ),
            ),
            city = "İstanbul",
            district = "Sultanbeyli",
            neighborhood = null,
        )

        assertEquals(1, merged.size)
        assertEquals("0537 519 74 53", merged.single().phone)
        assertEquals(40.99, merged.single().latitude)
        assertEquals(29.26, merged.single().longitude)
    }

    @Test
    fun unverifiedOrInactiveRegistryRecordsAreNotAddedAsStandaloneBusinesses() {
        val merged = OfficialRegistryInventory.mergeIntoInventory(
            discoveredBusinesses = emptyList(),
            records = listOf(
                record(name = "Sicilsiz", registrationNumber = null),
                record(name = "Kapanmış", registrationNumber = "999", status = "Terkin"),
            ),
            city = "İstanbul",
            district = "Sultanbeyli",
            neighborhood = null,
        )

        assertTrue(merged.isEmpty())
    }

    @Test
    fun selectedNeighborhoodDoesNotLeakRegistryRecordsFromOtherNeighborhoods() {
        val merged = OfficialRegistryInventory.mergeIntoInventory(
            discoveredBusinesses = emptyList(),
            records = listOf(
                record(name = "Mimar Nokta", registrationNumber = "1", neighborhood = "Mimar Sinan Mahallesi"),
                record(name = "Hasanpaşa Nokta", registrationNumber = "2", neighborhood = "Hasanpaşa Mahallesi"),
            ),
            city = "İstanbul",
            district = "Sultanbeyli",
            neighborhood = "Mimar Sinan",
        )

        assertEquals(listOf("Mimar Nokta"), merged.map { it.name })
    }

    @Test
    fun districtScopedStandaloneInventoryRequiresExplicitDistrictMatch() {
        val merged = OfficialRegistryInventory.mergeIntoInventory(
            discoveredBusinesses = emptyList(),
            records = listOf(
                record(name = "Doğru İlçe", registrationNumber = "1", district = "Sultanbeyli"),
                record(name = "Belirsiz İlçe", registrationNumber = "2", district = null),
                record(name = "Yanlış İlçe", registrationNumber = "3", district = "Pendik"),
            ),
            city = "İstanbul",
            district = "Sultanbeyli",
            neighborhood = null,
        )

        assertEquals(listOf("Doğru İlçe"), merged.map { it.name })
    }
    @Test
    fun explicitOtherNeighborhoodRecordCannotEnrichSelectedNeighborhoodBusiness() {
        val original = business(
            name = "Aynı İsim Market",
            phone = null,
            neighborhood = "Mimar Sinan",
        )
        val merged = OfficialRegistryInventory.mergeIntoInventory(
            discoveredBusinesses = listOf(original),
            records = listOf(
                record(
                    name = "Aynı İsim Market",
                    registrationNumber = "88",
                    neighborhood = "Hasanpaşa Mahallesi",
                    phone = "0555 999 88 77",
                ),
            ),
            city = "İstanbul",
            district = "Sultanbeyli",
            neighborhood = "Mimar Sinan",
        )

        assertEquals(1, merged.size)
        assertNull(merged.single().phone)
        assertNull(merged.single().officialRegistryEvidence)
    }

}

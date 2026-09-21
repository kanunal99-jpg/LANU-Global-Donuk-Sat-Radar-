package com.lanu.globaldonuksatisradari.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BusinessProvenanceTest {
    private val source = DataSourceDescriptor(
        id = "osm-overpass",
        name = "OpenStreetMap Overpass",
        publisher = "OpenStreetMap / FOSS GIS",
        licenseOrTerms = "ODbL",
        sourceUrl = "https://overpass-api.de/api/interpreter",
        lastVerifiedAtEpochMs = 1L,
    )

    private fun business(evidence: List<BusinessFieldEvidence> = emptyList()) = VerifiedBusiness(
        id = "node:1", name = "ABC", city = "İstanbul", district = "Kadıköy", neighborhood = null,
        source = source, verifiedAtEpochMs = 2L, address = "Test adres", phone = "555",
        category = "restaurant", fieldEvidence = evidence,
    )

    @Test
    fun effectiveEvidenceIsGeneratedFromPopulatedVerifiedFields() {
        val result = business().effectiveEvidence()
        assertTrue(result.any { it.field == "name" && it.source.id == "osm-overpass" })
        assertTrue(result.any { it.field == "address" })
        assertTrue(result.any { it.field == "phone" })
    }

    @Test
    fun explicitEvidenceIsPreservedWithoutInventingRegistryEvidence() {
        val result = business(listOf(BusinessFieldEvidence("name", source, 3L))).effectiveEvidence()
        assertEquals(1, result.size)
        assertEquals("name", result.single().field)
    }

    @Test
    fun qualityReportsEvidenceCoverageSeparatelyFromCompleteness() {
        val result = BusinessQualityEvaluator.evaluate(
            business(listOf(
                BusinessFieldEvidence("name", source, 3L),
                BusinessFieldEvidence("city", source, 3L),
                BusinessFieldEvidence("district", source, 3L),
                BusinessFieldEvidence("address", source, 3L),
                BusinessFieldEvidence("phone", source, 3L),
                BusinessFieldEvidence("category", source, 3L),
            )),
        )
        assertTrue(result.completenessPercent > 0)
        assertEquals(100, result.evidenceCoveragePercent)
    }

    @Test
    fun registryFieldsRemainEmptyUntilVerifiedRegistrySourceSuppliesThem() {
        val result = BusinessQualityEvaluator.evaluate(business())
        assertTrue(result.missingFields.contains("NACE"))
        assertTrue(result.missingFields.contains("Hukuki unvan"))
        assertTrue(result.missingFields.contains("Ticaret sicil no"))
    }
}
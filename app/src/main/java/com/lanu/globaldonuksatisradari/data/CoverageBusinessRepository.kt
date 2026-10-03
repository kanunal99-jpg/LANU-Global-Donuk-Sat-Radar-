package com.lanu.globaldonuksatisradari.data

import android.content.Context
import android.util.Log
import com.lanu.globaldonuksatisradari.IstanbulDistricts

internal fun planCoverageScopes(
    city: String,
    selectedDistrict: String?,
    selectedNeighborhood: String? = null,
    query: String,
    discoveredDistricts: List<String>,
): List<CoverageScope> {
    val category = query.ifBlank { "*" }
    if (selectedDistrict != null) {
        return listOf(
            CoverageScope(
                city = city,
                district = selectedDistrict,
                neighborhood = selectedNeighborhood,
                category = category,
            ),
        )
    }

    val canonicalDistricts = discoveredDistricts
        .filter { it.isNotBlank() && !it.equals("Tümü", ignoreCase = true) }
        .distinctBy(BusinessDeduplication::normalizeForComparison)

    return if (canonicalDistricts.isNotEmpty()) {
        canonicalDistricts.map { districtName ->
            CoverageScope(
                city = city,
                district = districtName,
                category = category,
            )
        }
    } else {
        listOf(
            CoverageScope(
                city = city,
                district = "Tümü",
                category = category,
            ),
        )
    }
}

internal fun nominatimFallbackQuery(category: String): String = when (
    category.trim().lowercase()
) {
    "education" -> "school"
    "automotive" -> "car repair"
    "beauty" -> "beauty salon"
    "finance" -> "bank"
    "construction" -> "construction company"
    "agriculture" -> "farm"
    "logistics" -> "logistics"
    "manufacturer" -> "factory"
    "commercial" -> "business"
    else -> category
}

/**
 * Production wiring: Overture monthly Turkey snapshot -> OSM Overpass -> Nominatim ->
 * local cache -> safe empty. Broad scans merge successful bulk sources; targeted scans
 * stop at the first source that returns data.
 */
class CoverageBusinessRepository(
    context: Context,
    private val localCache: CoverageLocalCache = SharedPreferencesCoverageLocalCache(context),
) : BusinessRepository {

    private val overture = OvertureBusinessSourceAdapter(context)
    private val supplemental = SupplementalBusinessDirectory(context)
    private val overpass = OverpassBusinessSourceAdapter()
    private val nominatim = NominatimBusinessSourceAdapter()
    private val districtCatalog = DistrictCatalogRepository(context)
    private val officialRegistryStore = OfficialRegistryStore(context)

    private val engine = BusinessCoverageEngine(
        sources = listOf(
            CoverageSource { scope ->
                val businesses = overpass.fetchValidated(
                    query = scope.category.takeUnless { it == "*" }.orEmpty(),
                    city = scope.city,
                    district = scope.district.takeUnless { it == "Tümü" },
                    neighborhood = scope.neighborhood,
                )
                Result.success(
                    CoverageSourceResult(
                        source = overpass.contract.descriptor,
                        businesses = businesses,
                        completedAtEpochMs = System.currentTimeMillis(),
                    ),
                )
            },
            CoverageSource { scope ->
                val businesses = nominatim.fetchValidated(
                    query = scope.category.takeUnless { it == "*" }
                        ?.let(::nominatimFallbackQuery)
                        .orEmpty(),
                    city = scope.city,
                    district = scope.district.takeUnless { it == "Tümü" },
                    neighborhood = scope.neighborhood,
                )
                Result.success(
                    CoverageSourceResult(
                        source = nominatim.contract.descriptor,
                        businesses = businesses,
                        completedAtEpochMs = System.currentTimeMillis(),
                    ),
                )
            },
        ),
        nowEpochMs = { System.currentTimeMillis() },
    )

    override suspend fun search(
        query: String,
        city: String,
        district: String?,
    ): List<VerifiedBusiness> = searchScoped(
        query = query,
        city = city,
        district = district,
        neighborhood = null,
    )

    suspend fun searchScoped(
        query: String,
        city: String,
        district: String?,
        neighborhood: String?,
    ): List<VerifiedBusiness> {
        val normalizedDistrict = district?.takeUnless { it.isBlank() || it.equals("Tümü", true) }
        val normalizedNeighborhood = neighborhood
            ?.takeUnless { it.isBlank() || it.equals("Tümü", true) }
            ?.takeIf { normalizedDistrict != null }

        val fallbackDistricts = if (city.equals("İstanbul", true)) {
            IstanbulDistricts.ALL
        } else {
            emptyList()
        }
        val discoveredDistricts = if (normalizedDistrict == null) {
            districtCatalog.getDistricts(city, fallbackDistricts)
        } else {
            emptyList()
        }
        val scopes = planCoverageScopes(
            city = city,
            selectedDistrict = normalizedDistrict,
            selectedNeighborhood = normalizedNeighborhood,
            query = query,
            discoveredDistricts = discoveredDistricts,
        )

        // Overture is a province snapshot. Read it once per user search instead of
        // once for every district scope; a city-wide İstanbul scan would otherwise
        // decompress the same file 39 times.
        val overtureRecords = runCatching {
            overture.fetchValidated(
                query = query,
                city = city,
                district = normalizedDistrict,
                neighborhood = normalizedNeighborhood,
            )
        }.onFailure { error ->
            Log.w(
                "LanuRadar",
                "Overture snapshot kullanılamadı; OSM kaynaklarına geçiliyor: " +
                    city + "/" + normalizedDistrict,
                error,
            )
        }.getOrDefault(emptyList())

        // Curated corrections fill documented blind spots in the bulk/open providers.
        // This layer is small, source-attributed and validation-gated; it is not a
        // replacement for Overture/OSM or official registry data.
        val supplementalRecords = supplemental.search(
            query = query,
            city = city,
            district = normalizedDistrict,
            neighborhood = normalizedNeighborhood,
        )

        val scans = engine.scanAll(scopes)
        var discovered = BusinessDeduplication.deduplicateCrossSource(
            supplementalRecords + overtureRecords + CoverageResultMerger.merge(
                scans = scans,
                localCache = localCache,
                nowEpochMs = System.currentTimeMillis(),
            ),
        )

        // Some OSM neighborhoods have no Overpass area counterpart. In that case,
        // make one bounded district fallback request and keep only records whose
        // explicit OSM neighborhood/quarter/suburb tag matches the selected mahalle.
        if (normalizedNeighborhood != null && normalizedDistrict != null && discovered.isEmpty()) {
            val districtFallback = runCatching {
                overpass.fetchValidated(
                    query = query,
                    city = city,
                    district = normalizedDistrict,
                    neighborhood = null,
                )
            }.getOrDefault(emptyList())

            val wanted = normalizeNeighborhoodForComparison(normalizedNeighborhood)
            discovered = districtFallback.filter { business ->
                normalizeNeighborhoodForComparison(business.neighborhood.orEmpty()) == wanted
            }
        }

        if (query.isBlank()) {
            discovered = discovered.filter(BusinessEntityEligibility::keepForBusinessInventory)
        }

        val registryRecords = officialRegistryStore.recordsFor(city, normalizedDistrict)
        return if (query.isBlank()) {
            OfficialRegistryDiscovery.mergeIntoBroadInventory(
                discovered = discovered,
                records = registryRecords,
                selectedCity = city,
                selectedDistrict = normalizedDistrict,
            )
        } else {
            OfficialRegistryEnricher.enrich(
                businesses = discovered,
                records = registryRecords,
            )
        }
    }

    private fun normalizeNeighborhoodForComparison(value: String): String =
        BusinessDeduplication.normalizeForComparison(value)
            .removeSuffix(" mahallesi")
            .removeSuffix(" mah")
            .trim()

}
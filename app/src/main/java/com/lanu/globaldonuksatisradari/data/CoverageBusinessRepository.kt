package com.lanu.globaldonuksatisradari.data

import android.content.Context
import com.lanu.globaldonuksatisradari.IstanbulDistricts

internal fun planCoverageScopes(
    city: String,
    selectedDistrict: String?,
    query: String,
    discoveredDistricts: List<String>,
): List<CoverageScope> {
    val category = query.ifBlank { "*" }
    if (selectedDistrict != null) {
        return listOf(
            CoverageScope(
                city = city,
                district = selectedDistrict,
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

/**
 * Production wiring: Coverage Engine -> real OSM adapters -> local cache -> safe empty.
 * Successful empty responses remain authoritative; local cache is used only after source failures.
 */
class CoverageBusinessRepository(
    context: Context,
    private val localCache: CoverageLocalCache = SharedPreferencesCoverageLocalCache(context),
) : BusinessRepository {

    private val overpass = OverpassBusinessSourceAdapter()
    private val nominatim = NominatimBusinessSourceAdapter()
    private val districtCatalog = DistrictCatalogRepository(context)

    private val engine = BusinessCoverageEngine(
        sources = listOf(
            CoverageSource { scope ->
                val businesses = overpass.fetchValidated(
                    query = scope.category.takeUnless { it == "*" }.orEmpty(),
                    city = scope.city,
                    district = scope.district.takeUnless { it == "Tümü" },
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
                    query = scope.category.takeUnless { it == "*" }.orEmpty(),
                    city = scope.city,
                    district = scope.district.takeUnless { it == "Tümü" },
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
    ): List<VerifiedBusiness> {
        val normalizedDistrict = district?.takeUnless { it.isBlank() || it.equals("Tümü", true) }

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
            query = query,
            discoveredDistricts = discoveredDistricts,
        )

        val scans = engine.scanAll(scopes)
        return CoverageResultMerger.merge(
            scans = scans,
            localCache = localCache,
            nowEpochMs = System.currentTimeMillis(),
        )
    }

}
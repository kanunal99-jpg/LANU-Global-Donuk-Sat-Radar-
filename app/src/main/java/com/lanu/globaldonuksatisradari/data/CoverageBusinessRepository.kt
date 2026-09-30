package com.lanu.globaldonuksatisradari.data

import android.content.Context
import com.lanu.globaldonuksatisradari.IstanbulDistricts

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
        return searchDistricts(query, city, normalizedDistrict?.let(::listOf))
    }

    suspend fun searchDistricts(
        query: String,
        city: String,
        districts: List<String>?,
    ): List<VerifiedBusiness> {
        val normalizedDistricts = districts
            ?.map(String::trim)
            ?.filter(String::isNotEmpty)
            ?.distinct()

        val scopes = coverageScopes(query, city, normalizedDistricts)

        val scans = engine.scanAll(scopes)
        return CoverageResultMerger.merge(
            scans = scans,
            localCache = localCache,
            nowEpochMs = System.currentTimeMillis(),
        )
    }

    internal companion object {
        fun coverageScopes(query: String, city: String, normalizedDistricts: List<String>?): List<CoverageScope> = if (city.equals("İstanbul", true)) {
            (normalizedDistricts ?: IstanbulDistricts.ALL).map { districtName ->
                CoverageScope(
                    city = city,
                    district = districtName,
                    category = query.ifBlank { "*" },
                )
            }
        } else {
            listOf(
                CoverageScope(
                    city = city,
                    district = normalizedDistricts?.singleOrNull() ?: "Tümü",
                    category = query.ifBlank { "*" },
                )
            )
        }
    }
}

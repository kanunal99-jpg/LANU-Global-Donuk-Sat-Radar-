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
    private val neighborhoodScopedSource = NeighborhoodScopedOsmSource()

    private val engine = BusinessCoverageEngine(
        sources = listOf(
            CoverageSource { scope ->
                val query = scope.category.takeUnless { it == "*" }.orEmpty()
                val district = scope.district.takeUnless { it == "Tümü" }
                val businesses = if (scope.neighborhood.isNullOrBlank()) {
                    overpass.fetchValidated(
                        query = query,
                        city = scope.city,
                        district = district,
                    )
                } else {
                    neighborhoodScopedSource.fetchOverpass(
                        query = query,
                        city = scope.city,
                        district = requireNotNull(district) { "Mahalle kapsamı için ilçe gerekli" },
                        neighborhood = scope.neighborhood,
                    )
                }
                Result.success(
                    CoverageSourceResult(
                        source = overpass.contract.descriptor,
                        businesses = businesses,
                        completedAtEpochMs = System.currentTimeMillis(),
                    ),
                )
            },
            CoverageSource { scope ->
                val query = scope.category.takeUnless { it == "*" }.orEmpty()
                val district = scope.district.takeUnless { it == "Tümü" }
                val businesses = if (scope.neighborhood.isNullOrBlank()) {
                    nominatim.fetchValidated(
                        query = query,
                        city = scope.city,
                        district = district,
                    )
                } else {
                    neighborhoodScopedSource.fetchNominatim(
                        query = query,
                        city = scope.city,
                        district = requireNotNull(district) { "Mahalle kapsamı için ilçe gerekli" },
                        neighborhood = scope.neighborhood,
                    )
                }
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
    ): List<VerifiedBusiness> = searchScoped(query, city, district, null)

    suspend fun searchScoped(
        query: String,
        city: String,
        district: String?,
        neighborhood: String?,
    ): List<VerifiedBusiness> {
        val normalizedDistrict = district?.takeUnless { it.isBlank() || it.equals("Tümü", true) }
        val normalizedNeighborhood = neighborhood?.takeUnless { it.isBlank() || it.equals("Tümü", true) }
        require(normalizedNeighborhood == null || normalizedDistrict != null) {
            "Mahalle seçimi ilçe olmadan uygulanamaz"
        }

        val scopes = if (city.equals("İstanbul", true) && normalizedDistrict == null) {
            IstanbulDistricts.ALL.map { districtName ->
                CoverageScope(
                    city = city,
                    district = districtName,
                    neighborhood = null,
                    category = query.ifBlank { "*" },
                )
            }
        } else {
            listOf(
                CoverageScope(
                    city = city,
                    district = normalizedDistrict ?: "Tümü",
                    neighborhood = normalizedNeighborhood,
                    category = query.ifBlank { "*" },
                )
            )
        }

        val scans = engine.scanAll(scopes)
        val merged = CoverageResultMerger.merge(
            scans = scans,
            localCache = localCache,
            nowEpochMs = System.currentTimeMillis(),
        )
        return if (normalizedNeighborhood == null) {
            merged
        } else {
            merged.filter { it.neighborhood?.equals(normalizedNeighborhood, ignoreCase = true) == true }
        }
    }

}

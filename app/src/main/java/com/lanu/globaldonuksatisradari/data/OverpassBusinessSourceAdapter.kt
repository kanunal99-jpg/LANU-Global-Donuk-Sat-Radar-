package com.lanu.globaldonuksatisradari.data

import android.os.SystemClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

object OverpassBusinessSource {
    const val BASE_URL = "https://overpass-api.de/api/interpreter"
    val FALLBACK_URLS = listOf(
        "https://overpass.private.coffee/api/interpreter",
        "https://maps.mail.ru/osm/tools/overpass/api/interpreter",
    )
    private const val SOURCE_REVIEWED_AT = 1789646400000L

    val descriptor = DataSourceDescriptor(
        id = "osm-overpass",
        name = "OpenStreetMap Overpass",
        publisher = "OpenStreetMap / FOSSGISS",
        licenseOrTerms = "https://wiki.openstreetmap.org/wiki/Overpass_API ; https://www.openstreetmap.org/copyright",
        sourceUrl = "https://overpass-api.de/api/interpreter",
        lastVerifiedAtEpochMs = SOURCE_REVIEWED_AT,
    )

    val contract = BusinessSourceContract(
        descriptor = descriptor,
        accessMethod = SourceAccessMethod.PUBLIC_SEARCH,
        scope = "Bounded user-triggered commercial-business discovery across shop, office, craft, industrial, nightlife, hospitality and service tags; OSM/ODbL data; cached locally",
        permittedUseVerified = true,
        supportsBulk = false,
        fieldNames = setOf(
            "name", "city", "district", "neighborhood", "latitude", "longitude",
            "category", "address", "phone", "website", "opening_hours", "menu", "menu_url",
        ),
    )
}

object OverpassQueryBuilder {
    fun build(
        city: String,
        district: String?,
        query: String,
        neighborhood: String? = null,
    ): String {
        require(city.isNotBlank()) { "Şehir boş olamaz" }
        val scope = buildScope(city, district, neighborhood)
        val body = if (query.isBlank()) buildBroadQuery() else buildTermQuery(query.trim())

        return """
            [out:json][timeout:90][maxsize:33554432];
            $scope
            (
            $body
            );
            out center tags qt;
        """.trimIndent()
    }

    private fun buildScope(
        city: String,
        district: String?,
        neighborhood: String?,
    ): String {
        val escapedCity = escapeQuoted(city)
        val selectedDistrict = district?.takeUnless {
            it.isBlank() || it.equals("Tümü", ignoreCase = true)
        }
        val selectedNeighborhood = neighborhood?.takeUnless {
            it.isBlank() || it.equals("Tümü", ignoreCase = true)
        }

        if (selectedDistrict == null) {
            return """area["name"="$escapedCity"]["boundary"="administrative"]["admin_level"="4"]->.searchArea;"""
        }

        val escapedDistrict = escapeQuoted(selectedDistrict)
        if (selectedNeighborhood == null) {
            return """
                area["name"="$escapedCity"]["boundary"="administrative"]["admin_level"="4"]->.cityArea;
                relation(area.cityArea)["boundary"="administrative"]["admin_level"="6"]["name"="$escapedDistrict"]->.districtRelation;
                .districtRelation map_to_area->.searchArea;
            """.trimIndent()
        }

        val neighborhoodBase = selectedNeighborhood
            .replace(Regex("""\s+Mahallesi$""", RegexOption.IGNORE_CASE), "")
            .trim()
        val escapedNeighborhoodRegex = escapeRegex(neighborhoodBase)
        return """
            area["name"="$escapedCity"]["boundary"="administrative"]["admin_level"="4"]->.cityArea;
            relation(area.cityArea)["boundary"="administrative"]["admin_level"="6"]["name"="$escapedDistrict"]->.districtRelation;
            .districtRelation map_to_area->.districtArea;
            (
              relation(area.districtArea)["boundary"="administrative"]["name"~"^$escapedNeighborhoodRegex( Mahallesi)?$",i];
              way(area.districtArea)["boundary"="administrative"]["name"~"^$escapedNeighborhoodRegex( Mahallesi)?$",i];
            )->.neighborhoodBoundary;
            .neighborhoodBoundary map_to_area->.searchArea;
        """.trimIndent()
    }

    private fun buildBroadQuery(): String = listOf(
        // Every named shop type: market, mall, clothing, electronics, automotive, wholesale, etc.
        """nwr["name"]["shop"](area.searchArea);""",
        // Named offices/companies and professional services.
        """nwr["name"]["office"](area.searchArea);""",
        // Named crafts/workshops and small producers.
        """nwr["name"]["craft"](area.searchArea);""",
        // Manufacturing/industrial businesses.
        """nwr["name"]["industrial"](area.searchArea);""",
        """nwr["name"]["man_made"="works"](area.searchArea);""",
        // Hospitality, food, nightlife, finance, fuel, health and other commercial services.
        """nwr["name"]["amenity"~"restaurant|cafe|fast_food|food_court|bar|pub|biergarten|ice_cream|marketplace|catering|nightclub|bank|atm|pharmacy|clinic|doctors|dentist|veterinary|fuel|car_wash|car_rental|vehicle_inspection|cinema|casino|gambling|internet_cafe|coworking_space|conference_centre|events_venue|charging_station|bureau_de_change"](area.searchArea);""",
        // Commercial accommodation/tourism.
        """nwr["name"]["tourism"~"hotel|hostel|motel|guest_house|apartment|chalet|camp_site|caravan_site|resort"](area.searchArea);""",
        // Entertainment / recreation businesses.
        """nwr["name"]["leisure"~"adult_gaming_centre|amusement_arcade|bowling_alley|fitness_centre|sports_centre|dance|escape_game|water_park"](area.searchArea);""",
        // Named clubs (sports/social/nightlife); users can further filter after fetch.
        """nwr["name"]["club"](area.searchArea);""",
        // Healthcare-tagged businesses that are not necessarily represented by amenity/shop.
        """nwr["name"]["healthcare"](area.searchArea);""",
    ).joinToString("\n")

    private fun buildTermQuery(query: String): String {
        val normalized = normalize(query)
        val regex = escapeRegex(query)
        val categoryClauses = when {
            normalized in setOf("cafe", "kafe", "coffee", "kahve") ->
                listOf("""nwr["amenity"="cafe"](area.searchArea);""")
            normalized in setOf("restaurant", "restoran", "lokanta") ->
                listOf("""nwr["amenity"="restaurant"](area.searchArea);""")
            normalized in setOf("fast food", "fastfood") ->
                listOf("""nwr["amenity"="fast_food"](area.searchArea);""")
            normalized in setOf("catering", "catering sirketi", "catering şirketi") ->
                listOf(
                    """nwr["amenity"="catering"](area.searchArea);""",
                    """nwr["name"~"$regex",i](area.searchArea);""",
                )
            normalized in setOf("firin", "fırın", "bakery") ->
                listOf("""nwr["shop"="bakery"](area.searchArea);""")
            normalized in setOf("kasap", "butcher") ->
                listOf("""nwr["shop"="butcher"](area.searchArea);""")
            normalized in setOf("market", "supermarket", "süpermarket") ->
                listOf(
                    """nwr["shop"="supermarket"](area.searchArea);""",
                    """nwr["shop"="convenience"](area.searchArea);""",
                )
            normalized in setOf("uretici", "üretici", "manufacturer", "factory", "fabrika") ->
                listOf(
                    """nwr["name"]["man_made"="works"](area.searchArea);""",
                    """nwr["name"]["industrial"](area.searchArea);""",
                    """nwr["name"]["craft"](area.searchArea);""",
                )
            normalized in setOf("toptanci", "toptancı", "wholesale", "wholesaler") ->
                listOf(
                    """nwr["name"]["shop"="wholesale"](area.searchArea);""",
                    """nwr["name"~"$regex",i](area.searchArea);""",
                )
            normalized in setOf("avm", "mall", "alisveris merkezi", "alışveriş merkezi") ->
                listOf(
                    """nwr["name"]["shop"~"mall|department_store"](area.searchArea);""",
                    """nwr["name"~"$regex",i](area.searchArea);""",
                )
            normalized in setOf("gece kulubu", "gece kulübü", "nightclub", "disko", "disco") ->
                listOf(
                    """nwr["name"]["amenity"="nightclub"](area.searchArea);""",
                    """nwr["name"]["leisure"="dance"](area.searchArea);""",
                    """nwr["name"]["club"](area.searchArea);""",
                )
            normalized in setOf("ofis", "sirket", "şirket", "office", "company") ->
                listOf("""nwr["name"]["office"](area.searchArea);""")
            normalized in setOf("magaza", "mağaza", "shop") ->
                listOf("""nwr["name"]["shop"](area.searchArea);""")
            normalized in setOf("playstation", "playstation cafe", "oyun salonu", "internet cafe") ->
                listOf(
                    """nwr["amenity"="internet_cafe"](area.searchArea);""",
                    """nwr["leisure"="adult_gaming_centre"](area.searchArea);""",
                    """nwr["name"~"$regex",i](area.searchArea);""",
                )
            normalized.contains("cigkofte") || normalized.contains("çiğköfte") ->
                listOf(
                    """nwr["cuisine"~"cig_kofte|çiğ_köfte",i](area.searchArea);""",
                    """nwr["name"~"$regex",i](area.searchArea);""",
                )
            else -> listOf(
                """nwr["name"~"$regex",i](area.searchArea);""",
                """nwr["cuisine"~"$regex",i](area.searchArea);""",
                """nwr["amenity"~"$regex",i](area.searchArea);""",
                """nwr["shop"~"$regex",i](area.searchArea);""",
                """nwr["tourism"~"$regex",i](area.searchArea);""",
                """nwr["leisure"~"$regex",i](area.searchArea);""",
                """nwr["craft"~"$regex",i](area.searchArea);""",
                """nwr["office"~"$regex",i](area.searchArea);""",
                """nwr["industrial"~"$regex",i](area.searchArea);""",
                """nwr["man_made"~"$regex",i](area.searchArea);""",
                """nwr["club"~"$regex",i](area.searchArea);""",
                """nwr["healthcare"~"$regex",i](area.searchArea);""",
            )
        }
        return categoryClauses.joinToString("\n")
    }

    private fun normalize(value: String): String =
        value.trim()
            .lowercase()
            .replace('ı', 'i')
            .replace('ş', 's')
            .replace('ğ', 'g')
            .replace('ü', 'u')
            .replace('ö', 'o')
            .replace('ç', 'c')

    private fun escapeQuoted(value: String): String =
        value.replace("\\", "\\\\").replace("\"", "\\\"")

    private fun escapeRegex(value: String): String =
        value.replace("\\", "\\\\").replace("\"", "\\\"")
}

class OverpassBusinessSourceAdapter(
    private val baseUrlProvider: () -> String = { OverpassBusinessSource.BASE_URL },
    private val fallbackUrlProvider: () -> List<String> = { OverpassBusinessSource.FALLBACK_URLS },
    private val nowEpochMs: () -> Long = { System.currentTimeMillis() },
) : BusinessSourceAdapter {
    override val contract: BusinessSourceContract = OverpassBusinessSource.contract

    override suspend fun fetch(
        query: String,
        city: String,
        district: String?,
        neighborhood: String?,
    ): List<VerifiedBusiness> = withContext(Dispatchers.IO) {
        contract.validate().getOrElse { error ->
            throw IllegalStateException("Overpass kaynak sözleşmesi geçersiz", error)
        }
        require(city.isNotBlank()) { "Şehir boş olamaz" }

        val endpoints = buildList {
            add(baseUrlProvider())
            addAll(fallbackUrlProvider())
        }.map(String::trim)
            .filter { it.startsWith("https://") }
            .distinct()

        var lastFailure: Exception? = null
        for (endpoint in endpoints) {
            try {
                return@withContext fetchFromEndpoint(endpoint, query, city, district, neighborhood)
            } catch (error: kotlinx.coroutines.CancellationException) {
                throw error
            } catch (error: Exception) {
                lastFailure = error
            }
        }

        throw lastFailure ?: IllegalStateException("Overpass için kullanılabilir HTTPS endpoint bulunamadı")
    }

    private suspend fun fetchFromEndpoint(
        endpoint: String,
        query: String,
        city: String,
        district: String?,
        neighborhood: String?,
    ): List<VerifiedBusiness> {
        RateLimiter.await()

        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 20_000
            readTimeout = 120_000
            doOutput = true
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
            setRequestProperty(
                "User-Agent",
                "LANU-Global-Donuk-Satis-Radari/0.3 (+https://github.com/kanunal99-jpg/LANU-Global-Donuk-Sat-Radar-)"
            )
        }

        try {
            val encodedQuery = java.net.URLEncoder.encode(
                OverpassQueryBuilder.build(city, district, query, neighborhood),
                Charsets.UTF_8.name(),
            )
            connection.outputStream.bufferedWriter(Charsets.UTF_8).use { writer ->
                writer.write("data=")
                writer.write(encodedQuery)
            }

            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                throw IOException("Overpass HTTP $responseCode")
            }

            val input = BufferedInputStream(connection.inputStream)
            val text = buildString {
                val buffer = ByteArray(16 * 1024)
                var total = 0
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    total += read
                    if (total > 32 * 1024 * 1024) {
                        throw IllegalStateException("Overpass yanıtı güvenli boyut sınırını aştı")
                    }
                    append(String(buffer, 0, read, Charsets.UTF_8))
                }
            }
            return parse(text, city, district, nowEpochMs(), neighborhood)
        } finally {
            connection.disconnect()
        }
    }

    internal fun parse(
        payload: String,
        selectedCity: String,
        selectedDistrict: String?,
        verifiedAtEpochMs: Long,
        selectedNeighborhood: String? = null,
    ): List<VerifiedBusiness> {
        val json = JSONObject(payload)
        val elements = json.optJSONArray("elements") ?: JSONArray()
        val result = mutableListOf<VerifiedBusiness>()

        for (index in 0 until elements.length()) {
            val item = elements.optJSONObject(index) ?: continue
            val tags = item.optJSONObject("tags") ?: continue
            val name = tags.optString("name").trim()
            if (name.isBlank()) continue

            val center = item.optJSONObject("center")
            val latitude = item.optDouble("lat").takeUnless { it.isNaN() }
                ?: center?.optDouble("lat")?.takeUnless { it.isNaN() }
            val longitude = item.optDouble("lon").takeUnless { it.isNaN() }
                ?: center?.optDouble("lon")?.takeUnless { it.isNaN() }

            val district = selectedDistrict?.takeUnless { it.isBlank() || it.equals("Tümü", ignoreCase = true) }
                ?: firstTag(tags, "addr:district", "addr:county", "addr:city_district", "is_in:district")
                ?: "Bilinmiyor"

            val address = listOfNotNull(
                tags.optString("addr:street").takeIf(String::isNotBlank),
                tags.optString("addr:housenumber").takeIf(String::isNotBlank),
                tags.optString("addr:suburb").takeIf(String::isNotBlank),
                tags.optString("addr:postcode").takeIf(String::isNotBlank),
                tags.optString("addr:city").takeIf(String::isNotBlank),
            ).joinToString(", ").takeIf(String::isNotBlank)

            val category = firstTag(
                tags,
                "amenity",
                "shop",
                "craft",
                "tourism",
                "leisure",
                "office",
                "healthcare",
                "sport",
                "industrial",
                "man_made",
                "club",
                "cuisine",
            )
            val id = item.optString("type") + ":" + item.optLong("id")
            if (id.isBlank() || id.endsWith(":0")) continue

            result += VerifiedBusiness(
                id = id,
                name = name,
                city = selectedCity,
                district = district,
                neighborhood = selectedNeighborhood?.takeUnless {
                    it.isBlank() || it.equals("Tümü", ignoreCase = true)
                } ?: firstTag(
                    tags,
                    "addr:neighbourhood",
                    "addr:quarter",
                    "addr:suburb",
                    "addr:village",
                    "addr:hamlet",
                    "is_in:neighbourhood",
                ),
                source = contract.descriptor,
                verifiedAtEpochMs = verifiedAtEpochMs,
                latitude = latitude,
                longitude = longitude,
                category = category,
                address = address,
                phone = firstTag(tags, "phone", "contact:phone", "contact_phone"),
                website = firstTag(tags, "website", "contact:website", "url"),
                openingHours = firstTag(tags, "opening_hours"),
                menuUrl = firstTag(tags, "menu_url", "website:menu", "contact:menu"),
                menuText = firstTag(tags, "menu", "menu:description"),
            )
        }
        return BusinessDeduplication.deduplicate(result)
    }

    private fun firstTag(tags: JSONObject, vararg keys: String): String? =
        keys.firstNotNullOfOrNull { key ->
            tags.optString(key).trim().takeIf(String::isNotBlank)
        }

    private object RateLimiter {
        private const val MIN_INTERVAL_MS = 1_500L
        private var lastRequestAt = 0L

        @Synchronized
        fun await() {
            val now = SystemClock.elapsedRealtime()
            val wait = MIN_INTERVAL_MS - (now - lastRequestAt)
            if (wait > 0) Thread.sleep(wait)
            lastRequestAt = SystemClock.elapsedRealtime()
        }
    }
}

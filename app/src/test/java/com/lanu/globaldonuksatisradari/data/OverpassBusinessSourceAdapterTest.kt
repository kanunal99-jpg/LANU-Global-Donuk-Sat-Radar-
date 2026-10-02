package com.lanu.globaldonuksatisradari.data

import kotlin.test.Test
import kotlin.test.assertTrue

class OverpassBusinessSourceAdapterTest {
    @Test
    fun blankQueryBuildsBroadCommercialInventory() {
        val query = OverpassQueryBuilder.build("İstanbul", null, "")
        assertTrue(query.contains("""area["name"="İstanbul"]["boundary"="administrative"]["admin_level"="4"]->.searchArea;"""))
        assertTrue(query.contains("""["shop"]"""))
        assertTrue(query.contains("""["office"]"""))
        assertTrue(query.contains("""["craft"]"""))
        assertTrue(query.contains("""["industrial"]"""))
        assertTrue(query.contains("""["man_made"="works"]"""))
        assertTrue(query.contains("""["amenity"]["amenity"!~"""))
        assertTrue(query.contains("place_of_worship|school"))
        assertTrue(query.contains("""["tourism"~"hotel|hostel|motel|guest_house|apartment|chalet|camp_site|caravan_site|resort|theme_park|zoo|aquarium",i]"""))
        assertTrue(query.contains("""["leisure"~"fitness_centre|sports_centre|bowling_alley|dance|adult_gaming_centre|amusement_arcade|water_park|sauna|spa|escape_game|trampoline_park|horse_riding",i]"""))
        assertTrue(query.contains("""["club"]"""))
        assertTrue(query.contains("""["healthcare"]"""))
        assertTrue(!query.contains("""["landuse"~"retail|commercial|industrial|farmyard"]"""))
        assertTrue(!query.contains("""["place"="farm"]"""))
        assertTrue(!query.contains("""["product"]"""))
        assertTrue(query.contains("""["operator:type"="private"]"""))
        assertTrue(!query.contains("map_to_area"))
    }

    @Test
    fun districtQueryUsesDistrictAdministrativeBoundary() {
        val query = OverpassQueryBuilder.build("İstanbul", "Kadıköy", "")
        assertTrue(query.contains("""["admin_level"="6"]"""))
        assertTrue(query.contains("""["name"="Kadıköy"]"""))
        assertTrue(query.contains("districtRelation"))
    }
    @Test
    fun neighborhoodQueryBuildsAreaInsideSelectedDistrict() {
        val query = OverpassQueryBuilder.build(
            city = "İstanbul",
            district = "Kadıköy",
            query = "",
            neighborhood = "Caferağa Mahallesi",
        )

        assertTrue(query.contains("districtArea"))
        assertTrue(query.contains("neighborhoodBoundary"))
        assertTrue(query.contains("""["name"~"^Caferağa( Mahallesi)?$",i]"""))
        assertTrue(query.contains(".neighborhoodBoundary map_to_area->.searchArea;"))
    }


    @Test
    fun parserUsesSelectedDistrictAndQuarterNeighborhood() {
        val payload = """
            {"elements":[{
              "type":"node",
              "id":123,
              "lat":39.55,
              "lon":44.08,
              "tags":{
                "name":"Test Market",
                "shop":"supermarket",
                "addr:district":"Beyşehir",
                "addr:quarter":"Hürriyet Mahallesi"
              }
            }]}
        """.trimIndent()

        val result = OverpassBusinessSourceAdapter().parse(
            payload = payload,
            selectedCity = "Ağrı",
            selectedDistrict = "Doğubayazıt",
            verifiedAtEpochMs = 100L,
        )

        assertTrue(result.single().district == "Doğubayazıt")
        assertTrue(result.single().neighborhood == "Hürriyet Mahallesi")
    }

    @Test
    fun parserPinsSelectedNeighborhoodForBoundaryScopedResults() {
        val payload = """
            {"elements":[{
              "type":"node",
              "id":222,
              "lat":40.98,
              "lon":29.03,
              "tags":{
                "name":"Mahalle İçindeki İşletme",
                "office":"company"
              }
            }]}
        """.trimIndent()

        val result = OverpassBusinessSourceAdapter().parse(
            payload = payload,
            selectedCity = "İstanbul",
            selectedDistrict = "Kadıköy",
            verifiedAtEpochMs = 100L,
            selectedNeighborhood = "Caferağa",
        )

        assertTrue(result.single().neighborhood == "Caferağa")
        assertTrue(result.single().category == "company")
    }

    @Test
    fun categoryQueriesCoverRequestedBusinessTypes() {
        val cafe = OverpassQueryBuilder.build("İstanbul", "Pendik", "cafe")
        val playstation = OverpassQueryBuilder.build("İstanbul", "Pendik", "PlayStation")
        val cigkofte = OverpassQueryBuilder.build("İstanbul", "Pendik", "çiğköfte")
        assertTrue(cafe.contains("""["amenity"="cafe"]"""))
        assertTrue(playstation.contains("""["amenity"="internet_cafe"]"""))
        assertTrue(cigkofte.contains("""["cuisine"~"cig_kofte|çiğ_köfte"""))
    }
    @Test
    fun expandedCommercialCategoryQueriesReachManufacturerMallNightlifeAndOffice() {
        val manufacturer = OverpassQueryBuilder.build("İstanbul", "Pendik", "manufacturer")
        val mall = OverpassQueryBuilder.build("İstanbul", "Pendik", "mall")
        val nightclub = OverpassQueryBuilder.build("İstanbul", "Pendik", "nightclub")
        val office = OverpassQueryBuilder.build("İstanbul", "Pendik", "office")

        assertTrue(manufacturer.contains("""["man_made"="works"]"""))
        assertTrue(manufacturer.contains("""["industrial"]"""))
        assertTrue(mall.contains("""["shop"~"mall|department_store"]"""))
        assertTrue(nightclub.contains("""["amenity"="nightclub"]"""))
        assertTrue(nightclub.contains("""["club"]"""))
        assertTrue(office.contains("""["office"]"""))
    }

    @Test
    fun exhaustiveBusinessFiltersReachServiceHealthIndustryWarehouseAndCommercialBuildings() {
        val service = OverpassQueryBuilder.build("İstanbul", "Pendik", "amenity")
        val health = OverpassQueryBuilder.build("İstanbul", "Pendik", "healthcare")
        val industry = OverpassQueryBuilder.build("İstanbul", "Pendik", "industrial")
        val warehouse = OverpassQueryBuilder.build("İstanbul", "Pendik", "warehouse")
        val commercial = OverpassQueryBuilder.build("İstanbul", "Pendik", "commercial")

        assertTrue(service.contains("""["amenity"]["amenity"!~"""))
        assertTrue(health.contains("""["healthcare"]"""))
        assertTrue(industry.contains("""["industrial"]"""))
        assertTrue(warehouse.contains("""["building"="warehouse"]"""))
        assertTrue(commercial.contains("""["building"~"retail|commercial|industrial|warehouse|office|supermarket|kiosk|hotel"]"""))
    }

    @Test
    fun genericCategoryQueriesReachTourismAndLeisureTags() {
        val hotel = OverpassQueryBuilder.build("İstanbul", "Pendik", "hotel")
        val gaming = OverpassQueryBuilder.build("İstanbul", "Pendik", "adult_gaming_centre")

        assertTrue(hotel.contains("""["tourism"~"hotel",i]"""))
        assertTrue(gaming.contains("""["leisure"~"adult_gaming_centre",i]"""))
    }

    @Test
    fun addedBusinessFamiliesBuildExplicitSourceQueries() {
        val education = OverpassQueryBuilder.build("İstanbul", "Pendik", "education")
        val automotive = OverpassQueryBuilder.build("İstanbul", "Pendik", "automotive")
        val beauty = OverpassQueryBuilder.build("İstanbul", "Pendik", "beauty")
        val finance = OverpassQueryBuilder.build("İstanbul", "Pendik", "finance")
        val construction = OverpassQueryBuilder.build("İstanbul", "Pendik", "construction")
        val agriculture = OverpassQueryBuilder.build("İstanbul", "Pendik", "agriculture")
        val logistics = OverpassQueryBuilder.build("İstanbul", "Pendik", "logistics")

        assertTrue(education.contains("""["amenity"~"school|kindergarten|college|university|language_school|music_school|driving_school|training|childcare",i]"""))
        assertTrue(automotive.contains("""["shop"~"car|car_repair|car_parts|tyres|motorcycle|bicycle",i]"""))
        assertTrue(beauty.contains("""["shop"~"hairdresser|beauty|cosmetics|massage|tattoo",i]"""))
        assertTrue(finance.contains("""["office"~"financial|insurance|estate_agent|accountant",i]"""))
        assertTrue(construction.contains("""["office"~"construction|architect|engineer",i]"""))
        assertTrue(agriculture.contains("""["place"="farm"]"""))
        assertTrue(logistics.contains("""["office"~"logistics|transport|moving_company",i]"""))
    }

    @Test
    fun parserKeepsCommercialLanduseAndFarmCategories() {
        val payload = """
            {"elements":[
              {"type":"way","id":501,"center":{"lat":40.9,"lon":29.1},"tags":{"name":"Örnek Sanayi","landuse":"industrial"}},
              {"type":"node","id":502,"lat":40.91,"lon":29.11,"tags":{"name":"Örnek Çiftlik","place":"farm"}}
            ]}
        """.trimIndent()

        val result = OverpassBusinessSourceAdapter().parse(
            payload = payload,
            selectedCity = "İstanbul",
            selectedDistrict = "Pendik",
            verifiedAtEpochMs = 100L,
        )

        assertTrue(result.any { it.name == "Örnek Sanayi" && it.category == "industrial" })
        assertTrue(result.any { it.name == "Örnek Çiftlik" && it.category == "farm" })
    }

}

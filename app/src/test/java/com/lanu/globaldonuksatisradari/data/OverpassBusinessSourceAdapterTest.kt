package com.lanu.globaldonuksatisradari.data

import kotlin.test.Test
import kotlin.test.assertTrue

class OverpassBusinessSourceAdapterTest {
    @Test
    fun blankQueryBuildsBroadCommercialInventoryWithoutCivicPoiNoise() {
        val query = OverpassQueryBuilder.build("İstanbul", null, "")
        assertTrue(query.contains("""area["name"="İstanbul"]["boundary"="administrative"]["admin_level"="4"]->.searchArea;"""))
        assertTrue(query.contains("""["shop"]["name"]"""))
        assertTrue(query.contains("""["shop"]["brand"]"""))
        assertTrue(query.contains("""["shop"]["operator"]"""))
        assertTrue(query.contains("""["office"]["name"]["office"!~""""))
        assertTrue(query.contains("government|ngo|association|foundation"))
        assertTrue(query.contains("""["craft"]["name"]"""))
        assertTrue(query.contains("""["industrial"]["name"]"""))
        assertTrue(query.contains("""["man_made"="works"]["name"]"""))
        assertTrue(query.contains("""["amenity"]["name"]["amenity"!~""""))
        assertTrue(query.contains("post_office|atm"))
        assertTrue(query.contains("""["tourism"~"^(hotel|hostel|motel|guest_house|apartment"""))
        assertTrue(query.contains("""["leisure"~"^(adult_gaming_centre|amusement_arcade|bowling_alley"""))
        assertTrue(query.contains("""["healthcare"]["name"]"""))
        assertTrue(query.contains("""["building"~"retail|commercial|industrial|warehouse|office|supermarket|kiosk|hotel"]["name"]"""))
        assertTrue(!query.contains("""nwr["name"]["tourism"]"""))
        assertTrue(!query.contains("""nwr["name"]["leisure"]"""))
        assertTrue(!query.contains("""nwr["name"]["club"]"""))
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
    fun parserAcceptsBrandOrOperatorWhenNameTagIsMissing() {
        val payload = """
            {"elements":[
              {
                "type":"node","id":1,"lat":40.99,"lon":29.26,
                "tags":{"shop":"convenience","brand":"Marka Market"}
              },
              {
                "type":"node","id":2,"lat":40.98,"lon":29.27,
                "tags":{"office":"company","operator":"Operatör Şirket"}
              }
            ]}
        """.trimIndent()

        val result = OverpassBusinessSourceAdapter().parse(
            payload = payload,
            selectedCity = "İstanbul",
            selectedDistrict = "Sultanbeyli",
            verifiedAtEpochMs = 100L,
        )

        assertTrue(result.any { it.name == "Marka Market" })
        assertTrue(result.any { it.name == "Operatör Şirket" })
    }

}

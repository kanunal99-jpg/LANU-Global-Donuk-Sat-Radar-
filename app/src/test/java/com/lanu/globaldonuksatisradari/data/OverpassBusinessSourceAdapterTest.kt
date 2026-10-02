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
        assertTrue(query.contains("nightclub"))
        assertTrue(query.contains("mall").not())
        assertTrue(query.contains("hotel|hostel|motel"))
        assertTrue(query.contains("adult_gaming_centre"))
        assertTrue(query.contains("""["club"]"""))
        assertTrue(query.contains("""["healthcare"]"""))
        assertTrue(!query.contains("place_of_worship"))
        assertTrue(!query.contains("school"))
        assertTrue(!query.contains("community_centre"))
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
    fun genericCategoryQueriesReachTourismAndLeisureTags() {
        val hotel = OverpassQueryBuilder.build("İstanbul", "Pendik", "hotel")
        val gaming = OverpassQueryBuilder.build("İstanbul", "Pendik", "adult_gaming_centre")

        assertTrue(hotel.contains("""["tourism"~"hotel",i]"""))
        assertTrue(gaming.contains("""["leisure"~"adult_gaming_centre",i]"""))
    }

}

package com.lanu.globaldonuksatisradari.data

import kotlin.test.Test
import kotlin.test.assertTrue

class OverpassBusinessSourceAdapterTest {
    @Test
    fun blankIstanbulQueryBuildsSalesTargetInventoryOnly() {
        val query = OverpassQueryBuilder.build("İstanbul", null, "")
        assertTrue(query.contains("""area["name"="İstanbul"]["boundary"="administrative"]["admin_level"="4"]->.searchArea;"""))
        assertTrue(query.contains("restaurant|cafe|fast_food"))
        assertTrue(query.contains("supermarket|convenience|food|bakery"))
        assertTrue(query.contains("hotel|hostel|motel"))
        assertTrue(query.contains("""["amenity"="internet_cafe"]"""))
        assertTrue(query.contains("""["leisure"="adult_gaming_centre"]"""))
        assertTrue(!query.contains("place_of_worship"))
        assertTrue(!query.contains("healthcare"))
        assertTrue(!query.contains("school"))
        assertTrue(!query.contains("sport"))
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
    fun categoryQueriesCoverRequestedBusinessTypes() {
        val cafe = OverpassQueryBuilder.build("İstanbul", "Pendik", "cafe")
        val playstation = OverpassQueryBuilder.build("İstanbul", "Pendik", "PlayStation")
        val cigkofte = OverpassQueryBuilder.build("İstanbul", "Pendik", "çiğköfte")
        assertTrue(cafe.contains("""["amenity"="cafe"]"""))
        assertTrue(playstation.contains("""["amenity"="internet_cafe"]"""))
        assertTrue(cigkofte.contains("""["cuisine"~"cig_kofte|çiğ_köfte"""))
    }
    @Test
    fun genericCategoryQueriesReachTourismAndLeisureTags() {
        val hotel = OverpassQueryBuilder.build("İstanbul", "Pendik", "hotel")
        val gaming = OverpassQueryBuilder.build("İstanbul", "Pendik", "adult_gaming_centre")

        assertTrue(hotel.contains("""["tourism"~"hotel",i]"""))
        assertTrue(gaming.contains("""["leisure"~"adult_gaming_centre",i]"""))
    }

}

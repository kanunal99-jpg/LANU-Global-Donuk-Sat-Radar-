package com.lanu.globaldonuksatisradari.data

import kotlin.test.Test
import kotlin.test.assertTrue

class OverpassBusinessSourceAdapterTest {
    @Test
    fun blankIstanbulQueryBuildsBroadBusinessInventory() {
        val query = OverpassQueryBuilder.build("İstanbul", null, "")
        assertTrue(query.contains("""area["name"="İstanbul"]["boundary"="administrative"]["admin_level"="4"]->.searchArea;"""))
        assertTrue(query.contains("""nwr["name"]["amenity"]"""))
        assertTrue(query.contains("""nwr["name"]["shop"]"""))
        assertTrue(query.contains("""nwr["name"]["craft"]"""))
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
    fun categoryQueriesCoverRequestedBusinessTypes() {
        val cafe = OverpassQueryBuilder.build("İstanbul", "Pendik", "cafe")
        val playstation = OverpassQueryBuilder.build("İstanbul", "Pendik", "PlayStation")
        val cigkofte = OverpassQueryBuilder.build("İstanbul", "Pendik", "çiğköfte")
        assertTrue(cafe.contains("""["amenity"="cafe"]"""))
        assertTrue(playstation.contains("""["amenity"="internet_cafe"]"""))
        assertTrue(cigkofte.contains("""["cuisine"~"cig_kofte|çiğ_köfte"""))
    }
}

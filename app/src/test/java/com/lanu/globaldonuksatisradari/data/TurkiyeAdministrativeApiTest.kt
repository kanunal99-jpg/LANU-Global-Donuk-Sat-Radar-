package com.lanu.globaldonuksatisradari.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TurkiyeAdministrativeApiTest {
    @Test
    fun provinceParserUsesExactNormalizedTurkishName() {
        val payload = """
            {"data":[
              {"id":4,"name":"Ağrı"},
              {"id":34,"name":"İstanbul"}
            ]}
        """.trimIndent()

        assertEquals(4, TurkiyeAdministrativeApi.parseProvinceId(payload, "AĞRI"))
        assertEquals(34, TurkiyeAdministrativeApi.parseProvinceId(payload, "istanbul"))
        assertNull(TurkiyeAdministrativeApi.parseProvinceId(payload, "Konya"))
    }

    @Test
    fun districtParserRejectsRowsFromOtherProvinceAndDeduplicatesNames() {
        val payload = """
            {"data":[
              {"id":100,"name":"Doğubayazıt","provinceId":4},
              {"id":101,"name":"Patnos","provinceId":4},
              {"id":102,"name":"PATNOS","provinceId":4},
              {"id":200,"name":"Beyşehir","provinceId":42}
            ]}
        """.trimIndent()

        val result = TurkiyeAdministrativeApi.parseDistricts(payload, 4)

        assertEquals(listOf("Doğubayazıt", "Patnos"), result.map { it.name })
    }

    @Test
    fun neighborhoodParserDeduplicatesCaseAndTurkishCharacters() {
        val payload = """
            {"data":[
              {"id":1,"name":"Yeni Mahalle"},
              {"id":2,"name":"YENİ MAHALLE"},
              {"id":3,"name":"Hürriyet"}
            ]}
        """.trimIndent()

        val result = TurkiyeAdministrativeApi.parseNeighborhoods(payload)

        assertEquals(2, result.size)
        assertEquals(setOf("Yeni Mahalle", "Hürriyet"), result.toSet())
    }
}

package com.lanu.globaldonuksatisradari.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NominatimBusinessSourceAdapterTest {
    @Test
    fun parse_maps_contact_fields_from_extratags() {
        val payload = """
            [{
              "osm_type":"node",
              "osm_id":"123",
              "name":"Test Cafe",
              "lat":"41.0901",
              "lon":"29.0602",
              "type":"cafe",
              "display_name":"Test Cafe, Besiktas, Istanbul, Turkiye",
              "address":{"neighbourhood":"Ornek Mahallesi","suburb":"Besiktas"},
              "extratags":{
                "phone":"+90 212 000 00 00",
                "website":"https://example.com",
                "opening_hours":"Mo-Sa 08:00-22:00"
              },
              "namedetails":{"name:tr":"Test Cafe"}
            }]
        """.trimIndent()

        val result = NominatimBusinessSourceAdapter().parse(payload, "Istanbul", null, 123L)

        assertEquals(1, result.size)
        assertEquals("+90 212 000 00 00", result.single().phone)
        assertEquals("https://example.com", result.single().website)
        assertEquals("Mo-Sa 08:00-22:00", result.single().openingHours)
        assertEquals("Ornek Mahallesi", result.single().neighborhood)
    }

    @Test
    fun parse_uses_contact_fallback_keys_and_does_not_invent_missing_values() {
        val payload = """
            [{
              "osm_type":"way",
              "osm_id":"456",
              "name":"Fallback Restaurant",
              "address":{"city_district":"Kadikoy"},
              "extratags":{
                "contact:phone":"+90 216 111 11 11",
                "contact:website":"https://restaurant.example"
              }
            },{
              "osm_type":"node",
              "osm_id":"789",
              "name":"No Contact",
              "address":{"town":"Uskudar"}
            }]
        """.trimIndent()

        val result = NominatimBusinessSourceAdapter().parse(payload, "Istanbul", null, 456L)

        assertEquals(2, result.size)
        val fallback = result.first { it.id == "way:456" }
        assertEquals("+90 216 111 11 11", fallback.phone)
        assertEquals("https://restaurant.example", fallback.website)
        assertNull(fallback.openingHours)

        val missing = result.first { it.id == "node:789" }
        assertNull(missing.phone)
        assertNull(missing.website)
        assertNull(missing.openingHours)
    }
}

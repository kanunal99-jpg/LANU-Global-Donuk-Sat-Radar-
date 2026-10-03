package com.lanu.globaldonuksatisradari.data

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BusinessTextSearchTest {
    @Test
    fun separatedWordsStillMatchBusinessName() {
        assertTrue(
            BusinessTextSearch.matches(
                "sandora cafe",
                "Sandora Fast Food & Cafe",
                "fast_food_restaurant",
            ),
        )
    }

    @Test
    fun everyQueryTokenMustBePresent() {
        assertFalse(
            BusinessTextSearch.matches(
                "sandora market",
                "Sandora Fast Food & Cafe",
                "fast_food_restaurant",
            ),
        )
    }

    @Test
    fun turkishCharactersAndPunctuationAreNormalized() {
        assertTrue(
            BusinessTextSearch.matches(
                "özgürlük cafe",
                "Cafe",
                "Mimar Sinan, Özgürlük Cd. No:76/A",
            ),
        )
    }

    @Test
    fun overpassRegexAllowsWordsBetweenHumanQueryTokens() {
        assertTrue(BusinessTextSearch.flexibleRegex("sandora cafe") == "sandora.*cafe")
    }
}

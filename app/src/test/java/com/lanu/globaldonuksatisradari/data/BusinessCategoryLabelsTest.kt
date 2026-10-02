package com.lanu.globaldonuksatisradari.data

import kotlin.test.Test
import kotlin.test.assertEquals

class BusinessCategoryLabelsTest {
    @Test
    fun commonOsmCategoriesAreHumanReadableInExports() {
        assertEquals("Restoran", BusinessCategoryLabels.displayName("restaurant"))
        assertEquals("Süpermarket", BusinessCategoryLabels.displayName("supermarket"))
        assertEquals("Otel", BusinessCategoryLabels.displayName("hotel"))
        assertEquals("Kafe", BusinessCategoryLabels.displayName("cafe"))
    }

    @Test
    fun unknownManualBusinessTypeIsPreserved() {
        assertEquals("Kurumsal Yemekhane", BusinessCategoryLabels.displayName("Kurumsal Yemekhane"))
    }

    @Test
    fun broadCommercialCategoriesAreExposedForSourceDrivenSearch() {
        assertEquals("manufacturer", BusinessCategoryLabels.searchQueryForLabel("Üretici / Fabrika"))
        assertEquals("wholesale", BusinessCategoryLabels.searchQueryForLabel("Toptancı"))
        assertEquals("mall", BusinessCategoryLabels.searchQueryForLabel("AVM"))
        assertEquals("nightclub", BusinessCategoryLabels.searchQueryForLabel("Gece Kulübü / Disko"))
        assertEquals("office", BusinessCategoryLabels.searchQueryForLabel("Şirket / Ofis"))
        assertEquals("shop", BusinessCategoryLabels.searchQueryForLabel("Mağaza"))
        assertEquals("amenity", BusinessCategoryLabels.searchQueryForLabel("Tüm Hizmet İşletmeleri"))
        assertEquals("healthcare", BusinessCategoryLabels.searchQueryForLabel("Sağlık İşletmeleri"))
        assertEquals("leisure", BusinessCategoryLabels.searchQueryForLabel("Eğlence / Aktivite"))
        assertEquals("industrial", BusinessCategoryLabels.searchQueryForLabel("Sanayi"))
        assertEquals("warehouse", BusinessCategoryLabels.searchQueryForLabel("Depo / Lojistik"))
        assertEquals("tourism", BusinessCategoryLabels.searchQueryForLabel("Turizm İşletmeleri"))
        assertEquals("commercial", BusinessCategoryLabels.searchQueryForLabel("Ticari Bina / Kompleks"))
    }

    @Test
    fun searchCategoryLabelsCanDriveSourceQueriesBeforeResultsExist() {
        assertEquals("restaurant", BusinessCategoryLabels.searchQueryForLabel("Restoran"))
        assertEquals("hotel", BusinessCategoryLabels.searchQueryForLabel("Otel"))
        assertEquals("internet cafe", BusinessCategoryLabels.searchQueryForLabel("İnternet Kafe"))
        assertEquals("adult_gaming_centre", BusinessCategoryLabels.searchQueryForLabel("Oyun Salonu"))
    }
}

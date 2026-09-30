package com.lanu.globaldonuksatisradari.crm

internal fun matchesCrmRegion(
    customerCity: String,
    customerDistrict: String,
    selectedCity: String,
    selectedDistrict: String?,
): Boolean =
    customerCity.equals(selectedCity, ignoreCase = true) &&
        (selectedDistrict == null || customerDistrict.equals(selectedDistrict, ignoreCase = true))

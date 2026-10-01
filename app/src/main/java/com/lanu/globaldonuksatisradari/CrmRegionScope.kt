package com.lanu.globaldonuksatisradari

import com.lanu.globaldonuksatisradari.crm.CrmCustomer

internal fun scopeCrmCustomers(
    customers: List<CrmCustomer>,
    city: String,
    district: String,
): List<CrmCustomer> =
    customers.filter { customer ->
        customer.city.equals(city, ignoreCase = true) &&
            (district == "Tümü" || customer.district.equals(district, ignoreCase = true))
    }

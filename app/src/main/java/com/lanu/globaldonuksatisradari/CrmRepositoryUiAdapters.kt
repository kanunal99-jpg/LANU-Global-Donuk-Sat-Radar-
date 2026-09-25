package com.lanu.globaldonuksatisradari

import com.lanu.globaldonuksatisradari.crm.CrmActivity
import com.lanu.globaldonuksatisradari.crm.CrmActivityType
import com.lanu.globaldonuksatisradari.crm.LocalCrmRepository

/**
 * UI-safe adapter for recording an activity with a note.
 *
 * LocalCrmRepository.recordActivity's third positional parameter is the
 * occurrence timestamp (Long). UI callbacks naturally provide a note as the
 * third value. Keeping this overload next to the UI prevents a nullable String
 * from being accidentally bound to the timestamp parameter while preserving
 * the repository API and its default timestamp behaviour.
 */
suspend fun LocalCrmRepository.recordActivity(
    customerId: String,
    type: CrmActivityType,
    note: String?,
): CrmActivity = recordActivity(
    customerId = customerId,
    type = type,
    note = note,
)

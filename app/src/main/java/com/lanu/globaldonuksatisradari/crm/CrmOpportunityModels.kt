package com.lanu.globaldonuksatisradari.crm

enum class CrmOpportunityStatus {
    OPEN,
    WON,
    LOST,
}

enum class CrmValueOrigin {
    OBSERVED,
    ESTIMATED,
    USER_ENTERED,
    UNKNOWN,
}

data class CrmOpportunity(
    val id: String,
    val customerId: String,
    val title: String,
    val status: CrmOpportunityStatus = CrmOpportunityStatus.OPEN,
    val notes: String? = null,
    val estimatedValueMinor: Long? = null,
    val currency: String? = null,
    val valueOrigin: CrmValueOrigin = CrmValueOrigin.UNKNOWN,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
    val version: Long = 1L,
    val syncState: SyncState = SyncState.LOCAL_ONLY,
)

package com.lanu.globaldonuksatisradari.crm

/** Deterministic conflict policy used when local and remote versions describe the same customer. */
enum class ConflictResolution {
    LOCAL_WINS,
    REMOTE_WINS,
    UNRESOLVED,
}

data class RemoteCustomerSnapshot(
    val customer: CrmCustomer,
)

object CrmConflictResolver {
    fun resolve(local: CrmCustomer, remote: RemoteCustomerSnapshot): ConflictResolution {
        val remoteCustomer = remote.customer
        return when {
            remoteCustomer.version > local.version -> ConflictResolution.REMOTE_WINS
            remoteCustomer.version < local.version -> ConflictResolution.LOCAL_WINS
            remoteCustomer.updatedAtEpochMs > local.updatedAtEpochMs -> ConflictResolution.REMOTE_WINS
            remoteCustomer.updatedAtEpochMs < local.updatedAtEpochMs -> ConflictResolution.LOCAL_WINS
            remoteCustomer == local -> ConflictResolution.LOCAL_WINS
            else -> ConflictResolution.UNRESOLVED
        }
    }
}

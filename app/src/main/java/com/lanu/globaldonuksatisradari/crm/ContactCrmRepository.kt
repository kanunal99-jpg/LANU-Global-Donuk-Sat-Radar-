package com.lanu.globaldonuksatisradari.crm

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

/**
 * Local-first customer-contact workflow.
 *
 * Contacts are owner-scoped at the repository boundary: an authenticated repository may only
 * read/mutate contacts whose parent customer belongs to that user, while a local repository may
 * only access anonymous/local customers. Cloud sync stays disabled until LANU backend tables/RLS
 * are verified.
 */
class ContactCrmRepository(
    private val database: LanuCrmDatabase,
    private val ownerUserId: String? = null,
    private val now: () -> Long = { System.currentTimeMillis() },
    private val idGenerator: () -> String = { UUID.randomUUID().toString() },
) {
    fun observeContacts(customerId: String): Flow<List<CrmContact>> =
        database.contactDao().observeForCustomer(customerId).map { rows ->
            val customer = database.customerDao().findById(customerId)
            if (customer?.ownerUserId == ownerUserId) rows.map(::toDomain) else emptyList()
        }

    suspend fun createContact(
        customerId: String,
        fullName: String,
        role: String? = null,
        phone: String? = null,
        email: String? = null,
        makePrimary: Boolean = false,
    ): CrmContact = database.withTransaction {
        requireOwnedCustomer(customerId)
        val normalizedName = CrmContactValidator.normalizeName(fullName)
        val normalizedRole = CrmContactValidator.normalizeOptionalText(role)
        val normalizedPhone = CrmContactValidator.normalizePhone(phone)
        val normalizedEmail = CrmContactValidator.normalizeEmail(email)
        val shouldBePrimary = makePrimary || database.contactDao().countForCustomer(customerId) == 0
        val timestamp = now()

        if (shouldBePrimary) {
            database.contactDao().clearPrimary(
                customerId = customerId,
                updatedAtEpochMs = timestamp,
                state = SyncState.LOCAL_ONLY.name,
            )
        }

        val entity = CrmContactEntity(
            id = idGenerator(),
            customerId = customerId,
            fullName = normalizedName,
            role = normalizedRole,
            phone = normalizedPhone,
            email = normalizedEmail,
            isPrimary = shouldBePrimary,
            createdAtEpochMs = timestamp,
            updatedAtEpochMs = timestamp,
            version = 1L,
            syncState = SyncState.LOCAL_ONLY.name,
        )
        database.contactDao().upsert(entity)
        toDomain(entity)
    }

    suspend fun makePrimary(contactId: String): CrmContact = database.withTransaction {
        val current = database.contactDao().findById(contactId)
            ?: error("Yetkili kişi bulunamadı: $contactId")
        requireOwnedCustomer(current.customerId)
        val timestamp = now()
        database.contactDao().clearPrimary(
            customerId = current.customerId,
            updatedAtEpochMs = timestamp,
            state = SyncState.LOCAL_ONLY.name,
        )
        val updated = current.copy(
            isPrimary = true,
            updatedAtEpochMs = timestamp,
            version = current.version + 1L,
            syncState = SyncState.LOCAL_ONLY.name,
        )
        database.contactDao().upsert(updated)
        toDomain(updated)
    }

    private suspend fun requireOwnedCustomer(customerId: String): CrmCustomerEntity {
        val customer = database.customerDao().findById(customerId)
            ?: error("Yetkili kişi için CRM müşterisi bulunamadı: $customerId")
        require(customer.ownerUserId == ownerUserId) {
            "CRM müşterisi aktif kullanıcı kapsamına ait değil: $customerId"
        }
        return customer
    }

    private fun toDomain(entity: CrmContactEntity) = CrmContact(
        id = entity.id,
        customerId = entity.customerId,
        fullName = entity.fullName,
        role = entity.role,
        phone = entity.phone,
        email = entity.email,
        isPrimary = entity.isPrimary,
        createdAtEpochMs = entity.createdAtEpochMs,
        updatedAtEpochMs = entity.updatedAtEpochMs,
        version = entity.version,
        syncState = SyncState.valueOf(entity.syncState),
    )
}

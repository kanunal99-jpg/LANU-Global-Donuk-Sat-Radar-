package com.lanu.globaldonuksatisradari.crm

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

/** Local-first customer-contact workflow. Cloud sync is enabled only after LANU backend tables/RLS are verified. */
class ContactCrmRepository(
    private val database: LanuCrmDatabase,
    private val now: () -> Long = { System.currentTimeMillis() },
    private val idGenerator: () -> String = { UUID.randomUUID().toString() },
) {
    fun observeContacts(customerId: String): Flow<List<CrmContact>> =
        database.contactDao().observeForCustomer(customerId).map { rows -> rows.map(::toDomain) }

    suspend fun createContact(
        customerId: String,
        fullName: String,
        role: String? = null,
        phone: String? = null,
        email: String? = null,
        makePrimary: Boolean = false,
    ): CrmContact = database.withTransaction {
        require(database.customerDao().findById(customerId) != null) {
            "Yetkili kişi için CRM müşterisi bulunamadı: $customerId"
        }
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

package com.lanu.globaldonuksatisradari.crm

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

/**
 * Local-first customer-contact workflow with owner-scoped cloud queueing.
 *
 * A contact is always persisted locally first. Authenticated-owner records are queued in the same
 * Room transaction as the local mutation; anonymous/local-only customers remain LOCAL_ONLY.
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
        val syncState = CommercialCrmSync.stateFor(ownerUserId)

        if (shouldBePrimary) {
            clearAndQueuePreviousPrimary(customerId, timestamp, syncState)
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
            syncState = syncState,
        )
        database.contactDao().upsert(entity)
        enqueueIfCloudOwned(entity, LocalCrmRepository.OP_CREATE, timestamp)
        toDomain(entity)
    }

    suspend fun updateContact(
        contactId: String,
        fullName: String,
        role: String? = null,
        phone: String? = null,
        email: String? = null,
    ): CrmContact = database.withTransaction {
        val current = database.contactDao().findById(contactId)
            ?: error("Yetkili kişi bulunamadı: $contactId")
        requireOwnedCustomer(current.customerId)
        val timestamp = now()
        val updated = current.copy(
            fullName = CrmContactValidator.normalizeName(fullName),
            role = CrmContactValidator.normalizeOptionalText(role),
            phone = CrmContactValidator.normalizePhone(phone),
            email = CrmContactValidator.normalizeEmail(email),
            updatedAtEpochMs = timestamp,
            version = current.version + 1L,
            syncState = CommercialCrmSync.stateFor(ownerUserId),
        )
        database.contactDao().upsert(updated)
        enqueueIfCloudOwned(updated, LocalCrmRepository.OP_UPDATE, timestamp)
        toDomain(updated)
    }

    suspend fun makePrimary(contactId: String): CrmContact = database.withTransaction {
        val current = database.contactDao().findById(contactId)
            ?: error("Yetkili kişi bulunamadı: $contactId")
        requireOwnedCustomer(current.customerId)
        if (current.isPrimary) return@withTransaction toDomain(current)

        val timestamp = now()
        val syncState = CommercialCrmSync.stateFor(ownerUserId)
        clearAndQueuePreviousPrimary(current.customerId, timestamp, syncState)

        val refreshed = database.contactDao().findById(contactId)
            ?: error("Yetkili kişi güncelleme sırasında bulunamadı: $contactId")
        val updated = refreshed.copy(
            isPrimary = true,
            updatedAtEpochMs = timestamp,
            version = refreshed.version + 1L,
            syncState = syncState,
        )
        database.contactDao().upsert(updated)
        enqueueIfCloudOwned(updated, LocalCrmRepository.OP_UPDATE, timestamp)
        toDomain(updated)
    }

    private suspend fun clearAndQueuePreviousPrimary(
        customerId: String,
        timestamp: Long,
        syncState: String,
    ) {
        val previousPrimaries = database.contactDao().listForCustomer(customerId).filter { it.isPrimary }
        if (previousPrimaries.isEmpty()) return

        database.contactDao().clearPrimary(
            customerId = customerId,
            updatedAtEpochMs = timestamp,
            state = syncState,
        )
        if (ownerUserId.isNullOrBlank()) return

        previousPrimaries.forEach { previous ->
            val cleared = database.contactDao().findById(previous.id) ?: return@forEach
            CommercialCrmSync.enqueue(
                database = database,
                operationId = idGenerator(),
                entityType = CommercialCrmSync.ENTITY_CONTACT,
                entityId = cleared.id,
                operation = LocalCrmRepository.OP_UPDATE,
                payloadVersion = cleared.version,
                payloadJson = CommercialCrmSync.contactPayload(cleared),
                createdAtEpochMs = timestamp,
            )
        }
    }

    private suspend fun enqueueIfCloudOwned(
        entity: CrmContactEntity,
        operation: String,
        timestamp: Long,
    ) {
        if (ownerUserId.isNullOrBlank()) return
        CommercialCrmSync.enqueue(
            database = database,
            operationId = idGenerator(),
            entityType = CommercialCrmSync.ENTITY_CONTACT,
            entityId = entity.id,
            operation = operation,
            payloadVersion = entity.version,
            payloadJson = CommercialCrmSync.contactPayload(entity),
            createdAtEpochMs = timestamp,
        )
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

package com.lanu.globaldonuksatisradari.crm

import org.json.JSONObject
import java.util.UUID

/** The server checks version and applies mutation + idempotency receipt atomically. */
internal enum class AtomicCrmMutationStatus { APPLIED, CONFLICT, INVALID_VERSION }

/**
 * p_operation_id is a stable UUID taken from the persisted Room outbox.
 * p_expected_version must be EXACTLY previous version, not current version.
 *
 * Never use GET-version then POST-upsert as a fallback: it has a time-of-check/use race.
 */
internal fun buildAtomicCrmMutationRequest(
    operation: SyncOperationEntity,
    remoteRow: JSONObject,
): String {
    require(operation.entityType in setOf(
        CommercialCrmSync.ENTITY_CONTACT,
        CommercialCrmSync.ENTITY_QUOTE,
        CommercialCrmSync.ENTITY_QUOTE_LINE,
        CommercialCrmSync.ENTITY_ORDER,
        CommercialCrmSync.ENTITY_ORDER_LINE,
        LocalCrmRepository.ENTITY_NEXT_ACTION,
        LocalCrmRepository.ENTITY_OPPORTUNITY,
    )) { "Atomik senkronizasyon için desteklenmeyen varlık türü." }
    require(UUID.fromString(operation.id).toString() == operation.id.lowercase()) {
        "Senkronizasyon işlemi için geçersiz UUID."
    }
    require(UUID.fromString(operation.entityId).toString() == operation.entityId.lowercase()) {
        "Senkronize edilecek varlığın UUID değeri geçersiz."
    }
    require(operation.payloadVersion >= 1L) { "Sürüm numarası en az 1 olmalı." }
    require(remoteRow.getString("id") == operation.entityId) {
        "Senkronizasyon kimliği ve kayıt kimliği eşleşmiyor."
    }
    require(remoteRow.getLong("version") == operation.payloadVersion) {
        "Kuyruk sürümü ve veri sürümü uyuşmuyor."
    }

    return JSONObject()
        .put("p_operation_id", operation.id)
        .put("p_entity_type", operation.entityType)
        .put("p_payload", remoteRow)
        .put("p_expected_version", operation.payloadVersion - 1L)
        .toString()
}

/** RPC returns a JSON scalar string. Unknown responses must NEVER count as success. */
internal fun parseAtomicCrmMutationStatus(raw: String): AtomicCrmMutationStatus {
    val response = try {
        JSONObject().put("value", JSONObject("{\"value\":$raw}").getString("value"))
            .getString("value")
    } catch (error: Exception) {
        throw IllegalStateException("Atomik CRM sunucu yanıtı geçersiz.", error)
    }
    return AtomicCrmMutationStatus.entries.firstOrNull { it.name == response }
        ?: throw IllegalStateException("Atomik CRM sunucusundan bilinmeyen yanıt: $response")
}

package com.lanu.globaldonuksatisradari.crm

import android.content.Context
import android.content.SharedPreferences
import androidx.room.withTransaction
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.math.BigDecimal
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

object SupabaseConfig {
    const val URL = "https://jolfbmwxmsamzqtxassg.supabase.co"
    const val PUBLISHABLE_KEY = "sb_publishable_8kdSesbgIiI3V8TjmcMcZA_4FP8hTkM"
}

data class SupabaseSession(val accessToken: String, val refreshToken: String, val userId: String)

private class SecureTokenStore(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("lanu_secure_session", Context.MODE_PRIVATE)
    private val keyAlias = "lanu_supabase_session_key"

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(keyAlias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                keyAlias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setUserAuthenticationRequired(false)
                .build(),
        )
        return generator.generateKey()
    }

    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        return Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" +
            Base64.encodeToString(cipher.doFinal(value.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
    }

    private fun decrypt(value: String): String? = runCatching {
        val parts = value.split(':', limit = 2)
        if (parts.size != 2) return null
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP)))
        String(cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)), Charsets.UTF_8)
    }.getOrNull()

    fun save(session: SupabaseSession) {
        prefs.edit().putString("access", encrypt(session.accessToken))
            .putString("refresh", encrypt(session.refreshToken))
            .putString("user", session.userId).apply()
    }

    fun load(): SupabaseSession? {
        val access = prefs.getString("access", null)?.let(::decrypt) ?: return null
        val refresh = prefs.getString("refresh", null)?.let(::decrypt) ?: return null
        val user = prefs.getString("user", null) ?: return null
        return SupabaseSession(access, refresh, user)
    }

    fun clear() = prefs.edit().clear().apply()
}

class SupabaseAuthClient(context: Context) {
    private val store = SecureTokenStore(context.applicationContext)
    private val _session = MutableStateFlow(store.load())
    val session: StateFlow<SupabaseSession?> = _session.asStateFlow()

    suspend fun signIn(email: String, password: String): Result<Unit> =
        authenticate("/auth/v1/token?grant_type=password", email, password)

    suspend fun signUp(email: String, password: String): Result<Unit> = withContext(Dispatchers.IO) { runCatching {
        require(email.contains("@")) { "Geçerli bir e-posta adresi girin." }
        require(password.length >= 8) { "Şifre en az 8 karakter olmalıdır." }
        val response = request(
            "POST", "/auth/v1/signup",
            JSONObject().put("email", email.trim()).put("password", password).toString(),
        )
        val session = response.optJSONObject("session")
        val user = response.optJSONObject("user")
        val access = session?.optString("access_token").orEmpty()
        val refresh = session?.optString("refresh_token").orEmpty()
        val userId = user?.optString("id").orEmpty()
        if (access.isNotBlank() && refresh.isNotBlank() && userId.isNotBlank()) {
            saveSession(SupabaseSession(access, refresh, userId))
        }
    } }

    private suspend fun authenticate(path: String, email: String, password: String): Result<Unit> = withContext(Dispatchers.IO) { runCatching {
        require(email.contains("@")) { "Geçerli bir e-posta adresi girin." }
        require(password.length >= 8) { "Şifre en az 8 karakter olmalıdır." }
        val response = request(
            "POST", path,
            JSONObject().put("email", email.trim()).put("password", password).toString(),
        )
        val session = response.optJSONObject("session")
        val user = response.optJSONObject("user")
        val access = session?.optString("access_token").orEmpty()
        val refresh = session?.optString("refresh_token").orEmpty()
        val userId = user?.optString("id").orEmpty()
        if (access.isBlank() || refresh.isBlank() || userId.isBlank()) {
            throw IllegalStateException(
                response.optString("msg").ifBlank {
                    response.optString("message").ifBlank { "Hesap doğrulaması bekleniyor veya oturum oluşturulamadı." }
                },
            )
        }
        saveSession(SupabaseSession(access, refresh, userId))
    } }

    suspend fun refresh(): Result<Unit> = withContext(Dispatchers.IO) { runCatching {
        val current = _session.value ?: error("Aktif oturum yok.")
        val response = request(
            "POST", "/auth/v1/token?grant_type=refresh_token",
            JSONObject().put("refresh_token", current.refreshToken).toString(),
        )
        val access = response.optString("access_token")
        require(access.isNotBlank()) { "Oturum yenilenemedi." }
        saveSession(
            SupabaseSession(
                access,
                response.optString("refresh_token").ifBlank { current.refreshToken },
                response.optJSONObject("user")?.optString("id").orEmpty().ifBlank { current.userId },
            ),
        )
    } }

    suspend fun ensureSession(): SupabaseSession? = withContext(Dispatchers.IO) {
        val current = _session.value ?: return@withContext null
        runCatching {
            request("GET", "/auth/v1/user", accessToken = current.accessToken)
            current
        }.getOrElse {
            refresh().getOrNull()
            _session.value
        }
    }

    fun signOut() {
        _session.value = null
        store.clear()
    }

    private fun saveSession(session: SupabaseSession) {
        store.save(session)
        _session.value = session
    }

    private fun request(method: String, path: String, body: String? = null, accessToken: String? = null): JSONObject {
        val connection = (URL(SupabaseConfig.URL + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15_000
            readTimeout = 20_000
            doInput = true
            setRequestProperty("apikey", SupabaseConfig.PUBLISHABLE_KEY)
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Content-Type", "application/json")
            accessToken?.let { setRequestProperty("Authorization", "Bearer " + it) }
            if (body != null) doOutput = true
        }
        try {
            body?.let { connection.outputStream.use { out -> out.write(it.toByteArray(Charsets.UTF_8)) } }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.let {
                BufferedReader(InputStreamReader(it, Charsets.UTF_8)).use(BufferedReader::readText)
            }.orEmpty()
            val json = if (text.isBlank()) JSONObject() else runCatching { JSONObject(text) }.getOrElse {
                JSONObject().put("message", text)
            }
            if (code !in 200..299) {
                throw SupabaseHttpException(code, json.optString("msg").ifBlank { json.optString("message") })
            }
            return json
        } finally {
            connection.disconnect()
        }
    }

    fun rawRequest(method: String, path: String, body: String?, accessToken: String): String {
        val connection = (URL(SupabaseConfig.URL + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15_000
            readTimeout = 20_000
            doInput = true
            setRequestProperty("apikey", SupabaseConfig.PUBLISHABLE_KEY)
            setRequestProperty("Authorization", "Bearer " + accessToken)
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Prefer", "resolution=merge-duplicates,return=minimal")
            if (body != null) doOutput = true
        }
        try {
            body?.let { connection.outputStream.use { out -> out.write(it.toByteArray(Charsets.UTF_8)) } }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.let {
                BufferedReader(InputStreamReader(it, Charsets.UTF_8)).use(BufferedReader::readText)
            }.orEmpty()
            if (code !in 200..299) throw SupabaseHttpException(code, text)
            return text
        } finally {
            connection.disconnect()
        }
    }
}

class SupabaseHttpException(val code: Int, override val message: String) : IllegalStateException("Supabase HTTP " + code + ": " + message)

class SupabaseCrmRemoteDataSource(private val auth: SupabaseAuthClient) : RemoteCrmDataSource {
    override suspend fun apply(operation: SyncOperationEntity): RemoteSyncResult {
        val session = auth.ensureSession() ?: return RemoteSyncResult.NotConfigured
        return runCatching {
            val payload = JSONObject(operation.payloadJson)
            val table = when (operation.entityType) {
                LocalCrmRepository.ENTITY_CUSTOMER -> "lanu_crm_customers"
                LocalCrmRepository.ENTITY_ACTIVITY -> "lanu_crm_activities"
                LocalCrmRepository.ENTITY_NEXT_ACTION -> "lanu_crm_next_actions"
                LocalCrmRepository.ENTITY_OPPORTUNITY -> "lanu_crm_opportunities"
                else -> error("Bilinmeyen CRM entity: " + operation.entityType)
            }
            val row = when (operation.entityType) {
                LocalCrmRepository.ENTITY_CUSTOMER -> customerRow(payload, session.userId)
                LocalCrmRepository.ENTITY_ACTIVITY -> activityRow(payload, session.userId)
                LocalCrmRepository.ENTITY_NEXT_ACTION -> nextActionRow(payload, session.userId)
                LocalCrmRepository.ENTITY_OPPORTUNITY -> opportunityRow(payload, session.userId)
                else -> error("unreachable")
            }
            val remoteVersion = fetchRemoteVersion(table, payload.optString("id"), operation.entityType, session)
            if (remoteVersion != null && operation.entityType != LocalCrmRepository.ENTITY_ACTIVITY &&
                remoteVersion > operation.payloadVersion
            ) {
                return RemoteSyncResult.Conflict(
                    "Uzak kayıt sürümü daha yeni: remote=" + remoteVersion + " local=" + operation.payloadVersion,
                )
            }
            auth.rawRequest("POST", "/rest/v1/" + table + "?on_conflict=id", JSONArray().put(row).toString(), session.accessToken)
            RemoteSyncResult.Success
        }.getOrElse { error ->
            when (error) {
                is SupabaseHttpException -> when {
                    error.code == 401 -> RemoteSyncResult.RetryableFailure("Oturum süresi doldu.")
                    error.code == 409 || error.code == 412 -> RemoteSyncResult.Conflict(error.message)
                    error.code in 408..599 -> RemoteSyncResult.RetryableFailure(error.message)
                    else -> RemoteSyncResult.PermanentFailure(error.message)
                }
                else -> RemoteSyncResult.RetryableFailure(error.message ?: "Bilinmeyen ağ hatası")
            }
        }
    }

    private fun fetchRemoteVersion(
        table: String,
        id: String,
        entityType: String,
        session: SupabaseSession,
    ): Long? {
        if (id.isBlank()) return null
        val column = when (entityType) {
            LocalCrmRepository.ENTITY_CUSTOMER -> "sync_version"
            LocalCrmRepository.ENTITY_NEXT_ACTION,
            LocalCrmRepository.ENTITY_OPPORTUNITY -> "version"
            else -> "id"
        }
        if (column == "id") return null
        val text = auth.rawRequest(
            "GET",
            "/rest/v1/" + table + "?select=" + column + "&id=eq." + id + "&limit=1",
            null,
            session.accessToken,
        )
        val array = JSONArray(text)
        if (array.length() == 0) return null
        return array.getJSONObject(0).optLong(column, 1L)
    }

    override suspend fun pullInto(database: LanuCrmDatabase): RemotePullResult {
        val session = auth.ensureSession() ?: return RemotePullResult.NotConfigured
        return runCatching {
            val customers = fetchAll("lanu_crm_customers", "updated_at", session)
            val activities = fetchAll("lanu_crm_activities", "created_at", session)
            val nextActions = fetchAll("lanu_crm_next_actions", "updated_at", session)
            val opportunities = fetchAll("lanu_crm_opportunities", "updated_at", session)
            val transitions = fetchAll("lanu_crm_stage_transitions", "changed_at", session)

            database.withTransaction {
                customers.forEach { p ->
                    val id = p.getString("id")
                    val remoteVersion = p.optLong("sync_version", 1L)
                    val local = database.customerDao().findById(id)
                    val accept = local == null || remoteVersion > local.version ||
                        local.syncState == SyncState.SYNCED.name
                    if (accept) {
                        database.customerDao().upsert(
                            CrmCustomerEntity(
                                id = id,
                                businessSourceId = p.optString("source_id"),
                                businessName = p.optString("name"),
                                city = p.optString("city"),
                                district = p.optString("district"),
                                neighborhood = p.optString("neighborhood").takeIf(String::isNotBlank),
                                address = p.optString("address").takeIf(String::isNotBlank),
                                latitude = p.optDouble("latitude").takeIf { !p.isNull("latitude") },
                                longitude = p.optDouble("longitude").takeIf { !p.isNull("longitude") },
                                dataQuality = runCatching { DataQuality.valueOf(p.optString("data_quality", "UNKNOWN")) }.getOrDefault(DataQuality.UNKNOWN),
                                stage = p.optString("stage", CrmStage.PROSPECT.name),
                                ownerUserId = session.userId,
                                notes = p.optString("notes").takeIf(String::isNotBlank),
                                createdAtEpochMs = parseInstant(p.optString("created_at")),
                                updatedAtEpochMs = parseInstant(p.optString("updated_at")),
                                version = remoteVersion,
                                syncState = SyncState.SYNCED.name,
                            ),
                        )
                    }
                }

                activities.forEach { p ->
                    val id = p.getString("id")
                    val local = database.activityDao().findById(id)
                    if (local == null || local.syncState == SyncState.SYNCED.name) {
                        database.activityDao().upsert(
                            CrmActivityEntity(
                                id = id,
                                customerId = p.getString("customer_id"),
                                type = p.getString("type"),
                                occurredAtEpochMs = parseInstant(p.optString("occurred_at")),
                                note = p.optString("note").takeIf(String::isNotBlank),
                                createdByUserId = p.optString("owner_user_id").takeIf(String::isNotBlank),
                                createdAtEpochMs = parseInstant(p.optString("created_at")),
                                version = 1L,
                                syncState = SyncState.SYNCED.name,
                            ),
                        )
                    }
                }

                nextActions.forEach { p ->
                    val id = p.getString("id")
                    val remoteVersion = p.optLong("version", 1L)
                    val local = database.nextActionDao().findById(id)
                    if (local == null || remoteVersion > local.version || local.syncState == SyncState.SYNCED.name) {
                        database.nextActionDao().upsert(
                            CrmNextActionEntity(
                                id = id,
                                customerId = p.getString("customer_id"),
                                type = p.getString("type"),
                                dueAtEpochMs = parseInstant(p.optString("due_at")),
                                note = p.optString("note").takeIf(String::isNotBlank),
                                createdByUserId = p.optString("created_by_user_id").takeIf(String::isNotBlank),
                                createdAtEpochMs = parseInstant(p.optString("created_at")),
                                completedAtEpochMs = p.optString("completed_at").takeIf(String::isNotBlank)?.let(::parseInstant),
                                completedByUserId = p.optString("completed_by_user_id").takeIf(String::isNotBlank),
                                version = remoteVersion,
                                syncState = SyncState.SYNCED.name,
                            ),
                        )
                    }
                }

                opportunities.forEach { p ->
                    val id = p.getString("id")
                    val local = database.opportunityDao().findById(id)
                    val remoteVersion = p.optLong("version", 1L)
                    if (local == null || remoteVersion > local.version || local.syncState == SyncState.SYNCED.name) {
                        val amountMinor = p.optString("amount").takeIf(String::isNotBlank)?.let {
                            runCatching { BigDecimal(it).movePointRight(2).longValueExact() }.getOrNull()
                        }
                        database.opportunityDao().upsert(
                            CrmOpportunityEntity(
                                id = id,
                                customerId = p.getString("customer_id"),
                                title = p.optString("title"),
                                status = p.optString("status", CrmOpportunityStatus.OPEN.name),
                                notes = p.optString("note").takeIf(String::isNotBlank),
                                estimatedValueMinor = amountMinor,
                                currency = p.optString("currency").takeIf(String::isNotBlank),
                                valueOrigin = p.optString("amount_origin", CrmValueOrigin.UNKNOWN.name),
                                createdAtEpochMs = parseInstant(p.optString("created_at")),
                                updatedAtEpochMs = parseInstant(p.optString("updated_at")),
                                version = remoteVersion,
                                syncState = SyncState.SYNCED.name,
                            ),
                        )
                    }
                }

                transitions.forEach { p ->
                    database.stageTransitionDao().insert(
                        CrmStageTransitionEntity(
                            id = p.getString("id"),
                            customerId = p.getString("customer_id"),
                            fromStage = p.optString("from_stage").takeIf(String::isNotBlank),
                            toStage = p.optString("to_stage"),
                            changedAtEpochMs = parseInstant(p.optString("changed_at")),
                            changedByUserId = p.optString("changed_by_user_id").takeIf(String::isNotBlank),
                            clientVersion = p.optLong("client_version", 1L),
                        ),
                    )
                }
            }
            RemotePullResult.Success
        }.getOrElse {
            RemotePullResult.RetryableFailure(it.message ?: "Uzak CRM verisi alınamadı.")
        }
    }

    private fun fetchAll(table: String, orderColumn: String, session: SupabaseSession): List<JSONObject> {
        val result = mutableListOf<JSONObject>()
        var offset = 0
        val pageSize = 500
        while (true) {
            val text = auth.rawRequest(
                "GET",
                "/rest/v1/" + table + "?select=*&owner_user_id=eq." + session.userId +
                    "&order=" + orderColumn + ".asc&limit=" + pageSize + "&offset=" + offset,
                null,
                session.accessToken,
            )
            val page = JSONArray(text)
            for (index in 0 until page.length()) result += page.getJSONObject(index)
            if (page.length() < pageSize) return result
            offset += pageSize
        }
    }

    private fun parseInstant(value: String): Long =
        runCatching { java.time.Instant.parse(value).toEpochMilli() }.getOrDefault(System.currentTimeMillis())

    private fun customerRow(p: JSONObject, userId: String) = JSONObject().apply {
        put("id", p.getString("id"))
        put("owner_user_id", userId)
        put("stage", p.getString("stage"))
        put("source", if (p.optString("businessSourceId").startsWith("manual:")) "manual" else "osm")
        put("source_id", p.getString("businessSourceId"))
        put("name", p.getString("businessName"))
        put("city", p.getString("city"))
        put("district", p.getString("district"))
        put("neighborhood", p.optString("neighborhood").takeIf(String::isNotBlank) ?: JSONObject.NULL)
        put("address", p.optString("address").takeIf(String::isNotBlank) ?: JSONObject.NULL)
        put("latitude", if (p.isNull("latitude")) JSONObject.NULL else p.getDouble("latitude"))
        put("longitude", if (p.isNull("longitude")) JSONObject.NULL else p.getDouble("longitude"))
        put("data_quality", p.optString("dataQuality").ifBlank { "UNKNOWN" })
        put("notes", p.optString("notes").takeIf(String::isNotBlank) ?: JSONObject.NULL)
        put("sync_version", p.optLong("version", 1L))
    }

    private fun activityRow(p: JSONObject, userId: String) = JSONObject().apply {
        put("id", p.getString("id"))
        put("owner_user_id", userId)
        put("customer_id", p.getString("customerId"))
        put("type", p.getString("type"))
        put("note", p.optString("note").takeIf(String::isNotBlank) ?: JSONObject.NULL)
        put("occurred_at", epochToIso(p.getLong("occurredAtEpochMs")))
        put("created_at", epochToIso(p.getLong("createdAtEpochMs")))
    }

    private fun nextActionRow(p: JSONObject, userId: String) = JSONObject().apply {
        put("id", p.getString("id"))
        put("owner_user_id", userId)
        put("customer_id", p.getString("customerId"))
        put("type", p.getString("type"))
        put("due_at", epochToIso(p.getLong("dueAtEpochMs")))
        put("note", p.optString("note").takeIf(String::isNotBlank) ?: JSONObject.NULL)
        put("created_by_user_id", p.optString("createdByUserId").takeIf(String::isNotBlank) ?: userId)
        put("completed_at", p.optLong("completedAtEpochMs", 0L).takeIf { it > 0 }?.let(::epochToIso) ?: JSONObject.NULL)
        put("completed_by_user_id", p.optString("completedByUserId").takeIf(String::isNotBlank) ?: JSONObject.NULL)
        put("version", p.optLong("version", 1L))
    }

    private fun opportunityRow(p: JSONObject, userId: String) = JSONObject().apply {
        put("id", p.getString("id"))
        put("owner_user_id", userId)
        put("customer_id", p.getString("customerId"))
        put("title", p.getString("title"))
        put("status", p.getString("status"))
        val minor = if (p.isNull("estimatedValueMinor")) null else p.optLong("estimatedValueMinor")
        put("amount", minor?.let { BigDecimal(it).movePointLeft(2).toPlainString() } ?: JSONObject.NULL)
        put("currency", p.optString("currency").takeIf(String::isNotBlank) ?: JSONObject.NULL)
        put("amount_origin", p.optString("valueOrigin").ifBlank { "UNKNOWN" })
        put("note", p.optString("notes").takeIf(String::isNotBlank) ?: JSONObject.NULL)
        put("created_at", epochToIso(p.getLong("createdAtEpochMs")))
        put("updated_at", epochToIso(p.getLong("updatedAtEpochMs")))
        put("version", p.optLong("version", 1L))
    }

    private fun epochToIso(epochMs: Long): String = java.time.Instant.ofEpochMilli(epochMs).toString()
}

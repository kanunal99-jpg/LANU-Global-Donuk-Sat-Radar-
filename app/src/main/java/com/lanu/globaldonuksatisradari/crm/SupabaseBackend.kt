package com.lanu.globaldonuksatisradari.crm

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
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

    suspend fun signUp(email: String, password: String): Result<Unit> =
        authenticate("/auth/v1/signup", email, password)

    private suspend fun authenticate(path: String, email: String, password: String): Result<Unit> = runCatching {
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
    }

    suspend fun refresh(): Result<Unit> = runCatching {
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
    }

    suspend fun ensureSession(): SupabaseSession? {
        val current = _session.value ?: return null
        return runCatching {
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

    private fun customerRow(p: JSONObject, userId: String) = JSONObject().apply {
        put("id", p.getString("id"))
        put("owner_user_id", userId)
        put("business_id", JSONObject.NULL)
        put("stage", p.getString("stage"))
        put("source", "osm")
        put("source_id", p.getString("businessSourceId"))
        put("name", p.getString("businessName"))
        put("city", p.getString("city"))
        put("district", p.getString("district"))
        put("neighborhood", p.optString("neighborhood").takeIf(String::isNotBlank) ?: JSONObject.NULL)
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
    }

    private fun epochToIso(epochMs: Long): String = java.time.Instant.ofEpochMilli(epochMs).toString()
}

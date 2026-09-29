package com.lanu.globaldonuksatisradari.crm

import org.json.JSONObject
import java.math.BigDecimal
import java.time.Instant

/** Semantic comparison for fields the client is about to upsert into Supabase. */
internal object SupabaseRowEquivalence {
    fun matchesOutgoing(remote: JSONObject, outgoing: JSONObject): Boolean {
        val keys = outgoing.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            if (!remote.has(key)) return false
            if (!equivalent(remote.opt(key), outgoing.opt(key))) return false
        }
        return true
    }

    private fun equivalent(left: Any?, right: Any?): Boolean {
        val leftNull = left == null || left === JSONObject.NULL
        val rightNull = right == null || right === JSONObject.NULL
        if (leftNull || rightNull) return leftNull && rightNull
        if (left == right) return true

        // PostgREST can deserialize a numeric DB value as Number while the client intentionally
        // serializes the same value as a decimal string (for example NUMERIC money fields).
        // Do not coerce two strings: SKU/phone/source IDs such as "001" and "1" are distinct.
        if (left is Number || right is Number) {
            numeric(left)?.let { l ->
                numeric(right)?.let { r -> return l.compareTo(r) == 0 }
            }
        }

        if (left is String && right is String) {
            val leftInstant = runCatching { Instant.parse(left) }.getOrNull()
            val rightInstant = runCatching { Instant.parse(right) }.getOrNull()
            if (leftInstant != null && rightInstant != null) return leftInstant == rightInstant
        }

        return left.toString() == right.toString()
    }

    private fun numeric(value: Any): BigDecimal? = when (value) {
        is Number -> runCatching { BigDecimal(value.toString()) }.getOrNull()
        is String -> runCatching { BigDecimal(value) }.getOrNull()
        else -> null
    }
}

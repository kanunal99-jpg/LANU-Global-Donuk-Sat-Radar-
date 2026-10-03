package com.lanu.globaldonuksatisradari.data

/**
 * Shared tolerant text matching for human-entered business searches.
 *
 * A user searching "sandora cafe" must match "Sandora Fast Food & Cafe".
 * Matching all normalized tokens avoids the old brittle whole-phrase substring rule
 * while still requiring every meaningful query token to be present.
 */
object BusinessTextSearch {
    fun matches(query: String, vararg fields: String?): Boolean {
        val tokens = tokens(query)
        if (tokens.isEmpty()) return true
        val haystack = tokens(fields.filterNotNull().joinToString(" "))
            .toSet()
        return tokens.all(haystack::contains)
    }

    fun flexibleRegex(query: String): String {
        val rawTokens = query
            .trim()
            .split(Regex("\\s+"))
            .map(String::trim)
            .filter(String::isNotEmpty)
        if (rawTokens.isEmpty()) return ""
        return rawTokens.joinToString(".*") { escapeRegex(it) }
    }

    internal fun tokens(value: String): List<String> =
        BusinessDeduplication.normalizeForComparison(value)
            .split(Regex("[^a-z0-9]+"))
            .filter { it.isNotBlank() }

    private fun escapeRegex(value: String): String = buildString {
        value.forEach { char ->
            when (char) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '.', '^', '$', '|', '?', '*', '+', '(', ')', '[', ']', '{', '}' -> {
                    append('\\')
                    append(char)
                }
                else -> append(char)
            }
        }
    }
}

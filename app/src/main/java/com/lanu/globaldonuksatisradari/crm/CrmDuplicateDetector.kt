package com.lanu.globaldonuksatisradari.crm

import java.util.Locale
import kotlin.math.min

data class CrmDuplicateCandidate(
    val first: CrmCustomer,
    val second: CrmCustomer,
    val score: Int,
    val reasons: List<String>,
)

object CrmDuplicateDetector {
    fun find(customers: List<CrmCustomer>, limit: Int = 100): List<CrmDuplicateCandidate> {
        if (customers.size < 2) return emptyList()

        val active = customers.filter { it.mergedIntoCustomerId.isNullOrBlank() }
        val byId = active.associateBy { it.id }
        val candidatePairs = linkedSetOf<Pair<String, String>>()
        val blocks = mutableMapOf<String, MutableList<String>>()

        active.forEach { customer ->
            blockingKeys(customer).forEach { key ->
                blocks.getOrPut(key) { mutableListOf() }.add(customer.id)
            }
        }

        blocks.values.forEach { ids ->
            val unique = ids.distinct().take(100)
            for (i in 0 until unique.size) {
                for (j in i + 1 until unique.size) {
                    val a = unique[i]
                    val b = unique[j]
                    candidatePairs += if (a < b) a to b else b to a
                }
            }
        }

        return candidatePairs.asSequence()
            .mapNotNull { (aId, bId) ->
                val a = byId[aId] ?: return@mapNotNull null
                val b = byId[bId] ?: return@mapNotNull null
                if (a.ownerUserId != b.ownerUserId) return@mapNotNull null
                score(a, b)
            }
            .filter { it.score >= 70 }
            .sortedWith(
                compareByDescending<CrmDuplicateCandidate> { it.score }
                    .thenBy { it.first.businessName }
                    .thenBy { it.second.businessName },
            )
            .take(limit)
            .toList()
    }

    private fun score(a: CrmCustomer, b: CrmCustomer): CrmDuplicateCandidate? {
        var score = 0
        val reasons = mutableListOf<String>()

        val registryA = identity(a.registryNumber)
        val registryB = identity(b.registryNumber)
        if (registryA != null && registryA == registryB) {
            score += 100
            reasons += "Aynı sicil/MERSİS"
        }

        val taxA = digits(a.taxOrNationalId)
        val taxB = digits(b.taxOrNationalId)
        if (taxA != null && taxA == taxB) {
            score += 100
            reasons += "Aynı VKN/TCKN"
        }

        val phoneA = phone(a.phone)
        val phoneB = phone(b.phone)
        if (phoneA != null && phoneA == phoneB) {
            score += 60
            reasons += "Aynı telefon"
        }

        val nameA = normalize(a.signboardName ?: a.businessName)
        val nameB = normalize(b.signboardName ?: b.businessName)
        val similarity = tokenSimilarity(nameA, nameB)
        when {
            nameA.isNotBlank() && nameA == nameB -> {
                score += 45
                reasons += "Aynı işletme/tabela adı"
            }
            similarity >= 0.8 -> {
                score += 35
                reasons += "Çok benzer işletme adı"
            }
            similarity >= 0.6 -> {
                score += 20
                reasons += "Benzer işletme adı"
            }
        }

        val addressA = normalize(a.address.orEmpty())
        val addressB = normalize(b.address.orEmpty())
        if (addressA.isNotBlank() && addressA == addressB) {
            score += 25
            reasons += "Aynı açık adres"
        }

        if (a.latitude != null && a.longitude != null && b.latitude != null && b.longitude != null) {
            val distance = CrmRoutePlanner.distanceKm(
                a.latitude,
                a.longitude,
                b.latitude,
                b.longitude,
            )
            when {
                distance <= 0.10 -> {
                    score += 35
                    reasons += "100 m içinde"
                }
                distance <= 0.30 -> {
                    score += 20
                    reasons += "300 m içinde"
                }
            }
        }

        if (!a.city.equals(b.city, ignoreCase = true)) score -= 80
        if (!a.district.equals(b.district, ignoreCase = true)) score -= 20

        return if (reasons.isEmpty()) null else CrmDuplicateCandidate(a, b, score, reasons)
    }

    private fun blockingKeys(customer: CrmCustomer): Set<String> = buildSet {
        identity(customer.registryNumber)?.let { add("r:$it") }
        digits(customer.taxOrNationalId)?.let { add("t:$it") }
        phone(customer.phone)?.let { add("p:$it") }
        val name = normalize(customer.signboardName ?: customer.businessName)
        if (name.isNotBlank()) {
            add("n:${normalize(customer.city)}:${normalize(customer.district)}:${name.take(24)}")
            name.split(' ').filter { it.length >= 4 }.take(3).forEach {
                add("w:${normalize(customer.city)}:${normalize(customer.district)}:$it")
            }
        }
    }

    private fun tokenSimilarity(a: String, b: String): Double {
        if (a.isBlank() || b.isBlank()) return 0.0
        val aa = a.split(' ').filter(String::isNotBlank).toSet()
        val bb = b.split(' ').filter(String::isNotBlank).toSet()
        if (aa.isEmpty() || bb.isEmpty()) return 0.0
        val intersection = aa.intersect(bb).size.toDouble()
        val union = aa.union(bb).size.toDouble()
        return if (union == 0.0) 0.0 else intersection / union
    }

    private fun identity(value: String?): String? =
        value?.filter(Char::isLetterOrDigit)?.uppercase(Locale.ROOT)?.takeIf(String::isNotBlank)

    private fun digits(value: String?): String? =
        value?.filter(Char::isDigit)?.takeIf { it.length >= 10 }

    private fun phone(value: String?): String? {
        val digits = value?.filter(Char::isDigit).orEmpty()
        if (digits.length < 10) return null
        return digits.takeLast(min(10, digits.length))
    }

    private fun normalize(value: String): String =
        value.trim()
            .replace('İ', 'I')
            .lowercase(Locale.ROOT)
            .replace("\u0307", "")
            .replace('ı', 'i')
            .replace('ğ', 'g')
            .replace('ü', 'u')
            .replace('ş', 's')
            .replace('ö', 'o')
            .replace('ç', 'c')
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()
}

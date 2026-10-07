package com.lanu.globaldonuksatisradari.crm

import java.util.Locale
import kotlin.math.floor

data class CrmDuplicateCandidate(
    val first: CrmCustomer,
    val second: CrmCustomer,
    val score: Int,
    val reasons: List<String>,
)

object CrmDuplicateDetector {
    private const val MAX_WEAK_BLOCK_SIZE = 40
    private const val MAX_CANDIDATE_PAIRS = 30_000
    private const val GRID_SIZE_DEGREES = 0.0025

    fun find(customers: List<CrmCustomer>, limit: Int = 100): List<CrmDuplicateCandidate> {
        if (customers.size < 2 || limit <= 0 || cancelled()) return emptyList()

        val active = ArrayList<CrmCustomer>(customers.size)
        for (customer in customers) {
            if (cancelled()) return emptyList()
            if (customer.mergedIntoCustomerId.isNullOrBlank()) active += customer
        }
        if (active.size < 2) return emptyList()

        val byId = active.associateBy { it.id }
        val candidatePairs = linkedSetOf<Pair<String, String>>()

        val registryBlocks = mutableMapOf<String, MutableList<String>>()
        val taxBlocks = mutableMapOf<String, MutableList<String>>()
        val phoneBlocks = mutableMapOf<String, MutableList<String>>()
        val exactNameBlocks = mutableMapOf<String, MutableList<String>>()
        val prefixBlocks = mutableMapOf<String, MutableList<String>>()
        val gridBlocks = mutableMapOf<String, MutableList<String>>()

        active.forEach { customer ->
            if (cancelled()) return emptyList()
            identity(customer.registryNumber)?.let { registryBlocks.add(it, customer.id) }
            digits(customer.taxOrNationalId)?.let { taxBlocks.add(it, customer.id) }
            phone(customer.phone)?.let { phoneBlocks.add(it, customer.id) }

            val city = normalize(customer.city)
            val district = normalize(customer.district)
            val name = normalize(customer.signboardName ?: customer.businessName)
            if (name.length >= 4) {
                exactNameBlocks.add("$city|$district|$name", customer.id)
                prefixBlocks.add("$city|$district|${name.take(12)}", customer.id)
            }

            val lat = customer.latitude
            val lon = customer.longitude
            if (lat != null && lon != null && lat in -90.0..90.0 && lon in -180.0..180.0) {
                val latCell = floor(lat / GRID_SIZE_DEGREES).toInt()
                val lonCell = floor(lon / GRID_SIZE_DEGREES).toInt()
                for (latOffset in -1..1) {
                    for (lonOffset in -1..1) {
                        gridBlocks.add(
                            "$city|$district|${latCell + latOffset}|${lonCell + lonOffset}",
                            customer.id,
                        )
                    }
                }
            }
        }

        fun consume(blocks: Map<String, List<String>>, weak: Boolean): Boolean {
            for (ids in blocks.values) {
                if (cancelled()) return false
                if (candidatePairs.size >= MAX_CANDIDATE_PAIRS) return true
                val unique = ids.distinct()
                if (unique.size < 2) continue
                if (weak && unique.size > MAX_WEAK_BLOCK_SIZE) continue

                val capped = if (weak) unique else unique.take(100)
                for (i in 0 until capped.lastIndex) {
                    if (cancelled()) return false
                    for (j in i + 1 until capped.size) {
                        if (cancelled()) return false
                        val a = capped[i]
                        val b = capped[j]
                        candidatePairs += if (a < b) a to b else b to a
                        if (candidatePairs.size >= MAX_CANDIDATE_PAIRS) return true
                    }
                }
            }
            return true
        }

        // Güçlü kimlikler önce işlenir; yeni kayıtlar kalabalık isim blokları yüzünden kaybolmaz.
        if (!consume(registryBlocks, weak = false)) return emptyList()
        if (!consume(taxBlocks, weak = false)) return emptyList()
        if (!consume(phoneBlocks, weak = false)) return emptyList()
        if (!consume(exactNameBlocks, weak = true)) return emptyList()
        if (!consume(prefixBlocks, weak = true)) return emptyList()
        if (!consume(gridBlocks, weak = true)) return emptyList()
        if (cancelled()) return emptyList()

        return candidatePairs.asSequence()
            .takeWhile { !cancelled() }
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

    private fun cancelled(): Boolean = Thread.currentThread().isInterrupted

    private fun <K> MutableMap<K, MutableList<String>>.add(key: K, customerId: String) {
        getOrPut(key) { mutableListOf() }.add(customerId)
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
        return digits.takeLast(10)
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

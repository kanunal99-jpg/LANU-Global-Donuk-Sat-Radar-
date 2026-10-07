package com.lanu.globaldonuksatisradari.crm

import java.util.Locale
import kotlin.math.min

data class CrmDuplicateCandidate(
    val first: CrmCustomer,
    val second: CrmCustomer,
    val score: Int,
    val reasons: List<String>,
)

private data class DuplicateFingerprint(
    val customer: CrmCustomer,
    val city: String,
    val district: String,
    val name: String,
    val address: String,
    val registry: String?,
    val taxId: String?,
    val phone: String?,
)

object CrmDuplicateDetector {
    private const val MAX_STRONG_BUCKET_SIZE = 50
    private const val MAX_WEAK_BUCKET_SIZE = 30
    private const val MAX_WEAK_PAIRS = 20_000

    fun find(customers: List<CrmCustomer>, limit: Int = 100): List<CrmDuplicateCandidate> {
        if (customers.size < 2 || limit <= 0) return emptyList()

        val fingerprints = customers.asSequence()
            .filter { it.mergedIntoCustomerId.isNullOrBlank() }
            .map(::fingerprint)
            .toList()
        if (fingerprints.size < 2) return emptyList()

        val byId = fingerprints.associateBy { it.customer.id }
        val strongBlocks = mutableMapOf<String, MutableList<String>>()
        val weakBlocks = mutableMapOf<String, MutableList<String>>()

        fingerprints.forEach { fp ->
            strongKeys(fp).forEach { key ->
                strongBlocks.getOrPut(key) { mutableListOf() }.add(fp.customer.id)
            }
            weakKeys(fp).forEach { key ->
                weakBlocks.getOrPut(key) { mutableListOf() }.add(fp.customer.id)
            }
        }

        val candidatePairs = LinkedHashSet<Pair<String, String>>()
        strongBlocks.values.forEach { ids ->
            addPairs(
                ids = ids,
                maxBucketSize = MAX_STRONG_BUCKET_SIZE,
                output = candidatePairs,
                pairBudget = Int.MAX_VALUE,
            )
        }

        var weakPairBudget = MAX_WEAK_PAIRS
        weakBlocks.values.forEach { ids ->
            if (weakPairBudget <= 0) return@forEach
            if (ids.size !in 2..MAX_WEAK_BUCKET_SIZE) return@forEach
            val before = candidatePairs.size
            addPairs(
                ids = ids,
                maxBucketSize = MAX_WEAK_BUCKET_SIZE,
                output = candidatePairs,
                pairBudget = weakPairBudget,
            )
            weakPairBudget -= candidatePairs.size - before
        }

        return candidatePairs.asSequence()
            .mapNotNull { (aId, bId) ->
                val a = byId[aId] ?: return@mapNotNull null
                val b = byId[bId] ?: return@mapNotNull null
                if (a.customer.ownerUserId != b.customer.ownerUserId) return@mapNotNull null
                if (a.city != b.city) return@mapNotNull null
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

    private fun addPairs(
        ids: List<String>,
        maxBucketSize: Int,
        output: MutableSet<Pair<String, String>>,
        pairBudget: Int,
    ) {
        if (ids.size < 2 || pairBudget <= 0) return
        val unique = ids.asSequence().distinct().take(maxBucketSize).toList()
        var added = 0
        loop@ for (i in 0 until unique.size) {
            for (j in i + 1 until unique.size) {
                if (added >= pairBudget) break@loop
                val a = unique[i]
                val b = unique[j]
                val pair = if (a < b) a to b else b to a
                if (output.add(pair)) added++
            }
        }
    }

    private fun score(a: DuplicateFingerprint, b: DuplicateFingerprint): CrmDuplicateCandidate? {
        var score = 0
        val reasons = mutableListOf<String>()

        if (a.registry != null && a.registry == b.registry) {
            score += 100
            reasons += "Aynı sicil/MERSİS"
        }

        if (a.taxId != null && a.taxId == b.taxId) {
            score += 100
            reasons += "Aynı VKN/TCKN"
        }

        if (a.phone != null && a.phone == b.phone) {
            score += 60
            reasons += "Aynı telefon"
        }

        val similarity = tokenSimilarity(a.name, b.name)
        when {
            a.name.isNotBlank() && a.name == b.name -> {
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

        if (a.address.isNotBlank() && a.address == b.address) {
            score += 25
            reasons += "Aynı açık adres"
        }

        val ac = a.customer
        val bc = b.customer
        if (ac.latitude != null && ac.longitude != null && bc.latitude != null && bc.longitude != null) {
            val distance = CrmRoutePlanner.distanceKm(
                ac.latitude,
                ac.longitude,
                bc.latitude,
                bc.longitude,
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

        if (a.district != b.district) score -= 20

        return if (reasons.isEmpty()) null else {
            CrmDuplicateCandidate(a.customer, b.customer, score, reasons)
        }
    }

    private fun fingerprint(customer: CrmCustomer): DuplicateFingerprint = DuplicateFingerprint(
        customer = customer,
        city = normalize(customer.city),
        district = normalize(customer.district),
        name = normalize(customer.signboardName ?: customer.businessName),
        address = normalize(customer.address.orEmpty()),
        registry = identity(customer.registryNumber),
        taxId = digits(customer.taxOrNationalId),
        phone = phone(customer.phone),
    )

    private fun strongKeys(fp: DuplicateFingerprint): Set<String> = buildSet {
        fp.registry?.let { add("r:$it") }
        fp.taxId?.let { add("t:$it") }
        fp.phone?.let { add("p:$it") }
        if (fp.name.isNotBlank()) {
            add("n:${fp.city}:${fp.district}:${fp.name}")
        }
    }

    private fun weakKeys(fp: DuplicateFingerprint): Set<String> = buildSet {
        if (fp.name.isBlank()) return@buildSet
        fp.name.split(' ')
            .asSequence()
            .filter { it.length >= 5 }
            .filterNot { it in COMMON_NAME_TOKENS }
            .distinct()
            .take(3)
            .forEach { token ->
                add("w:${fp.city}:${fp.district}:$token")
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

    private val COMMON_NAME_TOKENS = setOf(
        "market",
        "gida",
        "ticaret",
        "sanayi",
        "limited",
        "sirketi",
        "anonim",
        "restoran",
        "restaurant",
        "magaza",
        "sube",
    )
}

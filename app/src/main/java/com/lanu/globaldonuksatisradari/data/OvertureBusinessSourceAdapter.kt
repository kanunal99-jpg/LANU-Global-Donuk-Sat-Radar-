package com.lanu.globaldonuksatisradari.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.zip.GZIPInputStream

object OvertureBusinessSource {
    const val MANIFEST_URL =
        "https://github.com/kanunal99-jpg/LANU-Global-Donuk-Sat-Radar-/releases/download/business-directory-latest/manifest.json"
    const val ASSET_BASE_URL =
        "https://github.com/kanunal99-jpg/LANU-Global-Donuk-Sat-Radar-/releases/download/business-directory-latest"

    private const val SOURCE_POLICY_REVIEWED_AT = 1790985600000L

    val descriptor = DataSourceDescriptor(
        id = "overture-places",
        name = "Overture Maps Places",
        publisher = "Overture Maps Foundation",
        licenseOrTerms = "https://docs.overturemaps.org/attribution/",
        sourceUrl = "https://docs.overturemaps.org/guides/places/",
        lastVerifiedAtEpochMs = SOURCE_POLICY_REVIEWED_AT,
    )

    val contract = BusinessSourceContract(
        descriptor = descriptor,
        accessMethod = SourceAccessMethod.API,
        scope = "Türkiye sınırları içindeki sektör bağımsız Overture Places işyeri/POI envanteri; il bazlı sıkıştırılmış LANU snapshot dosyaları",
        permittedUseVerified = true,
        supportsBulk = true,
        fieldNames = setOf(
            "name",
            "city",
            "district",
            "neighborhood",
            "latitude",
            "longitude",
            "category",
            "address",
            "phone",
            "website",
            "operating_status",
            "confidence",
        ),
    )
}

internal data class OvertureDirectoryCityFile(
    val city: String,
    val regionCode: String,
    val asset: String,
    val sha256: String,
    val recordCount: Int,
)

internal data class OvertureDirectoryManifest(
    val schemaVersion: Int,
    val overtureRelease: String,
    val generatedAtEpochMs: Long,
    val cities: List<OvertureDirectoryCityFile>,
)

internal data class OvertureDirectoryRecord(
    val id: String,
    val name: String,
    val city: String,
    val district: String?,
    val neighborhood: String?,
    val address: String?,
    val latitude: Double?,
    val longitude: Double?,
    val basicCategory: String?,
    val category: String?,
    val topLevelCategory: String?,
    val phone: String?,
    val website: String?,
    val operatingStatus: String?,
    val confidence: Double?,
)

internal object OvertureDirectoryParser {
    fun parseManifest(payload: String): OvertureDirectoryManifest {
        val root = JSONObject(payload)
        val schemaVersion = root.getInt("schemaVersion")
        require(schemaVersion == 1) { "Desteklenmeyen Overture manifest sürümü: " + schemaVersion }
        val generatedAtEpochMs = root.getLong("generatedAtEpochMs")
        require(generatedAtEpochMs > 0L) { "Overture manifest zamanı geçersiz." }
        val overtureRelease = root.getString("overtureRelease").trim()
        require(overtureRelease.isNotEmpty()) { "Overture release bilgisi boş." }

        val cityArray = root.getJSONArray("cities")
        val cities = buildList {
            for (index in 0 until cityArray.length()) {
                val item = cityArray.getJSONObject(index)
                val city = item.getString("city").trim()
                val regionCode = item.getString("regionCode").trim()
                val asset = item.getString("asset").trim()
                val sha256 = item.getString("sha256").trim().lowercase()
                val recordCount = item.getInt("recordCount")
                require(city.isNotEmpty()) { "Manifest şehir adı boş." }
                require(regionCode.matches(Regex("^TR-[0-9]{2}$"))) {
                    "Manifest bölge kodu geçersiz: " + regionCode
                }
                require(asset.matches(Regex("^[A-Za-z0-9._-]+$"))) {
                    "Manifest asset adı güvenli değil: " + asset
                }
                require(sha256.matches(Regex("^[a-f0-9]{64}$"))) {
                    "Manifest SHA-256 geçersiz: " + asset
                }
                require(recordCount >= 0) { "Manifest kayıt sayısı negatif olamaz." }
                add(
                    OvertureDirectoryCityFile(
                        city = city,
                        regionCode = regionCode,
                        asset = asset,
                        sha256 = sha256,
                        recordCount = recordCount,
                    ),
                )
            }
        }
        require(cities.distinctBy { it.regionCode }.size == cities.size) {
            "Manifest aynı bölge kodunu birden fazla içeriyor."
        }
        return OvertureDirectoryManifest(
            schemaVersion = schemaVersion,
            overtureRelease = overtureRelease,
            generatedAtEpochMs = generatedAtEpochMs,
            cities = cities,
        )
    }

    fun parseRecord(line: String): OvertureDirectoryRecord? {
        if (line.isBlank()) return null
        val item = JSONObject(line)
        val id = item.optString("id").trim()
        val name = item.optString("name").trim()
        val city = item.optString("city").trim()
        if (id.isBlank() || name.isBlank() || city.isBlank()) return null

        val latitude = item.optDouble("latitude").takeUnless(Double::isNaN)
        val longitude = item.optDouble("longitude").takeUnless(Double::isNaN)
        if (latitude != null && latitude !in -90.0..90.0) return null
        if (longitude != null && longitude !in -180.0..180.0) return null

        return OvertureDirectoryRecord(
            id = id,
            name = name,
            city = city,
            district = optionalString(item, "district"),
            neighborhood = optionalString(item, "neighborhood"),
            address = optionalString(item, "address"),
            latitude = latitude,
            longitude = longitude,
            basicCategory = optionalString(item, "basicCategory"),
            category = optionalString(item, "category"),
            topLevelCategory = optionalString(item, "topLevelCategory"),
            phone = optionalString(item, "phone"),
            website = optionalString(item, "website"),
            operatingStatus = optionalString(item, "operatingStatus"),
            confidence = item.optDouble("confidence").takeUnless(Double::isNaN),
        )
    }

    fun toVerifiedBusiness(
        record: OvertureDirectoryRecord,
        verifiedAtEpochMs: Long,
    ): VerifiedBusiness? {
        val category = record.basicCategory
            ?: record.category
            ?: record.topLevelCategory
        val business = VerifiedBusiness(
            id = record.id,
            name = record.name,
            city = record.city,
            district = record.district ?: "Bilinmiyor",
            neighborhood = record.neighborhood,
            source = OvertureBusinessSource.descriptor,
            verifiedAtEpochMs = verifiedAtEpochMs,
            latitude = record.latitude,
            longitude = record.longitude,
            category = category,
            address = record.address,
            phone = record.phone,
            website = record.website,
        )
        return VerifiedBusinessValidator.validate(business).getOrNull()
    }

    fun matches(
        record: OvertureDirectoryRecord,
        query: String,
        district: String?,
        neighborhood: String?,
    ): Boolean {
        val wantedDistrict = district
            ?.takeUnless { it.isBlank() || it.equals("Tümü", ignoreCase = true) }
            ?.let(::normalize)
        if (wantedDistrict != null && normalize(record.district.orEmpty()) != wantedDistrict) return false

        val wantedNeighborhood = neighborhood
            ?.takeUnless { it.isBlank() || it.equals("Tümü", ignoreCase = true) }
            ?.let(::normalizeNeighborhood)
        if (wantedNeighborhood != null &&
            normalizeNeighborhood(record.neighborhood.orEmpty()) != wantedNeighborhood
        ) {
            return false
        }

        val wantedQuery = normalize(query)
        if (wantedQuery.isBlank()) return true
        val haystack = listOfNotNull(
            record.name,
            record.basicCategory,
            record.category,
            record.topLevelCategory,
            record.address,
        ).joinToString(" ")
        return normalize(haystack).contains(wantedQuery)
    }

    private fun optionalString(item: JSONObject, key: String): String? =
        item.optString(key).trim().takeIf { it.isNotBlank() && it != "null" }

    private fun normalize(value: String): String =
        BusinessDeduplication.normalizeForComparison(
            value.replace('_', ' ').replace('-', ' '),
        )

    private fun normalizeNeighborhood(value: String): String =
        normalize(value)
            .removeSuffix(" mahallesi")
            .removeSuffix(" mah")
            .trim()
}

class OvertureBusinessSourceAdapter(
    context: Context,
    private val nowEpochMs: () -> Long = { System.currentTimeMillis() },
    private val manifestUrlProvider: () -> String = { OvertureBusinessSource.MANIFEST_URL },
    private val assetBaseUrlProvider: () -> String = { OvertureBusinessSource.ASSET_BASE_URL },
) : BusinessSourceAdapter {
    override val contract: BusinessSourceContract = OvertureBusinessSource.contract

    private val cacheDirectory = File(
        context.applicationContext.filesDir,
        CACHE_DIRECTORY,
    ).apply { mkdirs() }

    override suspend fun fetch(
        query: String,
        city: String,
        district: String?,
        neighborhood: String?,
    ): List<VerifiedBusiness> = withContext(Dispatchers.IO) {
        contract.validate().getOrElse { error ->
            throw IllegalStateException("Overture kaynak sözleşmesi geçersiz.", error)
        }
        require(city.isNotBlank()) { "Şehir boş olamaz." }

        val manifest = loadManifest()
        val cityFile = manifest.cities.firstOrNull {
            BusinessDeduplication.normalizeForComparison(it.city) ==
                BusinessDeduplication.normalizeForComparison(city)
        } ?: throw IOException("Overture snapshot içinde şehir bulunamadı: " + city)

        val localFile = ensureCityFile(cityFile)
        readCityFile(
            file = localFile,
            query = query,
            district = district,
            neighborhood = neighborhood,
            verifiedAtEpochMs = manifest.generatedAtEpochMs,
        )
    }

    private fun loadManifest(): OvertureDirectoryManifest {
        val manifestFile = File(cacheDirectory, MANIFEST_CACHE_FILE)
        val isFresh = manifestFile.exists() &&
            nowEpochMs() - manifestFile.lastModified() in 0L..MANIFEST_TTL_MS

        if (!isFresh) {
            runCatching {
                downloadText(
                    url = manifestUrlProvider(),
                    maxBytes = MAX_MANIFEST_BYTES,
                )
            }.onSuccess { payload ->
                OvertureDirectoryParser.parseManifest(payload)
                writeTextAtomically(manifestFile, payload)
            }.onFailure { error ->
                Log.w(LOG_TAG, "Overture manifest yenilenemedi; önbellek deneniyor.", error)
            }
        }

        if (!manifestFile.exists()) {
            throw IOException("Overture manifest indirilemedi ve yerel önbellek yok.")
        }
        return OvertureDirectoryParser.parseManifest(manifestFile.readText(Charsets.UTF_8))
    }

    private fun ensureCityFile(entry: OvertureDirectoryCityFile): File {
        val target = File(cacheDirectory, entry.asset)
        if (target.exists() && sha256(target).equals(entry.sha256, ignoreCase = true)) {
            return target
        }
        if (target.exists() && !target.delete()) {
            throw IOException("Geçersiz Overture önbelleği silinemedi: " + target.name)
        }

        val temp = File(cacheDirectory, entry.asset + ".tmp")
        if (temp.exists()) temp.delete()

        val connection = (URL(assetBaseUrlProvider().trimEnd('/') + "/" + entry.asset)
            .openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 20_000
            readTimeout = 120_000
            instanceFollowRedirects = true
            setRequestProperty("Accept", "application/gzip, application/octet-stream")
            setRequestProperty(
                "User-Agent",
                "LANU-Global-Donuk-Satis-Radari/0.4 (+https://github.com/kanunal99-jpg/LANU-Global-Donuk-Sat-Radar-)",
            )
        }

        try {
            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                throw IOException("Overture snapshot HTTP " + responseCode)
            }
            val announced = connection.contentLengthLong
            if (announced > MAX_CITY_FILE_BYTES) {
                throw IOException("Overture şehir snapshot dosyası güvenli boyut sınırını aşıyor.")
            }

            val digest = MessageDigest.getInstance("SHA-256")
            var total = 0L
            BufferedInputStream(connection.inputStream).use { input ->
                FileOutputStream(temp).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        total += read
                        if (total > MAX_CITY_FILE_BYTES) {
                            throw IOException("Overture şehir snapshot dosyası güvenli boyut sınırını aşıyor.")
                        }
                        digest.update(buffer, 0, read)
                        output.write(buffer, 0, read)
                    }
                }
            }
            val actualSha = digest.digest().joinToString("") { byte -> "%02x".format(byte) }
            if (!actualSha.equals(entry.sha256, ignoreCase = true)) {
                temp.delete()
                throw IOException("Overture şehir snapshot SHA-256 doğrulaması başarısız.")
            }

            if (target.exists() && !target.delete()) {
                temp.delete()
                throw IOException("Eski Overture snapshot değiştirilemedi.")
            }
            if (!temp.renameTo(target)) {
                temp.copyTo(target, overwrite = true)
                temp.delete()
            }
            return target
        } finally {
            connection.disconnect()
            if (temp.exists() && temp.length() == 0L) temp.delete()
        }
    }

    private fun readCityFile(
        file: File,
        query: String,
        district: String?,
        neighborhood: String?,
        verifiedAtEpochMs: Long,
    ): List<VerifiedBusiness> {
        val records = mutableListOf<VerifiedBusiness>()
        GZIPInputStream(FileInputStream(file)).bufferedReader(Charsets.UTF_8).useLines { lines ->
            lines.forEach { line ->
                val record = runCatching { OvertureDirectoryParser.parseRecord(line) }.getOrNull()
                    ?: return@forEach
                if (!OvertureDirectoryParser.matches(record, query, district, neighborhood)) {
                    return@forEach
                }
                OvertureDirectoryParser.toVerifiedBusiness(record, verifiedAtEpochMs)
                    ?.let(records::add)
            }
        }
        return BusinessDeduplication.deduplicateCrossSource(records)
    }

    private fun downloadText(url: String, maxBytes: Long): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            setRequestProperty("Accept", "application/json")
            setRequestProperty(
                "User-Agent",
                "LANU-Global-Donuk-Satis-Radari/0.4 (+https://github.com/kanunal99-jpg/LANU-Global-Donuk-Sat-Radar-)",
            )
        }
        try {
            val responseCode = connection.responseCode
            if (responseCode !in 200..299) throw IOException("Overture manifest HTTP " + responseCode)
            val announced = connection.contentLengthLong
            if (announced > maxBytes) throw IOException("Overture manifest boyut sınırını aşıyor.")
            val input = BufferedInputStream(connection.inputStream)
            val output = StringBuilder()
            val buffer = ByteArray(16 * 1024)
            var total = 0L
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                total += read
                if (total > maxBytes) throw IOException("Overture manifest boyut sınırını aşıyor.")
                output.append(String(buffer, 0, read, Charsets.UTF_8))
            }
            return output.toString()
        } finally {
            connection.disconnect()
        }
    }

    private fun writeTextAtomically(target: File, payload: String) {
        val temp = File(target.parentFile, target.name + ".tmp")
        temp.writeText(payload, Charsets.UTF_8)
        if (target.exists() && !target.delete()) {
            temp.delete()
            throw IOException("Overture manifest önbelleği değiştirilemedi.")
        }
        if (!temp.renameTo(target)) {
            temp.copyTo(target, overwrite = true)
            temp.delete()
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
    }

    private companion object {
        const val LOG_TAG = "LanuOverture"
        const val CACHE_DIRECTORY = "overture_business_directory"
        const val MANIFEST_CACHE_FILE = "manifest.json"
        const val MANIFEST_TTL_MS = 24L * 60L * 60L * 1000L
        const val MAX_MANIFEST_BYTES = 2L * 1024L * 1024L
        const val MAX_CITY_FILE_BYTES = 256L * 1024L * 1024L
    }
}

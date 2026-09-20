package com.lanu.globaldonuksatisradari

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import java.util.UUID

data class CatalogProduct(
    val id: String,
    val name: String,
    val category: String,
    val unit: String,
    val priceMinor: Long,
    val currency: String,
    val note: String?,
    val updatedAtEpochMs: Long,
)

object ProductPrice {
    fun parseToMinor(input: String): Long {
        val raw = input
            .trim()
            .replace("₺", "")
            .replace("TL", "", ignoreCase = true)
            .replace(" ", "")
        require(raw.isNotEmpty()) { "Fiyat boş olamaz." }
        require(!raw.startsWith("-")) { "Fiyat negatif olamaz." }

        val normalized = when {
            raw.contains(",") && raw.contains(".") -> raw.replace(".", "").replace(",", ".")
            raw.contains(",") -> raw.replace(",", ".")
            else -> raw
        }

        return try {
            BigDecimal(normalized)
                .setScale(2, RoundingMode.HALF_UP)
                .movePointRight(2)
                .longValueExact()
                .also { require(it >= 0L) { "Fiyat negatif olamaz." } }
        } catch (_: Exception) {
            throw IllegalArgumentException("Fiyat sayısal olmalıdır. Örnek: 1250,50")
        }
    }

    fun formatMinor(minor: Long, currency: String): String {
        val symbols = DecimalFormatSymbols(Locale("tr", "TR"))
        val formatter = DecimalFormat("#,##0.00", symbols)
        return formatter.format(minor / 100.0) + " " + currency
    }
}

class ProductCatalogRepository(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val state = MutableStateFlow(load())

    val products: StateFlow<List<CatalogProduct>> = state.asStateFlow()

    @Synchronized
    fun upsert(
        id: String?,
        name: String,
        category: String,
        unit: String,
        priceMinor: Long,
        currency: String,
        note: String?,
    ): CatalogProduct {
        val normalizedName = name.trim()
        require(normalizedName.isNotEmpty()) { "Ürün adı boş olamaz." }
        require(priceMinor >= 0L) { "Fiyat negatif olamaz." }

        val normalizedCurrency = currency.trim().uppercase(Locale.ROOT)
        require(normalizedCurrency.length == 3) { "Para birimi 3 harf olmalıdır. Örnek: TRY" }

        val product = CatalogProduct(
            id = id?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString(),
            name = normalizedName,
            category = category.trim(),
            unit = unit.trim().ifEmpty { "Adet" },
            priceMinor = priceMinor,
            currency = normalizedCurrency,
            note = note?.trim()?.takeIf { it.isNotEmpty() },
            updatedAtEpochMs = System.currentTimeMillis(),
        )

        val updated = state.value
            .filterNot { it.id == product.id }
            .plus(product)
            .sortedBy { it.name.lowercase(Locale("tr", "TR")) }
        persist(updated)
        state.value = updated
        return product
    }

    @Synchronized
    fun delete(id: String) {
        val updated = state.value.filterNot { it.id == id }
        persist(updated)
        state.value = updated
    }

    @Synchronized
    fun clearAll() {
        persist(emptyList())
        state.value = emptyList()
    }

    private fun load(): List<CatalogProduct> {
        val raw = preferences.getString(KEY_PRODUCTS, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.getJSONObject(index)
                    add(
                        CatalogProduct(
                            id = item.getString("id"),
                            name = item.getString("name"),
                            category = item.optString("category"),
                            unit = item.optString("unit", "Adet").ifBlank { "Adet" },
                            priceMinor = item.getLong("priceMinor"),
                            currency = item.optString("currency", "TRY").ifBlank { "TRY" },
                            note = item.optString("note").takeIf { it.isNotBlank() },
                            updatedAtEpochMs = item.optLong("updatedAtEpochMs", 0L),
                        ),
                    )
                }
            }.sortedBy { it.name.lowercase(Locale("tr", "TR")) }
        }.getOrDefault(emptyList())
    }

    private fun persist(products: List<CatalogProduct>) {
        val array = JSONArray()
        products.forEach { product ->
            array.put(
                JSONObject().apply {
                    put("id", product.id)
                    put("name", product.name)
                    put("category", product.category)
                    put("unit", product.unit)
                    put("priceMinor", product.priceMinor)
                    put("currency", product.currency)
                    put("note", product.note)
                    put("updatedAtEpochMs", product.updatedAtEpochMs)
                },
            )
        }
        preferences.edit().putString(KEY_PRODUCTS, array.toString()).apply()
    }

    private companion object {
        const val PREFS_NAME = "lanu_product_catalog"
        const val KEY_PRODUCTS = "products"
    }
}

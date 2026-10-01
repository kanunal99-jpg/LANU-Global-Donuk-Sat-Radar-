package com.lanu.globaldonuksatisradari

import java.util.Locale

data class SalesAiContext(
    val city: String,
    val district: String,
    val crmCount: Int,
    val prospectCount: Int,
    val activeCustomerCount: Int,
    val radarResultCount: Int,
    val newBusinessCount: Int,
    val sampleBusinessNames: List<String>,
)

object LocalSalesAssistant {
    fun answer(question: String, context: SalesAiContext): String {
        val prompt = question.trim()
        val normalized = prompt.lowercase(Locale("tr", "TR"))
        val area = buildString {
            append(context.city)
            if (context.district.isNotBlank() && context.district != "Tümü") {
                append(" / ").append(context.district)
            }
        }
        val samples = context.sampleBusinessNames.take(5)

        return when {
            normalized.contains("whatsapp") || normalized.contains("mesaj") -> {
                val target = samples.firstOrNull() ?: "işletmeniz"
                """
                Merhaba, ben LANU Global Donuk Gıda'dan ulaşıyorum.
                $target için ürün grubumuza uygun kısa bir tanıtım ve fiyat çalışması paylaşmak isterim.
                Uygun olduğunuz bir zamanda ihtiyaçlarınızı öğrenip size özel seçenek hazırlayabiliriz.
                """.trimIndent()
            }

            normalized.contains("ziyaret") || normalized.contains("rota") || normalized.contains("plan") -> {
                buildString {
                    appendLine("$area için ücretsiz yerel ziyaret önerisi:")
                    if (context.newBusinessCount > 0) {
                        appendLine("• Önce son taramada bulunan ${context.newBusinessCount} yeni işletmeyi ziyaret et.")
                    }
                    if (samples.isNotEmpty()) {
                        samples.forEachIndexed { index, name -> appendLine("• ${index + 1}. $name") }
                    } else {
                        appendLine("• Radar taraması yapıp yeni ve CRM'de olmayan noktaları listele.")
                    }
                    append("• Ziyaret sonrası sonucu CRM aktivitesi olarak kaydet.")
                }.trim()
            }

            normalized.contains("öncelik") || normalized.contains("oncelik") -> {
                buildString {
                    appendLine("Satış önceliği:")
                    appendLine("• Yeni işletmeler: ${context.newBusinessCount}")
                    appendLine("• Prospect CRM kayıtları: ${context.prospectCount}")
                    appendLine("• Aktif müşteriler: ${context.activeCustomerCount}")
                    if (context.newBusinessCount > 0) {
                        append("Önce yeni bulunan işletmeleri, ardından uzun süredir prospect durumunda kalan noktaları ele al.")
                    } else {
                        append("Yeni işletme yoksa prospect müşterilerde ziyaret/arama takibini öne al.")
                    }
                }
            }

            normalized.contains("özet") || normalized.contains("ozet") || prompt.isBlank() -> {
                """
                $area satış özeti:
                • Radar sonucu: ${context.radarResultCount}
                • Son taramadan beri yeni: ${context.newBusinessCount}
                • CRM toplam: ${context.crmCount}
                • Prospect: ${context.prospectCount}
                • Aktif müşteri: ${context.activeCustomerCount}
                """.trimIndent()
            }

            else -> {
                buildString {
                    appendLine("Ücretsiz yerel satış asistanı yanıtı:")
                    appendLine("• Bölge: $area")
                    appendLine("• CRM: ${context.crmCount} kayıt")
                    appendLine("• Radar: ${context.radarResultCount} sonuç, ${context.newBusinessCount} yeni")
                    appendLine()
                    appendLine("Soruna göre öneri: $prompt")
                    append("Yeni işletmeleri önce kontrol et, uygun olanları toplu CRM'e kaydet ve ziyaret/arama aksiyonu oluştur.")
                }
            }
        }
    }
}

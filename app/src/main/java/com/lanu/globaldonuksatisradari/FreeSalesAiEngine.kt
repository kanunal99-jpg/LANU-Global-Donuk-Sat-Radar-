package com.lanu.globaldonuksatisradari

import android.util.Log
import com.google.mlkit.genai.common.DownloadStatus
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.GenerativeModel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull

enum class FreeAiMode {
    GEMINI_NANO,
    LOCAL_FALLBACK,
}

enum class FreeAiAvailability {
    UNKNOWN,
    AVAILABLE,
    DOWNLOADABLE,
    DOWNLOADING,
    UNAVAILABLE,
}

data class FreeAiAnswer(
    val text: String,
    val mode: FreeAiMode,
)

class FreeSalesAiEngine {
    private var model: GenerativeModel? = null

    private fun getModel(): GenerativeModel =
        model ?: Generation.getClient().also { model = it }

    suspend fun availability(): FreeAiAvailability {
        val status = withTimeoutOrNull(5_000) {
            runCatching { getModel().checkStatus() }
                .onFailure { Log.w(TAG, "Gemini Nano durum kontrolü başarısız; yerel fallback kullanılacak.", it) }
                .getOrNull()
        } ?: return FreeAiAvailability.UNAVAILABLE

        return when (status) {
            FeatureStatus.AVAILABLE -> FreeAiAvailability.AVAILABLE
            FeatureStatus.DOWNLOADABLE -> FreeAiAvailability.DOWNLOADABLE
            FeatureStatus.DOWNLOADING -> FreeAiAvailability.DOWNLOADING
            else -> FreeAiAvailability.UNAVAILABLE
        }
    }

    suspend fun prepareOnDeviceModel(): FreeAiAvailability {
        return when (availability()) {
            FreeAiAvailability.AVAILABLE -> FreeAiAvailability.AVAILABLE
            FreeAiAvailability.DOWNLOADABLE -> {
                var finalStatus = FreeAiAvailability.DOWNLOADING
                runCatching {
                    withTimeout(180_000) {
                        getModel().download().collect { status ->
                            finalStatus = when (status) {
                                is DownloadStatus.DownloadCompleted -> FreeAiAvailability.AVAILABLE
                                is DownloadStatus.DownloadFailed -> FreeAiAvailability.UNAVAILABLE
                                else -> FreeAiAvailability.DOWNLOADING
                            }
                        }
                    }
                }.onFailure {
                    Log.w(TAG, "Gemini Nano indirme/hazırlama başarısız; yerel fallback kullanılacak.", it)
                    finalStatus = FreeAiAvailability.UNAVAILABLE
                }
                finalStatus
            }
            FreeAiAvailability.DOWNLOADING -> FreeAiAvailability.DOWNLOADING
            else -> FreeAiAvailability.UNAVAILABLE
        }
    }

    suspend fun answer(question: String, context: SalesAiContext): FreeAiAnswer {
        val local = { FreeAiAnswer(LocalSalesAssistant.answer(question, context), FreeAiMode.LOCAL_FALLBACK) }
        val status = availability()
        if (status != FreeAiAvailability.AVAILABLE) return local()

        val prompt = buildPrompt(question, context)
        return runCatching {
            val response = withTimeout(45_000) {
                getModel().generateContent(prompt)
            }
            val text = response.candidates.firstOrNull()?.text?.trim().orEmpty()
            if (text.isBlank()) error("Gemini Nano boş yanıt döndürdü.")
            FreeAiAnswer(text, FreeAiMode.GEMINI_NANO)
        }.onFailure {
            Log.w(TAG, "Gemini Nano çıkarımı başarısız; yerel fallback kullanılacak.", it)
        }.getOrElse { local() }
    }

    fun close() {
        runCatching { model?.close() }
        model = null
    }

    private fun buildPrompt(question: String, context: SalesAiContext): String = """
        Sen LANU Global Donuk Gıda saha satış asistanısın.
        Türkçe, kısa, uygulanabilir ve profesyonel cevap ver.
        Verilmeyen kişisel/vergi bilgilerini uydurma.
        Mevcut CRM ve Radar verisini esas al.

        Bölge: ${context.city} / ${context.district}
        CRM toplam: ${context.crmCount}
        Prospect: ${context.prospectCount}
        Aktif müşteri: ${context.activeCustomerCount}
        Radar sonucu: ${context.radarResultCount}
        Son taramadan beri yeni işletme: ${context.newBusinessCount}
        Örnek işletmeler: ${context.sampleBusinessNames.take(5).joinToString(", ")}

        Kullanıcı isteği:
        ${question.trim()}
    """.trimIndent()

    companion object {
        private const val TAG = "LanuFreeAi"
    }
}

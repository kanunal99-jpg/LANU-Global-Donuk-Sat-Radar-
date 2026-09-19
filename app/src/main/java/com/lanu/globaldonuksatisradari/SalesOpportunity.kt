package com.lanu.globaldonuksatisradari

import java.util.Locale

data class SalesOpportunity(
    val focus: String,
    val discoveryQuestions: List<String>,
    val conversation: String,
    val verifiedClaims: List<String>,
    val sourceUrl: String = "https://globaldonukgida.com/",
)

/**
 * Produces sales guidance from the verified business category only.
 * It deliberately does not invent employee counts, order volumes, prices, or sales values.
 */
fun buildSalesOpportunity(category: String?): SalesOpportunity {
    val value = category.orEmpty().lowercase(Locale.ROOT)

    return when {
        listOf("restoran", "restaurant", "lokanta").any(value::contains) -> SalesOpportunity(
            focus = "Mutfak hazırlık yükünü ve servis hızını incele",
            discoveryQuestions = listOf(
                "Günlük sıcak yemek porsiyonu yaklaşık kaç?",
                "Hazırlıkta en çok zaman alan ürün grupları hangileri?",
                "Yoğun saatlerde mutfak personeli kapasitesi yeterli mi?",
            ),
            conversation = "Pişmiş donuk yemek çözümünün menüyü genişletme ve yoğun saatlerde hazırlık yükünü azaltma ihtiyacına uyup uymadığını ölç.",
        ,
        verifiedClaims = listOf(
            "Pişmiş donuk yemek çözümü: ürünler profesyonel mutfakta hazırlanıp pişirilir, standart porsiyonlanır ve hızlı dondurulur.",
            "Operasyon hedefi: uzun hazırlık süreçlerini ve mutfak yükünü azaltmaya yardımcı olmak.",
            "Servis hedefi: ürünleri kısa ısıtma sürecinin ardından servise hazırlamak.",
            "Hedef işletmeler: restoran, kafe, otel, catering/toplu yemek ve benzeri profesyonel işletmeler.",
            "Değer önerisi: standart porsiyon, standart lezzet, daha kontrollü fire ve operasyonel kolaylık.",
        ),
        )
        listOf("kafe", "cafe", "coffee", "bistro").any(value::contains) -> SalesOpportunity(
            focus = "Mevcut mutfak kapasitesiyle sıcak yemek ekleme fırsatını ölç",
            discoveryQuestions = listOf(
                "Menüde sıcak yemek açığı var mı?",
                "Sıcak yemek için ayrı üretim/personel kapasitesi mevcut mu?",
                "Müşterilerden hızlı servis talebi geliyor mu?",
            ),
            conversation = "Ek mutfak yatırımı yapmadan sıcak yemek seçenekleri eklemenin operasyonel olarak uygun olup olmadığını keşfet.",
        verifiedClaims = listOf(
            "Pişmiş donuk yemek çözümü: ürünler profesyonel mutfakta hazırlanıp pişirilir, standart porsiyonlanır ve hızlı dondurulur.",
            "Operasyon hedefi: uzun hazırlık süreçlerini ve mutfak yükünü azaltmaya yardımcı olmak.",
            "Servis hedefi: ürünleri kısa ısıtma sürecinin ardından servise hazırlamak.",
            "Hedef işletmeler: restoran, kafe, otel, catering/toplu yemek ve benzeri profesyonel işletmeler.",
            "Değer önerisi: standart porsiyon, standart lezzet, daha kontrollü fire ve operasyonel kolaylık.",
        ),
        )
        listOf("otel", "hotel").any(value::contains) -> SalesOpportunity(
            focus = "Standart porsiyon, servis sürekliliği ve operasyon yükünü incele",
            discoveryQuestions = listOf(
                "Otelin hangi servis noktalarında sıcak yemek ihtiyacı var?",
                "Vardiya/personel kapasitesi hangi saatlerde zorlanıyor?",
                "Standart porsiyon ve hızlı servis hangi ürünlerde kritik?",
            ),
            conversation = "Tedarik sürekliliği ve standart porsiyonlamanın mevcut operasyonla uyumunu doğrula.",
        verifiedClaims = listOf(
            "Pişmiş donuk yemek çözümü: ürünler profesyonel mutfakta hazırlanıp pişirilir, standart porsiyonlanır ve hızlı dondurulur.",
            "Operasyon hedefi: uzun hazırlık süreçlerini ve mutfak yükünü azaltmaya yardımcı olmak.",
            "Servis hedefi: ürünleri kısa ısıtma sürecinin ardından servise hazırlamak.",
            "Hedef işletmeler: restoran, kafe, otel, catering/toplu yemek ve benzeri profesyonel işletmeler.",
            "Değer önerisi: standart porsiyon, standart lezzet, daha kontrollü fire ve operasyonel kolaylık.",
        ),
        )
        listOf("catering", "toplu yemek", "yemek şirketi", "tabldot").any(value::contains) -> SalesOpportunity(
            focus = "Hacim, standart porsiyon ve tedarik sürekliliğini doğrula",
            discoveryQuestions = listOf(
                "Günlük/haftalık servis hacmi nasıl değişiyor?",
                "Hangi yemeklerde üretim kapasitesi darboğazı oluşuyor?",
                "Teslimat ve soğuk zincir gereksinimleri neler?",
            ),
            conversation = "Pişmiş donuk ürünlerin üretim planı ve soğuk zincir operasyonuna uyumunu veriyle test et.",
        verifiedClaims = listOf(
            "Pişmiş donuk yemek çözümü: ürünler profesyonel mutfakta hazırlanıp pişirilir, standart porsiyonlanır ve hızlı dondurulur.",
            "Operasyon hedefi: uzun hazırlık süreçlerini ve mutfak yükünü azaltmaya yardımcı olmak.",
            "Servis hedefi: ürünleri kısa ısıtma sürecinin ardından servise hazırlamak.",
            "Hedef işletmeler: restoran, kafe, otel, catering/toplu yemek ve benzeri profesyonel işletmeler.",
            "Değer önerisi: standart porsiyon, standart lezzet, daha kontrollü fire ve operasyonel kolaylık.",
        ),
        )
        listOf("büfe", "bufe", "buffet", "nargile", "lounge").any(value::contains) -> SalesOpportunity(
            focus = "Sıcak yemek ekleme ve hızlı servis ihtiyacını keşfet",
            discoveryQuestions = listOf(
                "Mevcut menüde sıcak yemek seçeneği var mı?",
                "Mutfak alanı ve personel kapasitesi ne kadar?",
                "En yoğun servis saatleri hangileri?",
            ),
            conversation = "Mevcut altyapıyı büyütmeden sıcak yemek seçeneği eklemenin ticari ve operasyonel uygunluğunu ölç.",
        verifiedClaims = listOf(
            "Pişmiş donuk yemek çözümü: ürünler profesyonel mutfakta hazırlanıp pişirilir, standart porsiyonlanır ve hızlı dondurulur.",
            "Operasyon hedefi: uzun hazırlık süreçlerini ve mutfak yükünü azaltmaya yardımcı olmak.",
            "Servis hedefi: ürünleri kısa ısıtma sürecinin ardından servise hazırlamak.",
            "Hedef işletmeler: restoran, kafe, otel, catering/toplu yemek ve benzeri profesyonel işletmeler.",
            "Değer önerisi: standart porsiyon, standart lezzet, daha kontrollü fire ve operasyonel kolaylık.",
        ),
        )
        listOf("cloud kitchen", "dark kitchen", "ghost kitchen").any(value::contains) -> SalesOpportunity(
            focus = "Üretim kapasitesi, menü çevikliği ve teslimata hazırlık süresini incele",
            discoveryQuestions = listOf(
                "Hangi menü kalemlerinde üretim kapasitesi sınırlı?",
                "Sipariş yoğunluğu hangi saatlerde artıyor?",
                "Standart porsiyon ve hızlı hazırlık hangi ürünlerde değer yaratır?",
            ),
            conversation = "Pişmiş donuk ürünlerin üretim kapasitesini ve servis hızını destekleyip desteklemediğini operasyon verisiyle doğrula.",
        verifiedClaims = listOf(
            "Pişmiş donuk yemek çözümü: ürünler profesyonel mutfakta hazırlanıp pişirilir, standart porsiyonlanır ve hızlı dondurulur.",
            "Operasyon hedefi: uzun hazırlık süreçlerini ve mutfak yükünü azaltmaya yardımcı olmak.",
            "Servis hedefi: ürünleri kısa ısıtma sürecinin ardından servise hazırlamak.",
            "Hedef işletmeler: restoran, kafe, otel, catering/toplu yemek ve benzeri profesyonel işletmeler.",
            "Değer önerisi: standart porsiyon, standart lezzet, daha kontrollü fire ve operasyonel kolaylık.",
        ),
        )
        else -> SalesOpportunity(
            focus = "İşletmenin HORECA yemek operasyonunu önce doğrula",
            discoveryQuestions = listOf(
                "Sıcak yemek servisi yapılıyor mu?",
                "Mevcut mutfak ve personel kapasitesi nasıl?",
                "Hazırlık süresi, fire ve menü genişletme tarafında temel sorun nedir?",
            ),
            conversation = "Kategori kaynağı yeterince ayrıntılı olmadığı için önce işletmenin gerçek operasyonunu keşfet; ürün veya satış rakamı varsayma.",
        verifiedClaims = listOf(
            "Pişmiş donuk yemek çözümü: ürünler profesyonel mutfakta hazırlanıp pişirilir, standart porsiyonlanır ve hızlı dondurulur.",
            "Operasyon hedefi: uzun hazırlık süreçlerini ve mutfak yükünü azaltmaya yardımcı olmak.",
            "Servis hedefi: ürünleri kısa ısıtma sürecinin ardından servise hazırlamak.",
            "Hedef işletmeler: restoran, kafe, otel, catering/toplu yemek ve benzeri profesyonel işletmeler.",
            "Değer önerisi: standart porsiyon, standart lezzet, daha kontrollü fire ve operasyonel kolaylık.",
        ),
        )
    }
}

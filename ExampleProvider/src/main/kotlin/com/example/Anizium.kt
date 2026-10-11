package com.example

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor

class Anizium : MainAPI() {
    override var mainUrl = "https://anizium.co"
    override var name = "Anizium"
    override val hasMainPage = true
    override var lang = "tr"
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.Anime)

    // 1. ARAMA KISMI (Birlikte bitirdiğimiz kısım)
    override suspend fun search(query: String): List<SearchResponse> {
        val searchUrl = "$mainUrl/?s=$query" 
        val document = app.get(searchUrl).document
        
        return document.select("div.iq-top-ten-block").mapNotNull { element ->
            val urlHref = element.select("a").attr("href")
            if (urlHref.isBlank()) return@mapNotNull null
            
            val url = if (urlHref.startsWith("/")) "$mainUrl$urlHref" else urlHref
            val poster = element.select("img.img-fluid").attr("src")
            val title = url.split("/").lastOrNull { it.isNotBlank() } ?: "Bilinmeyen Anime"
            
            newAnimeSearchResponse(title, url, TvType.Anime) {
                this.posterUrl = poster
            }
        }
    }

    // 2. DETAY SAYFASI (Başlık, Açıklama ve Bölümler)
    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document
        
        // SEO meta etiketlerinden bilgileri çekmeyi deniyoruz
        val title = document.selectFirst("meta[property=og:title]")?.attr("content") 
            ?: document.selectFirst("h1")?.text() 
            ?: "İsimsiz Anime"
            
        val poster = document.selectFirst("meta[property=og:image]")?.attr("content")
        val plot = document.selectFirst("meta[property=og:description]")?.attr("content")
        
        // Linkinde "bolum" veya "episode" geçen bağlantıları bölüm olarak listele
        val episodes = document.select("a[href]").filter { 
            it.attr("href").contains("-bolum", ignoreCase = true) || 
            it.attr("href").contains("episode", ignoreCase = true) 
        }.mapIndexed { index, element ->
            val epUrl = element.attr("href")
            val epName = element.text().takeIf { it.isNotBlank() } ?: "${index + 1}. Bölüm"
            val fixedUrl = if (epUrl.startsWith("/")) "$mainUrl$epUrl" else epUrl
            
            Episode(
                data = fixedUrl,
                name = epName,
                episode = index + 1
            )
        }.distinctBy { it.data } // Aynı linkten iki tane varsa teke düşür

        return newTvSeriesLoadResponse(title, url, TvType.Anime, episodes) {
            this.posterUrl = poster
            this.plot = plot
        }
    }

    // 3. VİDEO LİNKLERİ (Oynatıcıyı bulma)
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = app.get(data).document
        
        // Sayfadaki iframe (gömülü video) linklerini bul ve Cloudstream çözücülerine yolla
        document.select("iframe").forEach { iframe ->
            val iframeUrl = iframe.attr("src")
            if (iframeUrl.isNotBlank()) {
                val fixedIframeUrl = if (iframeUrl.startsWith("//")) "https:$iframeUrl" else iframeUrl
                loadExtractor(fixedIframeUrl, data, subtitleCallback, callback)
            }
        }
        return true
    }
}
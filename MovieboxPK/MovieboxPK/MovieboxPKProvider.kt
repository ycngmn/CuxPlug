package com.MovieboxPK

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*

class MovieboxPKProvider : MainAPI() {
    override val name = "MovieboxPK"
    override val mainUrl = "https://moviebox.pk"   // ← on va le rendre changeable après

    // On rend le domaine changeable directement dans CloudStream
    override val baseUrl: String
        get() = preferences.getString("base_url", "https://moviebox.pk") ?: "https://moviebox.pk"

    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    override val iconUrl = "https://moviebox.pk/favicon.ico"

    // Recherche
    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$baseUrl/search/?q=$query"
        val doc = app.get(url).document

        return doc.select("div.item, div.movie-item, div.poster") // ← sélecteur à ajuster si besoin
            .map {
                val title = it.selectFirst("h2, h3, .title")?.text() ?: "Sans titre"
                val poster = it.selectFirst("img")?.attr("src")?.takeIf { it.startsWith("http") } 
                    ?: it.selectFirst("img")?.attr("data-src")
                val link = fixUrl(it.selectFirst("a")?.attr("href") ?: "")
                newTvSeriesSearchResponse(title, link, TvType.Movie) {
                    this.posterUrl = poster
                }
            }
    }

    // Page d'accueil (derniers films/séries)
    override val mainPage = listOf(
        MainPageData("Films récents", "$baseUrl/movies/")   // change si la page est différente
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomeScreen {
        val doc = app.get(request.url).document
        val items = doc.select("div.item, div.movie-item, div.poster")
        val list = items.map {
            val title = it.selectFirst("h2, h3, .title")?.text() ?: ""
            val poster = it.selectFirst("img")?.attr("src")?.takeIf { it.startsWith("http") }
            val link = fixUrl(it.selectFirst("a")?.attr("href") ?: "")
            newTvSeriesSearchResponse(title, link, TvType.Movie) {
                this.posterUrl = poster
            }
        }
        return HomeScreen(list)
    }

    // Page détail
    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url).document
        val title = doc.selectFirst("h1, .title")?.text() ?: "Sans titre"
        val poster = doc.selectFirst("img.poster, img")?.attr("src")?.takeIf { it.startsWith("http") }

        val episodes = mutableListOf<Episode>()
        // Pour les films : un seul épisode
        episodes.add(Episode(
            data = url,   // on passe l'URL de la page
            name = "Lecture"
        ))

        return newTvSeriesLoadResponse(title, url, TvType.Movie, episodes) {
            this.posterUrl = poster
        }
    }

    // Extraction des liens vidéo (.m3u8 prioritaire)
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val doc = app.get(data).document
        val pageHtml = doc.html()

        // Regex très efficace pour trouver les .m3u8 et .mp4
        Regex("""(https?://[^\s'"]+\.(m3u8|mp4)[^\s'"]*)""").findAll(pageHtml).forEach { match ->
            val link = match.value
            if (link.endsWith(".m3u8")) {
                callback(ExtractorLink(
                    source = name,
                    name = "HLS (m3u8)",
                    url = link,
                    referer = baseUrl,
                    quality = Qualities.Unknown.value
                ))
            } else if (link.endsWith(".mp4")) {
                callback(ExtractorLink(
                    source = name,
                    name = "MP4",
                    url = link,
                    referer = baseUrl,
                    quality = Qualities.Unknown.value
                ))
            }
        }

        // Si le site a des players dans des scripts ou iframes (très courant)
        doc.select("iframe, script").forEach { el ->
            val src = el.attr("src") ?: el.html()
            Regex("""(https?://[^\s'"]+\.m3u8[^\s'"]*)""").findAll(src).forEach { m ->
                callback(ExtractorLink(name, "Player", m.value, baseUrl, Qualities.Unknown.value))
            }
        }
    }

    // Permet de changer le domaine dans les paramètres de l'extension
    override fun getPreferences(): List<Preference> {
        return listOf(
            StringPreference("base_url", "URL du site", "https://moviebox.pk", "https://moviebox.pk")
        )
    }
}

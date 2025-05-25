package com.ycngmn


import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.base64Decode
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.nodes.Element

class AnimeLuxeProvider : MainAPI() {

    override var mainUrl = "https://ww3.animeluxe.org"
    override var name = "AnimeLuxe"
    override val supportedTypes = setOf(
        TvType.Anime,
        TvType.AnimeMovie
    )

    override var lang = "ar"

    override val hasMainPage = true
    override val hasQuickSearch = false
    override val hasDownloadSupport = true

    override val mainPage = mainPageOf(
        "anime" to "آخر الأنميات المضافة",
        "episodes" to "آخر الحلقات المضافة",
        "seasons/winter-2025" to "شتاء 2025",
        "seasons/spring-2025" to "ربيع 2025")

    override suspend fun getMainPage(page: Int, request: MainPageRequest
    ): HomePageResponse {

        val doc = app.get("$mainUrl/${request.data}/page/$page").document
        val selector = when {
            doc.selectFirst(".anime-card") != null ->  ".anime-card"
            doc.selectFirst(".search-card") != null -> ".search-card"
            doc.selectFirst(".episode-card") != null -> ".episode-card"
            else -> ".ycngmn"
        }
        val home = doc.select(selector).map { toResult(it) }

        return newHomePageResponse(
            HomePageList(request.name, home, isHorizontalImages = false),
            hasNext = true
        )
    }

    private fun toResult(post: Element): SearchResponse {
        val title = post.selectFirst("h3")?.text() ?: ""
        val url = post.selectFirst("a")?.attr("href") ?: ""
        val thumb = post.selectFirst("a")?.attr("data-src") ?: ""
        return newMovieSearchResponse(title, url, TvType.Movie).apply {
            posterUrl = thumb
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val doc = app.get("$mainUrl/anime?s=$query").document
        return doc.select(".search-card").map { toResult(it) }
    }

    override suspend fun load(url: String): LoadResponse {
        val doc = app.post(url).document
        val title = doc.selectFirst(".episode-info h1")?.text()
            ?: doc.selectFirst(".media-story h3")?.text() ?: ""
        val image = doc.selectFirst("a[id=\"click-player\"]")?.attr("data-src")
            ?: doc.selectFirst(".episodes-lists a")?.attr("data-src") ?: ""
        val synopsis = doc.selectFirst(".content p")?.text() ?: ""
        val type = if (doc.selectFirst(".media-episodes") == null) TvType.AnimeMovie else TvType.Anime
        val genres = doc.select(".genres a").map { it.text() }
        val duration = doc.select(".media-info li span").getOrNull(6)?.text()
            ?.split(" ")?.getOrNull(0)?.toIntOrNull()
        val releasedYear = doc.select("ul.media-info li span").getOrNull(2)?.text()?.toIntOrNull()

        val episodes = mutableListOf<Episode>()

        if (type == TvType.Anime) {
            val episodeList = doc.select(".episodes-lists li")
            episodeList.map {
                episodes += newEpisode(it.selectFirst("a")?.attr("href") ?: "") {
                    name = it.selectFirst("h3")?.text() ?: ""
                    posterUrl = it.selectFirst("a")?.attr("data-src") ?: image
                }
            }

            return newTvSeriesLoadResponse(title, url, type, episodes) {
                this.posterUrl = image
                this.plot = synopsis
                this.tags = genres
                this.duration = duration
                this.year = releasedYear
            }
        }

        else {
            return newMovieLoadResponse(title, url, type, url) {
                this.posterUrl = image
            }
        }



    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {


        val soup = app.get(data).document
        val servers = soup.select(".server-list li")

        for (server in servers) {
            val encodedUrl = server.selectFirst("a")?.attr("data-url") ?: continue
            val streamUrl = base64Decode(encodedUrl)

            loadExtractor(streamUrl, subtitleCallback, callback)
        }

        return true
    }
}
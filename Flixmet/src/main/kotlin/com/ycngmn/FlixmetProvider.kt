package com.ycngmn


import android.net.Uri
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
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import org.jsoup.nodes.Element

class FlixmetProvider : MainAPI() {

    override var mainUrl = "https://flixmet.com"
    override var name = "Flixmet"
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
    )

    override var lang = "bn"

    override val hasMainPage = true
    override val hasQuickSearch = false
    override val hasDownloadSupport = true

    override val mainPage = mainPageOf(
        "latest-movie" to "Latest Movie",
        "bangla-movie" to "Bangla Movie",
        "hindi-dubbed" to "Hindi Dubbed Movie",
        "hollywood-english" to "Hollywood English Movie",
        "bangla-dabbed" to "Bangla Dubbed",
        "tv-web-series" to "Tv show & Web series",
        "bangla-natok-drama" to "Bangla Natok & Drama",
        "animation" to "Animation",
        "trending" to "Trending"

    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest
    ): HomePageResponse {

        val home =
            app.get("$mainUrl/category/${request.data}/page/$page").document
                .select("#gmr-main-load article").map { toResult(it) }

        return newHomePageResponse(
            HomePageList(request.name, home, isHorizontalImages = false),
            hasNext = true
        )
    }

    private fun toResult(post: Element): SearchResponse {
        val anchor = post.selectFirst("a")
        val title = anchor?.attr("title")?.substringAfter("Permalink to:")
            ?.substringBefore("Movie Download")?.substringBefore("download")
            ?.trim() ?: ""
        val url = anchor?.attr("href") ?: ""
        val thumb = post.selectFirst("img")
            ?.attr("src") ?: ""
        return newMovieSearchResponse(title, url, TvType.Movie).apply {
            posterUrl = thumb
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val doc = app.get("$mainUrl/?s=$query").document
        return doc.select("#gmr-main-load article").map { toResult(it) }
    }

    override suspend fun load(url: String): LoadResponse {

        val doc = app.post(url, cacheTime = 60).document
        val title = doc.selectFirst("title")?.text()
            ?.substringBefore(" Download")
            ?.substringBefore(" Movie") ?: ""
        val image = doc.selectFirst("meta[property=\"og:image\"]")?.attr("content")
        val synopsis = "."
        val type = if (url.contains("/tv/")) TvType.TvSeries
            else TvType.Movie
        val genres = doc.select("a[rel=\"category tag\"]").map { it.text().replace("&amp;", "&") }
        val duration = doc.selectFirst("span[property=\"duration\"]")?.text()
            ?.split(" ")?.getOrNull(0)?.toIntOrNull()
        val releasedYear = doc.selectFirst("span time[itemprop=\"dateCreated\"]")?.attr("datetime")
            ?.split("-")?.getOrNull(0)?.toIntOrNull()

        val episodes = mutableListOf<Episode>()

        if (type == TvType.TvSeries) {
            val seasonContainer = doc.select(".gmr-listseries a")

            seasonContainer.forEachIndexed { i, season ->

                if (i != 0) {

                    val soup = app.get(season.attr("href")).document
                    val downloadUrl = soup.selectFirst("#download")?.select("a")?.last()?.attr("href")

                    episodes += Episode(
                        data = downloadUrl ?: "",
                        name = season.text(),
                        posterUrl = image,
                        season = i,
                    )
                }

            }

            return newTvSeriesLoadResponse(title,url,TvType.TvSeries, episodes) {
                this.posterUrl = image
                this.plot = synopsis
                this.tags = genres
                this.duration = duration
                this.year = releasedYear
            }
        }

        else {
            val movieUrl = doc.selectFirst("#download")?.select("a")?.last()?.attr("href")
            return newMovieLoadResponse(title,url,TvType.Movie, movieUrl ?: "") {
                this.posterUrl = image
                this.plot = synopsis
                this.tags = genres
                this.year = releasedYear
                this.duration = duration
            }
        }

    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {

        if (data.isEmpty()) return false

        val parsedData = Uri.parse(data)
        val url = parsedData.getQueryParameter("link") ?: return false

        val soup = app.get(url).document
        val allButtons = soup.select(".wp-block-button__link")

        var srcSlug = ""

        for (button in allButtons.reversed()) {
            when {
                button.attr("href").isEmpty() -> { srcSlug = button.text(); continue }
                !(button.attr("href").contains("filepress")
                        || button.attr("href").contains("gdtot")) ->
                {
                    callback.invoke(
                        ExtractorLink(
                            name,
                            button.text() + "| $srcSlug",
                            button.attr("href"),
                            "",
                            Qualities.Unknown.value
                        )
                    )
                }
            }
        }

        return true
    }
}
package com.ycngmn


import android.util.Log
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.addSeasonNames
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.util.regex.Pattern
import kotlin.math.log

class FrenchStreamProvider : MainAPI() {

    override var mainUrl = "https://french-stream.pink" // dynamically get mainUrl from https://fstream.one/ if unstable.
    override var name = "French-Stream"
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
    )

    override var lang = "fr" // French

    override val hasMainPage = true
    override val hasQuickSearch = false



    override val mainPage = mainPageOf(
        "films" to "Derniers Films",
        "s-tv" to "Dernieres Séries",
        "netflix-series-" to "Nouveautés NETFLIX",
        "series-apple-tv" to "Nouveautés Apple TV+",
        "series-disney-plus" to "Nouveautés Disney+",
        "serie-amazon-prime-videos" to "Nouveautés Prime Video",
        "sries-du-moment" to "Box Office Série",

    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest
    ): HomePageResponse {



        val home = app.get("$mainUrl/${request.data}/page/$page").document
            .select(".short").map { toResult(it) }

        return newHomePageResponse(
            HomePageList(request.name, home, isHorizontalImages = false),
            hasNext = true
        )
    }


    private fun toResult(post: Element): SearchResponse {
        val title = post.selectFirst(".short-title")?.text() ?: ""
        val url = mainUrl + post.selectFirst(".short-poster.img-box.with-mask")?.attr("href")
        var thumb = post.selectFirst("img")?.attr("src") ?: ""
        if (thumb.startsWith("data:"))
            thumb = post.selectFirst("img")?.attr("data-src") ?: ""
        return newMovieSearchResponse(title, url, TvType.Movie) {
            this.posterUrl = thumb


        }
    }

    override suspend fun search(query: String): List<SearchResponse> {

        val doc = app.get("$mainUrl/index.php?story=$query&do=search&subaction=search").document
        return doc.select(".short.lazy-block")
            .map { toResult(it) }
    }


    override suspend fun load(url: String): LoadResponse {

        val doc = app.get(url, cacheTime = 60).document
        val title = doc.selectFirst("#s-title")?.ownText() ?: ""
        val image = doc.selectFirst(".thumbnail")?.attr("src")
        val synopsis = doc.selectFirst("#s-desc")?.ownText()
        val type = if (doc.selectFirst("#downloadBtn") == null) TvType.TvSeries else TvType.Movie

        val genres = if (type == TvType.Movie) doc.selectFirst("#s-list li:nth-child(2)")?.select("a")?.map { it.text() }
                    else doc.selectFirst("#s-list li:nth-child(2)")?.text()?.substringAfter(":")?.split(",")?.map { it.trim() }


        val episodes = mutableListOf<Episode>()

        if (type == TvType.TvSeries) {
            val versionContainers = doc.select(".elink")

            // Version 1 for VF, 2 for VOSTFR. Seasons are separate card.
            versionContainers.forEachIndexed { i, versionContainer ->
                versionContainer.select("a").forEach {
                    val dataRel = it.attr("data-rel")
                    if (dataRel.isNotEmpty())
                        episodes += Episode(
                            data = doc.selectFirst("#$dataRel")?.toString() ?: "",
                            name = it.attr("title"),
                            posterUrl = image,
                            season = i + 1,
                        )
                }

            }

            return newTvSeriesLoadResponse(title,url,TvType.TvSeries, episodes) {
                this.posterUrl = image
                this.plot = synopsis
                this.tags = genres
                addSeasonNames(listOf("VF","VOSTFR"))


            }
        }

        else {

            val movieData = doc.selectFirst("#player-container + script ")?.data()
            val regex = Pattern.compile("""playerUrls\s*=\s*(\{.*?\});""", Pattern.DOTALL)
            val matcher = regex.matcher(movieData.toString())
            val movieJson = if (matcher.find()) JSONObject(matcher.group(1) ?: "") else JSONObject()
            val sortedMJ = sortUrls(movieJson)

            // maybe i made it a bit complex. Would have been nicer if could be transformed to "Track/ Piste". "A Big Maybe"
            val movieEpisodes = mutableListOf<Episode>()
            val versionNames = mutableListOf<String>()
            var index = 0
            for ((version, links) in sortedMJ)  {
                index++
                versionNames += version
                movieEpisodes += Episode(
                    data = links.toString(),
                    name = "$title [$version]",
                    posterUrl = image,
                    season = index,
                    episode = 1,
                )

            }

            val releasedYear = doc.selectFirst(".flist-col:nth-child(2) li:nth-child(3)")?.ownText()?.trim()
            return newTvSeriesLoadResponse(title,url,TvType.TvSeries, movieEpisodes) {
                this.posterUrl = image
                this.plot = synopsis
                this.tags = genres
                this.year = releasedYear?.toIntOrNull()
                addSeasonNames(versionNames)

            }
        }

    }


    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {

        if (data.contains("class=\"fsctab\"")) {

            Jsoup.parse(data).select(".fsctab").forEach {
                // create extractor kt
                val vid = app.get(it.attr("href"), allowRedirects = true).url
                loadExtractor(vid, subtitleCallback, callback)
            }
        }

        else {
            val urls = data.removeSurrounding("[", "]").split(", ").map { it.trim() }
            urls.forEach {
                loadExtractor(it, subtitleCallback, callback)
            }
        }

        return true
    }

    // Sort urls by their type.
    private fun sortUrls (movieJson: JSONObject) : MutableMap<String,MutableList<String>> {

        val groupedUrls = mutableMapOf<String, MutableList<String>>()

        for (key in movieJson.keys()) {
            val source = movieJson.getJSONObject(key)

            for (type in source.keys()) {
                val url = source.getString(type)
                if (url.isNotEmpty() && url!="https://1.multiup.us/player/embed_player.php?vid=&autoplay=no") {
                    if (!groupedUrls.containsKey(type))
                        groupedUrls[type] = mutableListOf()
                    groupedUrls[type]?.add(url)
                }
            }
        }
        return groupedUrls
    }

}
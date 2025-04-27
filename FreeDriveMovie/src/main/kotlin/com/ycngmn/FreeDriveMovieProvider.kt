package com.ycngmn


import com.lagradost.cloudstream3.Actor
import com.lagradost.cloudstream3.ActorData
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
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.newTvSeriesSearchResponse
import com.lagradost.cloudstream3.toRatingInt
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.getQualityFromName
import com.lagradost.cloudstream3.utils.newExtractorLink
import org.json.JSONObject
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

class FreeDriveMovieProvider : MainAPI() {

    override var mainUrl = "https://freedrivemovie.com/"
    override var name = "FreeDriveMovie"
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
    )

    override var lang = "hi" // Bengali, ~Tamil, Korean, English. Whatever, let's say it's Hindi.

    override val hasMainPage = true
    override val hasQuickSearch = true

    override val mainPage = mainPageOf(
        "movies" to "Movies",
        "tvshows" to "TV Shows",
        "genre/bollywood-genre" to "Bollywood",
        "genre/bangla-ge" to "Bangla",
        "genre/south-indian" to "South Indian",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest
    ): HomePageResponse {

        val home = app.get("$mainUrl/${request.data}/page/$page").document
                .select("article.item").toList()
                .filter { it.select(".genres a")
                    .none { link -> link.text() == "Adult" } } // Adult filter from homepages
                .map { toResult(it) }

        return newHomePageResponse(
            HomePageList(request.name, home, isHorizontalImages = false),
            hasNext = true
        )
    }

    private fun createSearchRes(title: String, url: String, thumb: String): SearchResponse {
        val cleanTitle = title.substringBefore(" [")
        return if (url.contains("/tvshows/")) {
            newTvSeriesSearchResponse(cleanTitle, url, TvType.TvSeries).apply {
                posterUrl = thumb
            }
        } else {
            newMovieSearchResponse(cleanTitle, url, TvType.Movie).apply {
                posterUrl = thumb
            }
        }
    }

    private fun toResult(post: Element): SearchResponse {
        val title = post.selectFirst(".title")?.text() ?: ""
        val url = post.selectFirst("a")?.attr("href") ?: ""
        val thumb = post.selectFirst(".poster img")
            ?.attr("src") ?: ""
        return createSearchRes(title, url, thumb)
    }

    private fun toSearchResult(post: JSONObject): SearchResponse {

        return createSearchRes(
            title = post.getString("title"),
            url = post.getString("url"),
            thumb = post.getString("img")
        )
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> {
        return search(query)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val doc = app.get("$mainUrl/wp-json/dooplay/search/?keyword=$query&nonce=322292fe34").text
        val jsonObject = JSONObject(doc)

        val filteredJsonKeys = mutableListOf<String>() // Adult filtered

        for (key in jsonObject.keys()) {

            val url = jsonObject.getJSONObject(key).getString("url")

            val a = app.get(url).document
            val hasAdultTag = a.select(".sgeneros a").any { link -> link.text() == "Adult" }

            if (!hasAdultTag) {
                filteredJsonKeys.add(key)
            }
        }

        return filteredJsonKeys.map { toSearchResult(jsonObject.getJSONObject(it)) }
    }



    override suspend fun load(url: String): LoadResponse {

        val doc = app.post(url, cacheTime = 60).document
        val title = doc.selectFirst(".data h1")?.text() ?: ""
        val image = doc.selectFirst(".galeria a")
            ?.attr("href")
        val synopsis = doc.selectFirst("#info p")?.ownText()
            ?.removeSuffix(".")?.substringBeforeLast(".") + "."
        val type = if (url.contains("/tvshows/")) TvType.TvSeries
            else TvType.Movie
        val genres = doc.select(".sgeneros a").map { it.text() }
        val rating = doc.selectFirst("#repimdb strong")
            ?.text()?.toRatingInt()
        val duration = doc.selectFirst(".runtime")?.text()
            ?.split(" ")?.getOrNull(0)?.toIntOrNull()
        val releasedYear = doc.selectFirst(".date")?.text()
            ?.split(",")?.getOrNull(1)?.trim()?.toIntOrNull()
        val actors = doc.select(".persons").last()
            ?.select(".person")
            ?.map { ActorData(
                Actor(
                    it.text(),
                    it.selectFirst("img")?.attr("src")
                )
            ) }

        val episodes = mutableListOf<Episode>()

        if (type == TvType.TvSeries) {
            val seasonContainers = doc.select(".episodios")

            // Version 1 for VF, 2 for VOSTFR. Seasons are separate card.
            seasonContainers.forEachIndexed { i, seasonContainer ->
                seasonContainer.select("li").forEachIndexed { j, it ->
                    val date = it.selectFirst(".date")?.text() ?: ""
                    episodes += newEpisode(
                        data = it.selectFirst("a")
                            ?.attr("href") ?: "") {
                        name = it.selectFirst("a")?.text()
                        posterUrl = it.selectFirst("img")?.attr("src")
                        this.date = SimpleDateFormat(
                            "MMM. dd, yyyy",
                            Locale.ENGLISH
                        ).parse(date)?.time
                        episode = j + 1
                        season = i + 1
                    }
                }

            }

            return newTvSeriesLoadResponse(title,url,TvType.TvSeries, episodes) {
                this.posterUrl = image
                this.plot = synopsis
                this.tags = genres
                this.duration = duration
                this.year = releasedYear
                this.rating = rating
                this.actors = actors
            }
        }

        else {
            return newMovieLoadResponse(title,url,TvType.Movie, url) {
                this.posterUrl = image
                this.plot = synopsis
                this.tags = genres
                this.year = releasedYear
                this.duration = duration
                this.rating = rating
                this.actors = actors
            }
        }

    }


    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {

        val source = app.get(data).document
            .selectFirst("img[src=\"https://s2.googleusercontent.com/s2/favicons?domain=dl.freedrivemovie.com\"] + a")?.attr("href") ?: ""
        val sourceUrl = app.get(source).document
            .selectFirst("#link")?.attr("href") ?: ""

        val doc = app.get(sourceUrl).document


        doc.selectFirst(".wp-container-2")?.select("a")?.forEach {

            val req = app.get(it.attr("href").replace("gdlink.dev","gdflix.dad")).document
            val vidLink = req.selectFirst(".btn.btn-outline-success")?.attr("href")

            callback.invoke(
                newExtractorLink(
                    name,
                    "GDFlix",
                    vidLink ?: "",
                ) { quality = getQualityFromName(it.text()) }
            )
        }

        doc.selectFirst(".wp-container-1")?.select("a")?.forEach {
            callback.invoke(
                newExtractorLink(
                    name,
                    "G-Drive Direct",
                    it.attr("href"),
                ) { getQualityFromName(it.text()) }
            )
        }

        return true
    }
}
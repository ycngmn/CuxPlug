package com.ycngmn


import com.lagradost.cloudstream3.DubStatus
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
import com.lagradost.cloudstream3.getQualityFromString
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.newAnimeSearchResponse
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.nicehttp.NiceResponse
import okhttp3.FormBody
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.util.EnumSet

class FrenchStreamProvider : MainAPI() {

    override var mainUrl = "https://french-stream.one"
    private val animeUrl = "https://w14.french-manga.net"
    override var name = "French-Stream"
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries
    )

    override var lang = "fr" // French

    override val hasMainPage = true
    override val hasQuickSearch = true


    override val mainPage = mainPageOf(
        "films" to "Derniers Films",
        "s-tv" to "Dernières Séries",
        "films/top-film" to "Box Office Film",
        "sries-du-moment" to "Box Office Série",
        "netflix-series-" to "Nouveautés NETFLIX",
        "series-apple-tv" to "Nouveautés Apple TV+",
        "series-disney-plus" to "Nouveautés Disney+",
        "serie-amazon-prime-videos" to "Nouveautés Prime Video"
    )

    private var currentRetries = 0

    private suspend fun getHome(page: Int, request: MainPageRequest): NiceResponse =
         if (request.name.contains("Anime")) app.get("$animeUrl/index.php?cstart=$page&do=cat&category=${request.data}")
         else app.get("$mainUrl/${request.data}/page/$page")

    override suspend fun getMainPage(page: Int, request: MainPageRequest
    ): HomePageResponse {

        var home = getHome(page, request)
        while (!home.isSuccessful) home = getHome(page, request)

        val results = home.document.select(".short").map { toResult(it) }
        return newHomePageResponse(
            HomePageList(
                request.name,
                results,
                isHorizontalImages = false
            ),
            hasNext = true
        )
    }

    private fun toResult(post: Element): SearchResponse {

        val title = post.selectFirst(".short-title")?.text() ?: ""
        var url = post.selectFirst(".short-poster.img-box.with-mask")?.attr("href") ?: ""
        if (url.startsWith("/")) url = mainUrl + url
        var thumb = post.selectFirst("img")?.attr("src") ?: ""
        if (thumb.isEmpty() || thumb.startsWith("data:"))
            thumb = post.selectFirst("img")?.attr("data-src") ?: ""
        if (thumb.isNotEmpty() && thumb.startsWith("/"))
            thumb = mainUrl + thumb

        val vfStatus = (post.selectFirst(".film-version") ?:
            post.selectFirst(".film-verz"))?.text() ?: ""
        val epiNum = post.selectFirst(".mli-eps")?.ownText()
            ?.removeSurrounding("\"")?.trim()?.toIntOrNull()
            ?: post.selectFirst(".mli-eps i")?.text()?.toIntOrNull() ?: 0

        return newAnimeSearchResponse(title, url, TvType.Movie) {
            this.posterUrl = thumb
            if (vfStatus.contains("VF") || vfStatus.contains("french",ignoreCase = true)) {
                this.dubStatus = EnumSet.of(DubStatus.Dubbed)
                if (epiNum > 0) this.episodes = mutableMapOf(DubStatus.Dubbed to epiNum)
            }
            else if (vfStatus.contains("VOSTFR")) {
                this.dubStatus = EnumSet.of(DubStatus.Subbed)
                if (epiNum > 0) this.episodes = mutableMapOf(DubStatus.Subbed to epiNum)
            }
            this.quality = getQualityFromString(post.selectFirst(".film-quality")?.text())

        }
    }

    override suspend fun search(query: String): List<SearchResponse> =
        app.get("$mainUrl/index.php?story=$query&do=search&subaction=search")
            .document.select(".short").map { toResult(it) }

    /** Enforce the new interface*/
    private suspend fun loadReq (url: String) : NiceResponse {
        return app.post(
            url = url,
            headers = mapOf("content-type" to "application/x-www-form-urlencoded"),
            data = mapOf("skin_name" to "VFV2", "action_skin_change" to "yes")
        )
    }

    /**
     * Example input: https://fs18.lol/15123747-avatar-de-feu-et-de-cendres.html
     */
    private suspend fun getMovieSource(url: String): String {
        val contentPath = url.substringAfterLast("/").substringBeforeLast(".")
        val contentId = contentPath.substringBefore("-")

        val srcUrl = "$mainUrl/engine/ajax/film_api.php?id=$contentId"
        return app.get(srcUrl).body.string()
    }

    private suspend fun getSeriesSource(url: String): String {
        val contentPath = url.substringAfterLast("/").substringBeforeLast(".")
        val contentId = contentPath.substringBefore("-")

        val srcUrl = "$mainUrl/engine/ajax/get_seasons.php.php"
        return app.post(
            srcUrl,
            headers = mapOf("content-type" to "application/x-www-form-urlencoded"),
            requestBody = FormBody.Builder().add("serie_tag", contentId).build()
        ).body.string()
    }

    override suspend fun load(url: String): LoadResponse {
        var req = loadReq(url)
        while (!req.isSuccessful && currentRetries < 4) {
            req = loadReq(url)
            currentRetries++
        }

        val doc = req.document

        val title = doc.selectFirst("#s-title")?.ownText()
            ?.removeSurrounding("\"")?.trim() ?: ""

        val contentType = TvType.Movie

        val synopsis = if (contentType == TvType.TvSeries) doc.selectFirst(".fdesc")?.text()
            else doc.selectFirst("#s-desc")?.ownText()
        val infoContainer = doc.selectFirst("div.facts")
        val releaseYear = infoContainer?.selectFirst("span.release")
            ?.text()?.substringBefore("-")?.trim()
        val genres = (infoContainer?.selectFirst("span.genres")?.text()
            ?: doc.selectFirst("#s-list")?.ownText())
            ?.trim()?.split("','")?.map{ it.trim() }
        val duration = infoContainer?.selectFirst("span.runtime")?.text()
            ?.trim()?.substringBefore(" ")

        val posterRegex = Regex("""url\((https?://\S+)\)""")
        val image = posterRegex.find(doc.toString())?.groupValues?.get(1)
            ?: doc.selectFirst(".fposter img")?.attr("src") ?: ""


        if (contentType == TvType.TvSeries) {

            val vfEpisodes = mutableListOf<Episode>()
            val voEpisodes = mutableListOf<Episode>()

            val episodeLists = doc.select("script[type=\"text/template\"]")
                .map { Jsoup.parse(it.data()) }

            episodeLists.forEachIndexed { sourceIndex, episodeList ->

                val episodeContainers = episodeList.select(".episode-container")

                episodeContainers.forEachIndexed { epiIndex, episodeContainer ->

                    val epiTitle = episodeContainer.selectFirst(".episode-title")
                        ?.text()?.substringAfter(":") ?: ""
                    val epiImage = episodeContainer.selectFirst(".episode-image")
                        ?.attr("src") ?: image
                    val epiSynopsis =
                        episodeContainer.selectFirst(".episode-synopsis")?.text() ?: ""
                    val epiVF = episodeContainer.selectFirst("[data-episode*=\"-vf\"]")
                        ?.attr("data-url") ?: ""
                    val epiVO = episodeContainer
                        .selectFirst("[data-episode*=\"-vo\"]")
                        ?.attr("data-url") ?: ""

                    if (sourceIndex == 0) {
                        vfEpisodes += newEpisode(data = epiVF) {
                            name = epiTitle
                            posterUrl = epiImage
                            episode = epiIndex + 1
                            season = 1
                            description = epiSynopsis
                        }

                        voEpisodes += newEpisode(data = epiVO) {
                            name = epiTitle
                            posterUrl = epiImage
                            episode = epiIndex + 1
                            season = 2
                            description = epiSynopsis
                        }
                    }
                    else {
                        if (epiVF.isNotEmpty()) vfEpisodes[epiIndex].data += " $epiVF"
                        if (epiVO.isNotEmpty()) voEpisodes[epiIndex].data += " $epiVO"
                    }
                }

            }

            val episodes = (vfEpisodes + voEpisodes).filter { it.data.isNotEmpty() }

            return newTvSeriesLoadResponse(title, url, TvType.TvSeries,episodes) {
                this.posterUrl = image
                this.plot = synopsis
                this.tags = genres
                this.duration = duration?.toIntOrNull()
                this.year = releaseYear?.toIntOrNull()
                addSeasonNames(listOf("VF","VOSTFR"))
            }

        } else {
            val movieJson = JSONObject(getMovieSource(url)).getJSONObject("players")
            val sortedMJ = sortUrls(movieJson)

            val movieEpisodes = mutableListOf<Episode>()
            val versionNames = mutableListOf<String>()
            var index = 0
            for ((version, links) in sortedMJ)  {
                index++
                versionNames += version
                movieEpisodes += newEpisode(data = links.toString() ) {
                    name = "$title [$version]"
                    posterUrl = image
                    season = index
                    episode = 1
                }
            }

            return newTvSeriesLoadResponse(title,url,TvType.TvSeries, movieEpisodes) {
                this.posterUrl = image
                this.plot = synopsis
                this.tags = genres
                this.duration = duration?.toIntOrNull()
                this.year = releaseYear?.toIntOrNull()
                addSeasonNames(versionNames)
            }
        }

    }

    private suspend fun extractVid(vidUrl : String) : String {
        return if (vidUrl.contains("flixeo.xyz"))
            app.head(vidUrl, allowRedirects = false).headers["Location"] ?: ""
            else vidUrl
    }


    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {

        val urls = when {
            data.startsWith("[") -> data.removeSurrounding("[", "]").split(", ").map { it.trim() }
            data.contains("class=\"fstab\"") -> Jsoup.parse(data).select(".fsctab").map { it.attr("href")  }
            else -> data.split(" ")
        }

        urls.forEach {
            loadExtractor(
                extractVid(it),
                subtitleCallback,
                callback
            )
        }

        return true
    }

    /** Sort movie urls by their version name */
    private fun sortUrls (movieJson: JSONObject) : MutableMap<String,MutableList<String>> {
        val groupedUrls = mutableMapOf<String, MutableList<String>>()

        for (key in movieJson.keys()) {
            val source = movieJson.getJSONObject(key)

            for (type in source.keys()) {
                val url = source.getString(type)
                if (url.isNotEmpty()) {
                    if (!groupedUrls.containsKey(type))
                        groupedUrls[type] = mutableListOf()
                    groupedUrls[type]?.add(url)
                }
            }
        }

        return groupedUrls
    }

}
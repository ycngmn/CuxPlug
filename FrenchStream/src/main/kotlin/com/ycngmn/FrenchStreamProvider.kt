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
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.nicehttp.NiceResponse
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.select.Elements
import java.util.EnumSet
import java.util.regex.Pattern

class FrenchStreamProvider : MainAPI() {

    // dynamically get mainUrl from https://fstream.one/ if unstable.
    override var mainUrl = "https://french-stream.pink"
    private val animeUrl = "https://w14.french-manga.net"
    override var name = "French-Stream"
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
        TvType.Anime,
    )

    override var lang = "fr" // French

    override val hasMainPage = true
    override val hasQuickSearch = false


    override val mainPage = mainPageOf(
        "films" to "Derniers Films",
        "s-tv" to "Dernieres Séries",
        "manga-streaming-1" to "Derniers Animes",
        "coups-de-cur" to "Coups de Cœur - Anime",
        "netflix-series-" to "Nouveautés NETFLIX",
        "series-apple-tv" to "Nouveautés Apple TV+",
        "series-disney-plus" to "Nouveautés Disney+",
        "serie-amazon-prime-videos" to "Nouveautés Prime Video",
        "sries-du-moment" to "Box Office Série",
        "" to "Box Office Film"
    )

    //https://w14.french-manga.net/index.php?cstart=2&do=cat&category=manga-streaming-1

    private suspend fun getHome(page: Int, request: MainPageRequest): NiceResponse {
        return if (request.name.contains("Anime"))
            app.get("$animeUrl/index.php?cstart=$page&do=cat&category=${request.data}") else
            app.get("$mainUrl/${request.data}/page/${if (request.data=="") page+1 else page}")
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest
    ): HomePageResponse {

        var home = getHome(page, request)

        // The site has rate limit enabled. Tries to counter.
        while (home.document.selectFirst(".short") == null) {
            home =getHome(page, request)
        }

        return newHomePageResponse(
            HomePageList(request.name,
                home.document.select(".short").map { toResult(it) },
                isHorizontalImages = false), hasNext = true
        )
    }


    private fun toResult(post: Element): SearchResponse {

        val title = post.selectFirst(".short-title")?.text() ?: ""
        var url = post.selectFirst(".short-poster.img-box.with-mask")?.attr("href") ?: ""
        if (url.startsWith("/")) url = mainUrl + url
        var thumb = post.selectFirst("img")?.attr("src") ?: ""
        if (thumb.isEmpty() || thumb.startsWith("data:"))
            thumb = post.selectFirst("img")?.attr("data-src") ?: ""
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

    private suspend fun fetchSearchResults(url: String, query: String): Elements {
        return app.get("$url/index.php?story=$query&do=search&subaction=search")
            .document.select(".short")
    }

    override suspend fun search(query: String): List<SearchResponse> {

        val searchItems = fetchSearchResults(mainUrl, query) + fetchSearchResults(animeUrl, query)
        return searchItems.map { toResult(it) }
    }
    override suspend fun load(url: String): LoadResponse {

        // working with the new interface.
        val doc =  app.post(url,
            headers = mapOf("content-type" to "application/x-www-form-urlencoded"),
            data = mapOf("skin_name" to "VFV2","action_skin_change" to "yes" )
        ).document



        val contentType = if (url.contains("/films/")) TvType.Movie
        else if (url.contains("french-manga.net")) TvType.Anime
        else TvType.TvSeries

        val synopsis = if (contentType == TvType.TvSeries) doc.selectFirst(".fdesc")?.text()
            else doc.selectFirst("#s-desc")?.ownText()
        val infoContainer = doc.selectFirst("div.facts")
        val releaseYear = infoContainer?.selectFirst("span.release")
            ?.text()?.substringBefore("-")?.trim()
        val genres = (infoContainer?.selectFirst("span.genres")?.text()
            ?: doc.selectFirst("#s-list")?.ownText())
            ?.trim()?.split("${if (contentType == TvType.Anime) '-' else ','}")?.map{ it.trim() }
        val duration = infoContainer?.selectFirst("span.runtime")?.text()
            ?.trim()?.substringBefore(" ")

        val title = doc.selectFirst("#s-title")?.ownText()
            ?.removeSurrounding("\"")?.trim() ?: ""


        val posterRegex = Regex("""url\((https?://\S+)\)""")
        val image = posterRegex.find(doc.toString())?.groupValues?.get(1)
            ?: doc.selectFirst(".fposter img")?.attr("src") ?: ""

        if (contentType == TvType.Anime) {
            val episodes = mutableListOf<Episode>()
            val versionContainers = doc.select(".elink")

            // Version 1 for VF, 2 for VOSTFR. Seasons are separate card.
            versionContainers.forEachIndexed { i, versionContainer ->
                versionContainer.select("a").forEachIndexed { j, it ->
                    val dataRel = it.attr("data-rel")
                    if (dataRel.isNotEmpty())
                        episodes += Episode(
                            data = doc.selectFirst("#$dataRel")?.toString() ?: "",
                            posterUrl = image,
                            episode = j+1,
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


        val vfEpisodes = mutableListOf<Episode>()
        val voEpisodes = mutableListOf<Episode>()

        if (contentType == TvType.TvSeries) {

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

                        vfEpisodes += Episode(
                            name = epiTitle,
                            data = epiVF,
                            posterUrl = epiImage,
                            episode = epiIndex + 1,
                            season = 1,
                            description = epiSynopsis
                        )

                        voEpisodes += Episode(
                            name = epiTitle,
                            data = epiVO,
                            posterUrl = epiImage,
                            episode = epiIndex + 1,
                            season = 2,
                            description = epiSynopsis
                        )
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
        }

        else {

            val regex = Pattern.compile("""playerUrls\s*=\s*(\{.*?\});""", Pattern.DOTALL)
            val matcher = regex.matcher(doc.toString())
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


        if (!data.startsWith("[")) { // series

            if (data.contains("class=\"fstab\"")) { // Anime
                Jsoup.parse(data).select(".fsctab").forEach {
                    // create extractor kt
                    val vidSrc = it.attr("href")
                    loadExtractor(extractVid(vidSrc), subtitleCallback, callback)
                }
            }

            else {
                val sources = data.split(" ")
                sources.forEach {
                    loadExtractor(extractVid(it), subtitleCallback, callback)
                }
            }

        }

        else { // Movie
            val urls = data.removeSurrounding("[", "]").split(", ").map { it.trim() }
            urls.forEach {
                loadExtractor(extractVid(it), subtitleCallback, callback)
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
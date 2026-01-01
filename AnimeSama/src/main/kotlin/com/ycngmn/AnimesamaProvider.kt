package com.ycngmn

import com.lagradost.cloudstream3.DubStatus
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SeasonData
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.addEpisodes
import com.lagradost.cloudstream3.addSeasonNames
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.newAnimeLoadResponse
import com.lagradost.cloudstream3.newAnimeSearchResponse
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.nodes.Element


class AnimesamaProvider : MainAPI() {

    override var mainUrl = "https://anime-sama.tv"
    override var name = "Anime-sama"
    override val supportedTypes = setOf(
        TvType.Anime,
        TvType.AnimeMovie
    )

    override var lang = "fr"
    override val hasMainPage = true
    override val hasQuickSearch = true

    override val mainPage = mainPageOf(
        "1" to "Derniers épisodes ajoutés",
        "2" to "Derniers contenus sortis",
        "3" to "Les classiques",
        "4" to "Découvrez des pépites",
    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val doc = app.get(mainUrl, cacheTime = 60).document

        val query = when (request.data) {
            "1" -> "#containerAjoutsAnimes a"
            "2" -> "#containerSorties a"
            "3" -> "#containerClassiques a"
            "4" -> "#containerPepites a"
            else -> ""
        }
        val home = doc.select(query).mapNotNull { toResult(it) }

        return newHomePageResponse(
            HomePageList(request.name, home, isHorizontalImages = true),
            false
        )
    }


    private fun toResult(post: Element): SearchResponse {
        var title = post.selectFirst("h1")?.text() ?: ""
        if (title == "")
            title = post.selectFirst("h3")?.text() ?: ""
        var url = post.selectFirst("a")?.attr("href") ?: ""
        url = if (url.split("/").size > 5) url.split("/").take(5).joinToString("/")
        else url
        return newAnimeSearchResponse(title, url, TvType.Anime) {
            this.posterUrl = post.selectFirst("img")
                ?.attr("src")

        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val doc = app.post(
            "$mainUrl/template-php/defaut/fetch.php",
            data = mapOf("query" to query),
            cacheTime = 60
        ).document
        return doc.select("a").mapNotNull { toResult(it) }
    }

    override suspend fun load(url: String): LoadResponse {


        val doc = app.get(url, cacheTime = 60).document
        val title = doc.selectFirst("h4#titreOeuvre")?.text()
            ?: throw NotImplementedError("Unable to find title")
        val otherTitles =
            doc.selectFirst("#titreAlter")?.text()?.split(",")?.map { it.trim() } ?: listOf()
        val image = doc.selectFirst("#coverOeuvre")?.attr("src")
        val tags =
            doc.selectFirst("a.text-sm.text-gray-300.mt-2")?.text()?.split(",")?.map { it.trim() }
                ?: listOf()
        val synopsis = doc.selectFirst("p.text-sm.text-gray-400.mt-2")?.text() ?: ""

        // Pair of ( seasonName : href )
        val rawSeasonData =
            doc.selectFirst("div.flex.flex-wrap.overflow-y-hidden.justify-start.bg-slate-900.bg-opacity-70.rounded.mt-2.h-auto script")
                ?.toString() ?: ""
        val extractedData = rawSeasonData.split("/*", "*/")[2]
        val pattern = Regex("""panneauAnime\("([^"]+)",\s*"([^"]+)"\);""")
        val panneauMap = mutableMapOf<String, String>()
        pattern.findAll(extractedData).forEach {
            val season = it.groupValues[1]
            val alias = it.groupValues[2]
            panneauMap[season] = alias
        }

        val seasonList = mutableListOf<SeasonData>()
        var index = 1
        panneauMap.forEach { (season, _) ->
            seasonList.add(SeasonData(index, season))
            index++
        }

        var season = 1
        val episodeList = mutableListOf<Episode>()
        val vfEpisodeList = mutableListOf<Episode>()

        panneauMap.forEach { (seasonName, alias) ->
            val streamPage = "$url/$alias"
            val urlTransforme = streamPage.removeSuffix("/").split("/").toMutableList()

            var asSources : Map<String, List<String>> = mapOf()
            val asSourcesVF : Map<String, List<String>>

            if (urlTransforme[urlTransforme.size - 1] != "vf") {
                asSources = retreiveSrcs(streamPage)
                urlTransforme[urlTransforme.size - 1] = "vf"
                val vfPage = urlTransforme.joinToString("/")
                asSourcesVF = retreiveSrcs(vfPage)

            } else {
                asSourcesVF = retreiveSrcs(streamPage)
            }

            val maxNbEpisodes = asSources.values.maxOfOrNull { it.size }
                ?: asSourcesVF.values.maxOfOrNull { it.size } ?: 0


            for (i in 0 until maxNbEpisodes) {

                val nom = when {
                    seasonName.contains("Saison") -> "$title S${season}EP${(i + 1).toString().padStart(2, '0')}"
                    seasonName in listOf("Film", "OAV", "Films") -> "$title $seasonName ${i + 1}"
                    else -> "$title ${i + 1}"
                }

                var datas = ""
                for ((_,link) in asSources) {
                    if (i < link.size)
                        datas += " " + link[i]
                }

                if (datas!="") {

                    episodeList.add(
                        newEpisode(datas) {
                            this.apply {
                                name = nom
                                episode = i + 1
                                posterUrl = image
                                this.season = season
                            }
                        }
                    )
                }


                datas = ""
                for ((_,link) in asSourcesVF) {
                    if (i < link.size){
                        datas += " " + link[i] }
                }
                if (datas!="") {
                    vfEpisodeList.add(

                        newEpisode(datas) {
                            this.apply {
                                name = nom
                                episode = i + 1
                                posterUrl = image
                                this.season = season
                            }
                        }
                    )
                }
            }
            season++
        }

        return newAnimeLoadResponse(title, url, TvType.Anime) {

            this.posterUrl = image
            this.plot = synopsis
            this.tags = tags
            this.synonyms = otherTitles
            addSeasonNames(seasonList)
            addEpisodes(DubStatus.Subbed, episodeList)
            if (vfEpisodeList.isNotEmpty())
                addEpisodes(DubStatus.Dubbed, vfEpisodeList)

        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {

        val links = data.removePrefix(" ").split(" ")

        for (link in links)
            loadExtractor(link, subtitleCallback, callback)

        return true
    }

    /**
     * Extracts the stream host and episode URLs from AnimeSama stream pages.
     *
     * @param streamPage The AnimeSama URL to scrape from.
     * Example:
     * ```
     * https://anime-sama.tv/catalogue/anime-name/saison0/vostfr/
     * ```
     * @return A map containing pairs of sources and their corresponding lists of stream links,
     *         or an empty list if no match is found.
     */

    private suspend fun retreiveSrcs(streamPage: String): Map<String, List<String>> {

        val request = app.get(streamPage)

        if (request.isSuccessful) {

            val doc = request.document
            val epiKey = doc.selectFirst("#sousBlocMiddle script").toString()
            val re = Regex("""<script[^>]*src=['"]([^'"]*episodes\.js\?filever=\d+)['"][^>]*>""")
            val episodeKey = re.find(epiKey)?.groupValues?.get(1)
            val rawLinks = app.get("$streamPage/$episodeKey").text
            val reURL = """['"]https?://[^\s'"]+['"]""".toRegex()
            val urls = reURL.findAll(rawLinks)
                .map { it.value.trim('\'', '"') }
                .toList()

            return urls.groupBy { url ->
                when {
                    // here I listed the providers I crossed in AS.

                    url.contains("sibnet.ru") -> "Sibnet"
                    url.contains("vidmoly.to") -> "Vidmoly"
                    url.contains("oneupload.to") -> "Oneupload"
                    url.contains("sendvid.com") -> "Sendvid"
                    url.contains("vk.com") -> "Vk"


                    else -> "Other"
                }
            }

        }

        return mapOf()

    }
}



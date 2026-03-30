package com.ycngmn

import android.util.Log
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


class AnimeSamaProvider : MainAPI() {
    override var mainUrl = "https://anime-sama.to"
    override var name = "Anime Sama"
    override val supportedTypes = setOf(
        TvType.Anime,
        TvType.AnimeMovie
    )

    override var lang = "fr"
    override val hasMainPage = true
    override val hasQuickSearch = true

    override val mainPage = mainPageOf(
        "#containerAjoutsAnimes a" to "Derniers épisodes ajoutés",
        "#containerSorties a" to "Derniers contenus sortis",
        "#containerClassiques a" to "Les classiques",
        "#containerPepites a" to "Découvrez des pépites",
    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val doc = app.get(mainUrl).document
        val items = doc.select(request.data)
            .mapNotNull { toResult(it) }

        return newHomePageResponse(
            HomePageList(
                request.name,
                items,
                isHorizontalImages = true
            ),
            hasNext = false
        )
    }

    private fun toResult(post: Element): SearchResponse {
        val title = post.selectFirst(".card-title")?.text() ?: ""
        val slug = (post.selectFirst("a")?.attr("href") ?: "")
            .trim('/').split("/").take(2).joinToString("/")

        val url = "$mainUrl/$slug"

        return newAnimeSearchResponse(title, url, TvType.Anime) {
            posterUrl = post.selectFirst("img")?.attr("src")
        }
    }

    private fun toSearchResult(post: Element): SearchResponse {
        val title = post.selectFirst(".asn-search-result-title")?.text() ?: ""
        val url = post.selectFirst("a")?.attr("href") ?: ""

        return newAnimeSearchResponse(title, url, TvType.Anime) {
            posterUrl = post.selectFirst("img")?.attr("src")
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val doc = app.post(
            "$mainUrl/template-php/defaut/fetch.php",
            data = mapOf("query" to query)
        ).document

        return doc.select("a").mapNotNull { toSearchResult(it) }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> {
        return search(query)
    }

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url).document
        val title = doc.selectFirst("#titreOeuvre")?.text() ?: ""
        val otherTitles = doc.selectFirst("#titreAlter")?.text()
            ?.split(",")?.map { it.trim() } ?: listOf()

        val image = doc.selectFirst("#coverOeuvre")?.attr("src")
        val tags = doc.selectFirst("a.text-sm.text-gray-300.mt-2")?.text()
            ?.split(",")?.map { it.trim() } ?: listOf()
        val synopsis = doc.selectFirst("p.text-sm.text-gray-400.mt-2")?.text() ?: ""

        // Pair of ( seasonName : href )
        val rawSeasonSelector = "div.flex.flex-wrap.overflow-y-hidden.justify-start.bg-slate-900.bg-opacity-70.rounded.mt-2.h-auto script"
        val rawSeasonData = doc.selectFirst(rawSeasonSelector)?.toString() ?: ""

        val extractedData = rawSeasonData.split("\n").drop(1).joinToString()

        val pattern = Regex("""panneauAnime\("([^"]+)",\s*"([^"]+)"\);""")

        val panneauMap = pattern.findAll(extractedData).map {
            val season = it.groupValues[1]
            val alias = it.groupValues[2]
            season to alias
        }

        val seasonList: List<SeasonData> = buildList {
            panneauMap.forEachIndexed { index, (season, _) ->
                add(SeasonData(index + 1, season))
            }
        }

        val episodeList = mutableListOf<Episode>()
        val vfEpisodeList = mutableListOf<Episode>()

        panneauMap.forEachIndexed { seasonIndex, (seasonName, slug) ->
            val streamPage = "$url/$slug"

            val urlTransform = streamPage.removeSuffix("/").split("/")
            val isVF = urlTransform.last() == "vf"

            val sourcesVF = if (isVF) {
                extractStreamLinks(streamPage)
            } else {
                val vfPage = urlTransform.toMutableList().apply {
                    this[urlTransform.lastIndex] = "vf"
                }.joinToString("/")
                extractStreamLinks(vfPage)
            }

            val sourcesVO = if (!isVF) extractStreamLinks(streamPage) else emptyMap()


            val maxNbEpisodes = sourcesVO.values.maxOfOrNull { it.size }
                ?: sourcesVF.values.maxOfOrNull { it.size } ?: 0


            for (episodeIndex in 0 until maxNbEpisodes) {

                val nom = when {
                    seasonName.contains("Saison") -> {
                        val episodeString = (episodeIndex + 1).toString().padStart(2, '0')
                        "$title S${seasonIndex + 1}EP$episodeString"
                    }
                    seasonName in listOf("Film", "OAV", "Films") ->
                        "$title $seasonName ${episodeIndex + 1}"
                    else -> "$title ${episodeIndex + 1}"
                }


                val datas = buildString {
                    for ((_,link) in sourcesVO) {
                        if (episodeIndex < link.size) {
                            append(link[episodeIndex])
                            append(" ")
                        }
                    }
                }

                if (datas.isNotEmpty()) {
                    episodeList.add(
                        newEpisode(datas) {
                            this.apply {
                                name = nom
                                episode = episodeIndex + 1
                                posterUrl = image
                                this.season = seasonIndex + 1
                            }
                        }
                    )
                }

                val datas2 = buildString {
                    for ((_,link) in sourcesVF) {
                        if (episodeIndex < link.size) {
                            append(link[episodeIndex])
                            append(" ")
                        }
                    }
                }

                if (datas2.isNotEmpty()) {
                    vfEpisodeList.add(
                        newEpisode(datas2) {
                            name = nom
                            episode = episodeIndex + 1
                            posterUrl = image
                            season = seasonIndex + 1
                        }
                    )
                }
            }
        }

        return newAnimeLoadResponse(title, url, TvType.Anime) {
            posterUrl = image
            plot = synopsis
            synonyms = otherTitles
            this.tags = tags

            addSeasonNames(seasonList)
            addEpisodes(DubStatus.Subbed, episodeList)

            if (vfEpisodeList.isNotEmpty()) {
                addEpisodes(DubStatus.Dubbed, vfEpisodeList)
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {

        val links = data.trim().split(" ")

        for (link in links) {
            loadExtractor(
                link,
                subtitleCallback,
                callback
            )
        }

        return true
    }

    private suspend fun extractStreamLinks(streamPage: String): Map<String, List<String>> {

        val request = app.get(streamPage)

        if (!request.isSuccessful) return mapOf()

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
                url.contains("sibnet.ru") -> "Sibnet"
                url.contains("embed4me.com") -> "Embed4Me"
                url.contains("vidmoly.to") -> "Vidmoly"
                url.contains("oneupload.to") -> "Oneupload"
                url.contains("sendvid.com") -> "Sendvid"
                url.contains("vk.com") -> "Vk"
                else -> "Other"
            }
        }
    }
}
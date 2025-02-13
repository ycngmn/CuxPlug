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
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.utils.ExtractorLink
import org.jsoup.nodes.Element

class StreamCloudProvider : MainAPI() {

    override var mainUrl = "https://streamcloud.my"
    override var name = "StreamCloud"
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
    )

    override var lang = "de" // German

    override val hasMainPage = true
    override val hasQuickSearch = false



    override val mainPage = mainPageOf(
        "kinofilme" to "Kinofilme",
        "serien" to "Serien",
        "xfsearch/deutschland" to "Deutschland"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest
    ): HomePageResponse {

        val home = app.get("$mainUrl/${request.data}/page/$page").document
            .select("#dle-content > div").dropLast(1)
            .map { toResult(it) }

        return newHomePageResponse(
            HomePageList(request.name, home, isHorizontalImages = false),
            hasNext = true
        )
    }


    private fun toResult(post: Element): SearchResponse {
        val title = post.selectFirst(".f_title")?.text() ?: ""
        val url = post.selectFirst("a")?.attr("href") ?: ""
        val thumb = post.selectFirst("img")?.attr("src")

        return newMovieSearchResponse(title, url, TvType.Movie) {
            this.posterUrl = mainUrl + thumb

        }
    }

    override suspend fun search(query: String): List<SearchResponse> {

        val doc = app.get("$mainUrl/?do=search&subaction=search&story=$query").document
        return doc.select("#dle-content > div").dropLast(1)
            .map { toResult(it) }
    }


    override suspend fun load(url: String): LoadResponse {

        val doc = app.get(url, cacheTime = 60).document
        val title = doc.selectFirst(".title")?.text() ?: ""
        val image = mainUrl + doc.selectFirst("img[itemprop=\"image\"]")?.attr("src")
        val bgRegex = """url\((.*?)\)""".toRegex() // regex for image url inside style attr
        val matchResult = bgRegex.find(doc.selectFirst("style[media=\"screen\"]")?.data() ?: "")
        var coverImage =  mainUrl + matchResult?.groups?.get(1)?.value?.removeSurrounding("\"")
        if (coverImage == "") coverImage = image
        val synopsis = doc.selectFirst("#storyline")?.text() ?: ""
        val type = if (doc.selectFirst("#se-accordion") == null) TvType.Movie else TvType.TvSeries

        val rowLines = doc.selectFirst("#longInfo")
            ?.select("#storyline ~ div")
            ?.map { it.text().substringAfter(":").trim() } ?: listOf()

        val genres = rowLines.getOrNull(if (type==TvType.Movie) 0 else 4)
            ?.split("/")?.map { it.trim() }

        val releasedYear = rowLines.getOrNull(if (type==TvType.Movie) 1 else 5)

        val duration = rowLines.getOrNull(if (type==TvType.Movie) 4 else 3)
            ?.substringBefore(" ")?.trim()


        val movieUrlData: String
        val episodes = mutableListOf<Episode>()

        if (type == TvType.TvSeries) {
            val seasonContainers = doc.select(".su-spoiler.su-spoiler-style-default.su-spoiler-icon-arrow")

            // there are several sources but this one is probably easier to play.
            seasonContainers.forEachIndexed { i, seasonContainer ->
                seasonContainer.select("a").filterNot { it.text()!= "Supervideo"}.forEach {
                    episodes+= Episode(
                        data = it.attr("href"),
                        posterUrl = image,
                        season = i+1


                    )
                }

            }

            return newTvSeriesLoadResponse(title,url,TvType.TvSeries, episodes) {
                this.backgroundPosterUrl = coverImage
                this.posterUrl = image
                this.plot = synopsis
                this.tags = genres
                this.year = releasedYear?.toIntOrNull()
                this.duration = duration?.toIntOrNull()

            }
        }

        else {
            val req = app.get(doc.selectFirst("iframe")?.attr("src") ?: "").document
            movieUrlData = ("https:" + req.selectFirst("li")?.attr("data-link"))

            return newMovieLoadResponse(title,url,TvType.Movie, movieUrlData) {
                this.backgroundPosterUrl = coverImage
                this.posterUrl = image
                this.plot = synopsis
                this.tags = genres
                this.year = releasedYear?.toIntOrNull()
                this.duration = duration?.toIntOrNull()
            }
        }

    }


    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {

        val raw = app.get(data).text.replace("$","")
        val m3u8Data = Regex("""\|image\|(.*?)\|sources\|""").find(raw)?.groupValues?.get(1)?.split("|")?.reversed()
        val m3u8 = "https://${m3u8Data?.getOrNull(0)}.serversicuro.cc/" +
                "${m3u8Data?.getOrNull(1)}/" +
                ",${m3u8Data?.getOrNull(2)},.urlset/master.m3u8"




        callback.invoke(
            ExtractorLink(
                name,
                "Supervideo",
                m3u8,
                "",
                0,
                isM3u8 = true
            )
        )


        return true
    }

}
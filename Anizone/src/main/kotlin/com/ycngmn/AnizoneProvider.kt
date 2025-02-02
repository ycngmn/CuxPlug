package com.ycngmn



import com.lagradost.cloudstream3.DubStatus
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.ShowStatus
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.addEpisodes
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.newAnimeLoadResponse
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import org.json.JSONObject
import org.jsoup.Connection
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale


class AnizoneProvider : MainAPI() {

    override var mainUrl = "https://anizone.to"
    override var name = "AniZone"
    override val supportedTypes = setOf(
        TvType.Anime,
        TvType.AnimeMovie,
    )

    override var lang = ""

    override val hasMainPage = true
    override val hasQuickSearch = true
    override val hasDownloadSupport = true

    override val mainPage = mainPageOf(
        "2" to "Latest TV Series",
        "4" to "Latest Movies",
        "6" to "Latest Web"
    )

    private val initReq = Jsoup.connect("$mainUrl/anime")
        .method(Connection.Method.GET).execute()

    private var cookies = initReq.cookies()
    private val doc = initReq.parse()
    private val token = doc.select("script[data-csrf]").attr("data-csrf")
    private var wireSnapshot = getSnapshot(doc)


    private fun sortAnimeLatest() {
       liveWireBuilder(mapOf("sort" to "release-desc"), mutableListOf(), true)
    }


    private fun getSnapshot(doc : Document) : String {
        return doc.select("main div[wire:snapshot]")
            .attr("wire:snapshot").replace("&quot;", "\"")
    }
    private fun getSnapshot(json : JSONObject) : String {
        return json.getJSONArray("components")
            .getJSONObject(0).getString("snapshot")
    }

    private  fun getHtmlFromWire(json: JSONObject): Document {
        return Jsoup.parse(json.getJSONArray("components")
            .getJSONObject(0).getJSONObject("effects")
            .getString("html"))
    }


    private fun liveWireBuilder (updates : Map<String,String>,
                                 calls: List<Map<String, Any>>,
                                 remember : Boolean): JSONObject {

        val payload = mapOf(
            "_token" to token, "components" to listOf(
                mapOf("snapshot" to wireSnapshot, "updates" to updates,
                    "calls" to calls
                )
            )
        )

        val r = Jsoup.connect("$mainUrl/livewire/update")
            .method(Connection.Method.POST)
            .header("Content-Type", "application/json")
            .cookies(cookies)
            .ignoreContentType(true)
            .requestBody(payload.toJson())
            .execute()

        if (remember) {
            this.wireSnapshot = getSnapshot(JSONObject(r.body()))
            this.cookies = r.cookies()
        }

        return JSONObject(r.body())
    }

    // I am a bad coder, now you know 🎵
    // I will put these (token,cookie,Snapshot) inside a map , hopefully
    private fun liveWireBuilder2(updates : Map<String,String>,
                                 calls: List<Map<String, Any>>,
                                 jeton : String,
                                 biscuit: Map<String, String>,
                                 wss : String) : Connection.Response {

        val payload = mapOf("_token" to jeton,
            "components" to listOf(mapOf(
                    "snapshot" to wss, "updates" to updates,
                    "calls" to calls
                )
            )
        )

        val r = Jsoup.connect("$mainUrl/livewire/update")
            .method(Connection.Method.POST)
            .header("Content-Type", "application/json")
            .cookies(biscuit)
            .ignoreContentType(true)
            .requestBody(payload.toJson())
            .execute()

        return r
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest
    ): HomePageResponse {

        sortAnimeLatest() // Just 1 call is enough but where else to put it?
        var home = getHtmlFromWire(liveWireBuilder(mapOf("type" to request.data),mutableListOf(), false))
        if (page>1 && home.selectFirst(".h-12[x-intersect=\"\$wire.loadMore()\"]")!=null)
            home = getHtmlFromWire(liveWireBuilder(mapOf(),mutableListOf(
                mapOf("path" to "", "method" to "loadMore", "params" to listOf<String>())
                ), true
            ))

        return newHomePageResponse(
            HomePageList(request.name, home.select("div[wire:key]").map { toResult(it)}, isHorizontalImages = false),
            hasNext = true
        )
    }


    private fun toResult(post: Element): SearchResponse {
        val title = post.selectFirst("img")?.attr("alt") ?: ""
        val url = post.selectFirst("a")?.attr("href") ?: ""

        return newMovieSearchResponse(title, url, TvType.Movie) {
            this.posterUrl = post.selectFirst("img")
                ?.attr("src")

        }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun search(query: String): List<SearchResponse> {
        val doc = getHtmlFromWire(liveWireBuilder(mapOf("search" to query),mutableListOf(), false))
        return doc.select("div[wire:key]").mapNotNull { toResult(it) }
    }

    override suspend fun load(url: String): LoadResponse {

        val r = Jsoup.connect(url)
            .method(Connection.Method.GET).execute()


        var doc = Jsoup.parse(r.body())

        val jeton = doc.select("script[data-csrf]").attr("data-csrf")
        var cooky = r.cookies()
        var wss = getSnapshot(doc=r.parse()) // ne ps confondre, GET / POST



        val title = doc.selectFirst("h1")?.text()
            ?: throw NotImplementedError("Unable to find title")

        val bgImage = doc.selectFirst("main img")?.attr("src")
        val synopsis = doc.selectFirst(".sr-only + div")?.text() ?: ""

        val rowLines = doc.select("span.inline-block").map { it.text() }
        val releasedYear = rowLines.getOrNull(3)
        val status = if (rowLines.getOrNull(1) == "Completed") ShowStatus.Completed
        else if (rowLines.getOrNull(1) == "Ongoing") ShowStatus.Ongoing else null

        val genres = doc.select("a[wire:navigate][wire:key]").map { it.text() }

        while (doc.selectFirst(".h-12[x-intersect=\"\$wire.loadMore()\"]")!=null) {
            val req = liveWireBuilder2(
                mutableMapOf(), mutableListOf(
                    mapOf("path" to "", "method" to "loadMore", "params" to listOf<String>())
                ), jeton, cooky, wss
            )
            doc = getHtmlFromWire(JSONObject(r.body()))
            cooky = req.cookies()
            wss = getSnapshot(JSONObject(r.body()))
        }

        // doc returns whole episodes including the previous ones.
        // so we iter over it to scrap all at once.
        val epiElts = doc.select("li[x-data]")

        val episodes = epiElts.map{ elt ->
             Episode(
            data = elt.selectFirst("a")?.attr("href") ?: "",
            name = elt.selectFirst("h3")?.text()
                ?.substringAfter(":")?.trim(),
            season = 0,
            posterUrl = elt.selectFirst("img")?.attr("src"),
            date = elt.select("span.line-clamp-1").getOrNull(1)?.text()?.let {
                SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                    .parse(it)?.time
            } ?: 0

        ) }

        return newAnimeLoadResponse(title, url, TvType.Anime) {


            this.posterUrl = bgImage
            this.plot = synopsis
            this.tags = genres
            this.year = releasedYear?.toIntOrNull()
            this.showStatus = status
            //addSeasonNames(seasonList)
            addEpisodes(DubStatus.None, episodes)

        }

    }


    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {


        val web = app.get(data).document
        val sourceName = web.selectFirst("span.truncate")?.text() ?: ""
        val mediaPlayer = web.selectFirst("media-player")
        val m3U8 = mediaPlayer?.attr("src") ?: ""

        mediaPlayer?.select("track")?.forEach {
            subtitleCallback.invoke(
                SubtitleFile (
                    it.attr("label"),
                    it.attr("src")
                )
            )
        }

        callback.invoke(
            ExtractorLink(
                name,
                sourceName,
                m3U8,
                "",
                Qualities.Unknown.value,
                isM3u8 = true
            )
        )

        return true
    }

}
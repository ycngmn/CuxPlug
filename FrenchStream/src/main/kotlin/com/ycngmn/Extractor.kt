package com.ycngmn

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.extractors.Filesim
import com.lagradost.cloudstream3.extractors.Voe
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.JsUnpacker
import com.lagradost.cloudstream3.utils.newExtractorLink
import org.jsoup.nodes.Document

class MaxFinishSeveral : Voe() {
    override val mainUrl = "https://maxfinishseveral.com"
}

class Bf0skv : Filesim() {
    override val name = "FileMoon"
    override val mainUrl = "https://bf0skv.org"
}

open class BigWrap : ExtractorApi() {
    override val name = "BigWrap"
    override val mainUrl = "https://bigwarp.io"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {


        val soup = app.get(url, allowRedirects = true).text
        val fileUrlRegex = Regex("""file:"(https?://[^"]+)"""")
        val mp4Url = fileUrlRegex.find(soup)?.groupValues?.get(1)

        callback.invoke(
            newExtractorLink(
                this.name,
                this.name,
                mp4Url ?: return,
            )
        )

    }

}

open class Fsvid : ExtractorApi() {
    override val name = "Fsvid"
    override val mainUrl = "https://fsvid.lol"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val doc = app.get(url).document

        callback.invoke(
            newExtractorLink(
                source = this.name,
                name = this.name,
                url = getPackedM3u8(doc) ?: return,
                type = ExtractorLinkType.M3U8
            )
        )
    }
}

open class Vidzy : ExtractorApi() {
    override val name = "Vidzy"
    override val mainUrl = "https://vidzy.org"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val doc = app.get(url).document

        callback.invoke(
            newExtractorLink(
                source = this.name,
                name = this.name,
                url = getPackedM3u8(doc) ?: return,
                type = ExtractorLinkType.M3U8
            )
        )
    }
}


private fun getPackedM3u8(doc: Document): String? {
    val packedData = doc.selectFirst("script:containsData(function(p,a,c,k,e,d))")?.data().toString()
    return JsUnpacker(packedData).unpack()?.let {
        val m3u8Regex = """(?:file|src)\s*:\s*"([^"]+\.m3u8[^"]*)"""".toRegex()
        m3u8Regex.find(it)?.groupValues?.getOrNull(1)
    }
}
package com.ycngmn

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.extractors.Voe
import com.lagradost.cloudstream3.extractors.Filesim
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities

class MaxFinishSeveral : Voe() {
    override val mainUrl = "https://maxfinishseveral.com"
}

class Bf0skv : Filesim() {
    override val name = "FileMoon"
    override val mainUrl = "https://bf0skv.org"
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

        val m3u8Regex = """file:\s*"([^"]+\.m3u8[^"]*)"""".toRegex()
        val soup = app.get(url).text

        val m3u8 = m3u8Regex.find(soup)?.groupValues?.getOrNull(1)

        callback.invoke(
            ExtractorLink(
                this.name,
                this.name,
                m3u8 ?: return,
                "",
                Qualities.Unknown.value,
                isM3u8 = true
            )
        )
    }
}
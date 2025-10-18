package com.ycngmn

import android.content.Context
import com.lagradost.cloudstream3.extractors.Lulustream2
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class FrenchStreamPlugin: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(FrenchStreamProvider())
        registerExtractorAPI(MaxFinishSeveral())
        registerExtractorAPI(Bf0skv())
        registerExtractorAPI(Fsvid())
        registerExtractorAPI(Vidzy())
        registerExtractorAPI(Lulustream2())
        registerExtractorAPI(BigWrap())
    }
}
package com.ycngmn

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class AnimesamaPlugin: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(AnimesamaProvider())
    }
}
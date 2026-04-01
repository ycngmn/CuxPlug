plugins {
    id("cloudstream-plugin")
}

cloudstream {
    authors = listOf("TonNomGitHub")   // ← change "TonNomGitHub" par ton vrai nom GitHub
    name = "MovieboxPK"
    description = "Films, séries et TV de moviebox.pk"
    version = 1
    status = 1
    tvTypes = listOf(TvType.Movie, TvType.TvSeries)
    iconUrl = "https://moviebox.pk/favicon.ico"
}

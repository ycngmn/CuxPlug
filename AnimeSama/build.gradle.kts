// use an integer for version numbers
version = 5


cloudstream {
    // All of these properties are optional, you can safely remove them

    description = "Anime-Sama est un site de référencement et de catalogage, créé par des passionnés de l’animation et du divertissement APAC."
    authors = listOf("ycngmn")

    /**
    * Status int as the following:
    * 0: Down
    * 1: Ok
    * 2: Slow
    * 3: Beta only
    * */
    status = 1

    tvTypes = listOf("Anime")

    requiresResources = true
    language = "fr"


    iconUrl = "https://cdn.statically.io/gh/Anime-Sama/IMG/img/autres/logo.png"
}

android {
    buildFeatures {
        viewBinding = true
    }
}

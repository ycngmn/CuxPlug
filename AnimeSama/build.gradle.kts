// use an integer for version numbers
version = 2


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
    status = 2

    tvTypes = listOf("Anime")

    requiresResources = true
    language = "fr"

    // random cc logo i found
    iconUrl = "https://media.cdnandroid.com/item_images/1415321/imagen-anime-sama-voiranime-vostfree-0ori.jpg"
}

android {
    buildFeatures {
        viewBinding = true
    }
}

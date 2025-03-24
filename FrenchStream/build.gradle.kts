// use an integer for version numbers
version = 6


cloudstream {
    // All of these properties are optional, you can safely remove them

    description = "French Stream est un site qui va récupèrer les films et séries sur des plateformes comme Disney+, Netflix, Amazon Prime Video, HBO, Apple TV , Wakanim, Viki... et vous les proposer Gratuitement!"
    language    = "fr"
    authors = listOf("ycngmn")

    /**
     * Status int as the following:
     * 0: Down
     * 1: Ok
     * 2: Slow
     * 3: Beta only
     * */
    status = 1

    tvTypes = listOf("Movie","TvSeries")


    iconUrl = "https://pbs.twimg.com/profile_images/1849214735427719168/T7sqRoBF_400x400.jpg"
}

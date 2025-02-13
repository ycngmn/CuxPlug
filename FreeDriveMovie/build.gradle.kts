// use an integer for version numbers
version = 1


cloudstream {
    // All of these properties are optional, you can safely remove them

    description = "Indian multi-lingual movies & TV shows"
    language    = "hi"
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


    iconUrl = "https://freedrivemovie.com/wp-content/uploads/2020/10/cropped-PicsArt_10-11-07.20.53.jpg"
}

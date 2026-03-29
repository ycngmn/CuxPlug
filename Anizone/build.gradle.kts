// use an integer for version numbers
version = 2

cloudstream {
    // All of these properties are optional, you can safely remove them

    description = "Anizone.to streams latest anime content in multiple language."
    language = "en"
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

    iconUrl = "https://raw.githubusercontent.com/ycngmn/CuxPlug/refs/heads/main/icons/anizone.png"
}

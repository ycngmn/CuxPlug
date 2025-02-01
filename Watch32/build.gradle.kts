dependencies {
    implementation("androidx.legacy:legacy-support-v4:1.0.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.recyclerview:recyclerview:1.4.0")
}
// use an integer for version numbers
version = 1


cloudstream {
    // All of these properties are optional, you can safely remove them

    description = "Watch32 is a Free Movies streaming site with over 10000 movies and TV-Series."
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


    // random cc logo i found
    iconUrl = "https://editorialge.com/wp-content/uploads/2023/07/Watch32..jpg"
}

android {
    buildFeatures {
        viewBinding = true
    }
}

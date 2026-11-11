package com.music.aalap.data.api

/**
 * The large version of a cover. Services hand out small list thumbnails (YouTube Music 60–120 px,
 * Spotify 64/300 px, JioSaavn 50/150 px); shown full width on the player they looked blurry.
 * Each service serves the same picture bigger from a slightly different address. Pure.
 */
object Covers {
    private val GOOGLE_SIZE = Regex("""=(w\d+-h\d+|s\d+)[^/]*$""")
    private val SPOTIFY_SMALL = Regex("""ab67616d0000(1e02|4851)""")
    private val SAAVN_SIZE = Regex("""(\d{2,3})x\1(\.(jpg|png|webp))""")

    fun hd(url: String): String = when {
        url.isBlank() -> url
        // YouTube Music / Google: "…=w120-h120-l90-rj" → 1080 px.
        "googleusercontent.com" in url || "ggpht.com" in url ->
            if (GOOGLE_SIZE.containsMatchIn(url)) url.replace(GOOGLE_SIZE, "=w1080-h1080-l90-rj") else "$url=w1080-h1080-l90-rj"
        // Spotify album art: 300 px (1e02) and 64 px (4851) → 640 px (b273).
        "i.scdn.co" in url -> url.replace(SPOTIFY_SMALL, "ab67616d0000b273")
        // JioSaavn: 50x50 / 150x150 → 500x500.
        "saavncdn.com" in url || "jiosaavn" in url -> url.replace(SAAVN_SIZE, "500x500$2")
        // A YouTube video frame: the 480 px one (always there) instead of 120/320 px.
        "i.ytimg.com" in url -> url.replace(Regex("""/(default|mqdefault)\.jpg"""), "/hqdefault.jpg")
        else -> url
    }
}

package com.music.aalap.playback

/**
 * Finding a podcast episode on YouTube, where most shows post full episodes as videos. Songs are
 * matched by strict rules (artist, length, version) that reject an episode's upload; episodes are
 * matched by their title words instead. Pure, so it is unit-tested.
 */
object PodcastMatch {
    private val filler = setOf("the", "a", "an", "and", "of", "on", "in", "ft", "feat", "with", "ep", "episode", "podcast", "show", "full", "hindi", "हिंदी")

    fun words(t: String): Set<String> =
        t.lowercase().replace(Regex("[^\\p{L}\\p{N}]+"), " ").split(' ')
            .filter { it.length > 1 && it !in filler }.toSet()

    /**
     * How well a video title fits an episode title: the share of the episode's words in the
     * video title (episode titles are often cut short or carry extra tags on YouTube), 0..1.
     */
    fun score(episodeTitle: String, videoTitle: String): Double {
        val want = words(episodeTitle).take(12).toSet()
        if (want.isEmpty()) return 0.0
        val have = words(videoTitle)
        return want.count { it in have }.toDouble() / want.size
    }

    /** The best video of [candidates] (id to title) for the episode, when it's a good enough fit. */
    fun best(episodeTitle: String, candidates: List<Pair<String, String>>, min: Double = 0.55): String? =
        candidates.map { it.first to score(episodeTitle, it.second) }
            .filter { it.second >= min }.maxByOrNull { it.second }?.first

    /** What to search YouTube for: the title's main part, then that with the show's name (no script tags). */
    fun queries(title: String, show: String): List<String> {
        val main = title.split('|').first().trim().take(90)
        val showLatin = show.replace(Regex("[^\\p{IsLatin}\\p{N}\\s]"), " ").replace(Regex("\\s+"), " ").trim()
        return listOf(main, "$main $showLatin".trim()).distinct()
    }
}

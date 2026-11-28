package com.music.aalap.data.api

import com.music.aalap.data.entity.SongsModel

/**
 * Search's songs from two places, as one list with the best match on top:
 *  - Spotify's search (titles, artists, albums), and
 *  - YouTube Music's song search, which also matches lyrics and reliably has the original.
 * The same song from both shows once. The top result is the original you meant: its title is
 * what you typed and it isn't a remix, lofi, unplugged, cover… (unless you typed that), the one
 * both places agree on first. When nothing's title matches but YouTube Music found songs, you
 * typed lyrics: its first song is the top result. Pure, unit-tested.
 */
object SearchTop {
    data class Result(val songs: List<SongsModel>, val top: SongsModel?, val byLyrics: Boolean)

    private fun norm(t: String) = t.lowercase().replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()
    // The same song from both places matches with or without "(From …)" and "feat." parts, but a
    // remix, lofi or unplugged version stays a different song (it was merged into the original).
    private fun core(t: String) = norm(
        t.replace(Regex("""[(\[]\s*(from|feat\.?|ft\.?)[^)\]]*[)\]]""", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("""\s-\s*from\s.*$""", RegexOption.IGNORE_CASE), " ")
    )
    private fun firstArtist(a: String) = norm(a.substringBefore(",").substringBefore("&"))
    fun key(s: SongsModel) = core(s.title) + "|" + firstArtist(s.singer)

    /** The language a song says it's in ("(Tamil)" in its title, Tamil letters…), "" when it doesn't. */
    fun statedLanguage(s: SongsModel): String =
        com.music.aalap.data.recommendation.engine.LanguageGuess.labelled(s.title, s.album)
            .ifBlank { com.music.aalap.data.recommendation.engine.LanguageGuess.script(s.title) }

    /** The film or album a title names ("Yeh Ek Zindagi (From \"Monica, O My Darling\")" -> "monica o my darling"). */
    private fun filmOf(t: String): String =
        Regex("""(?i)[(\[]\s*from\s+["“']?([^"”')\]]+)""").find(t)?.groupValues?.get(1)?.let(::norm).orEmpty()

    /**
     * One score for every search (lower is better), the same rule for every song:
     *  - how well the title (or its film / album) matches what you typed, a version you didn't
     *    ask for and a cover channel counting against it ([SearchRank.score]);
     *  - both places agreeing (in Spotify's and YouTube Music's top 5), and YouTube Music's first
     *    song (it matches famous lines and lyrics), counting for it;
     *  - a language you turned off (and didn't type) sending it to the bottom.
     * The best becomes the top result. Pure, unit-tested.
     */
    fun score(query: String, s: SongsModel, spotifyRank: Int, youtubeRank: Int, mix: Map<String, Double>): Double {
        val q = norm(query)
        var x = SearchRank.score(query, s).toDouble()
        val film = filmOf(s.title)
        if (x >= 2 && film.isNotBlank() && (film == q || film.startsWith(q) || q.startsWith(film))) x = 1.0
        // A version you typed ("unplugged", "remix") that the title has: that's the one.
        if (listOf("unplugged", "remix", "lofi", "acoustic", "reprise", "slowed", "female", "male").any { it in q && it in norm(s.title) }) x -= 0.5
        if (spotifyRank in 0..4 && youtubeRank in 0..4) x -= 1.0
        if (youtubeRank == 0) x -= 0.6
        if (spotifyRank == 0) x -= 0.3
        val lang = statedLanguage(s)
        if (mix.isNotEmpty() && lang.isNotBlank() && lang !in q &&
            !com.music.aalap.data.preferences.RecoPrefs.languageAllowed(mix, lang)) x += 6.0
        return x
    }

    fun merge(query: String, spotify: List<SongsModel>, youtube: List<SongsModel>, mix: Map<String, Double> = emptyMap()): Result {
        val seen = HashSet<String>()
        val all = (spotify + youtube).filter { it.title.isNotBlank() && seen.add(key(it)) }
        val spRank = spotify.mapIndexed { i, s -> key(s) to i }.toMap()
        val ytRank = youtube.mapIndexed { i, s -> key(s) to i }.toMap()
        // Originals: a song whose artists all appear in the credits of another matching song is
        // what that one remakes, remixes or samples ("Run Down The City - Monica" credits Asha
        // Bhosle and R.D. Burman: "Piya Tu Ab To Aaja" by them is the original). It goes first.
        val near = (spotify.take(8) + youtube.take(8)).filter { it.title.isNotBlank() }
        // Only songs YouTube Music itself ranks for what you typed (its top 5): an R.D. Burman song
        // that merely shares a credit with a remake isn't what you searched for.
        val originals = all.filter { a -> (ytRank[key(a)] ?: 99) <= 4 && isOriginalOf(a, near) }.mapTo(HashSet()) { key(it) }
        val scored = all.withIndex().map { (i, s) ->
            val x = score(query, s, spRank[key(s)] ?: -1, ytRank[key(s)] ?: -1, mix) - (if (key(s) in originals) 2.5 else 0.0)
            Triple(s, x, i)
        }.sortedWith(compareBy({ it.second }, { it.third }))
        val ranked = scored.map { it.first }
        val line = norm(query).split(' ').count { it.isNotBlank() } >= 3
        val best = scored.firstOrNull()
        // A clear best match: the top result.
        if (best != null && best.second <= 1.5) {
            val titleMatch = SearchRank.score(query, best.first) <= 1 || filmOf(best.first.title).let { it.isNotBlank() && norm(query).startsWith(it) }
            return Result(ranked, best.first, byLyrics = line && !titleMatch)
        }
        // Nothing's title matches, but YouTube Music found songs: what you typed is a line from one.
        val yt = youtube.firstOrNull()?.let { y -> ranked.firstOrNull { key(it) == key(y) } }
        if (yt != null && scored.first { it.first === yt }.second < 6.0) {
            return Result(listOf(yt) + ranked.filter { it !== yt }, yt, byLyrics = line)
        }
        return Result(ranked, null, false)
    }

    private fun artistSet(a: String): Set<String> =
        a.split(",", "&", " x ", " and ", " feat. ", " ft. ").map { norm(it) }.filter { it.isNotBlank() }.toSet()

    /** [song]'s artists all appear among the (more numerous) credits of another of [near]. Pure. */
    fun isOriginalOf(song: SongsModel, near: List<SongsModel>): Boolean {
        val mine = artistSet(song.singer)
        if (mine.isEmpty()) return false
        return near.any { other -> other !== song && key(other) != key(song) &&
            artistSet(other.singer).let { theirs -> theirs.size > mine.size && theirs.containsAll(mine) } }
    }
}

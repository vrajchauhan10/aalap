package com.music.aalap.data.api

import com.music.aalap.data.entity.SongsModel

/**
 * Orders song results so the song you meant comes first: the original, most played version
 * rather than a cover, remix or slowed edit. Spotify's own order already leans on popularity,
 * so it stays the tie-breaker; this only moves exact titles up and versions down (unless the
 * search asked for that version, e.g. "tum hi ho unplugged").
 */
object SearchRank {
    private val versionWords = listOf(
        "cover", "remix", "lofi", "lo-fi", "lo fi", "slowed", "reverb", "sped up", "reprise", "karaoke",
        "instrumental", "club mix", "mashup", "female version", "male version", "unplugged", "acoustic",
        "8d", "bass boosted", "nightcore", "version", "edit", "mix", "jhankar", "dj",
    )

    private val coverArtist = Regex("""(?i)\b(presents?|studios?|records|recordings|cover|covers|karaoke|tribute|official channel)\b""")

    private fun norm(t: String) = t.lowercase().replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()

    /** The title without "(From …)" / "- Remix" parts. */
    private fun core(title: String) = norm(title.replace(Regex("""\(.*?\)|\[.*?\]"""), " ").substringBefore(" - "))

    fun score(query: String, song: SongsModel): Int {
        val q = norm(query)
        val title = norm(song.title)
        val c = core(song.title)
        var s = when {
            c == q || title == q -> 0
            c.startsWith(q) -> 1
            q.startsWith(c) && c.isNotBlank() -> 1 // "tum hi ho arijit": title + artist typed
            title.contains(q) -> 2
            else -> 3
        }
        val asked = versionWords.filter { q.contains(it) }
        if (versionWords.any { w -> w !in asked && Regex("\\b${Regex.escape(w)}\\b").containsMatchIn(title) }) s += 4
        // Re-uploads and covers credited to a channel or label instead of a singer ("Akm Present",
        // "Mono Guruji Studio", "XYZ Records") go below the real recordings.
        if (coverArtist.containsMatchIn(song.singer)) s += 3
        return s
    }

    /** Stable: equal scores keep Spotify's order. */
    fun rank(query: String, songs: List<SongsModel>): List<SongsModel> =
        songs.withIndex().sortedWith(compareBy({ score(query, it.value) }, { it.index })).map { it.value }
}

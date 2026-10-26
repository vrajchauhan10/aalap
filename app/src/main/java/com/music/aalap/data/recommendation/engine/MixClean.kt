package com.music.aalap.data.recommendation.engine

import com.music.aalap.data.entity.SongsModel

/**
 * The last step of every mix: each song once (the same song listed twice, "Kesariya" and
 * "Kesariya (From "Brahmastra")", is one song), and the song itself rather than a remix,
 * lofi, slowed, sped-up, 8D, karaoke, cover or instrumental take, unless that's all there is.
 * Pure, so it's tested on its own.
 */
object MixClean {
    private val ALTERED = Regex(
        """\b(remix|rmx|lofi|lo-fi|lo fi|slowed|reverb|sped up|speed up|nightcore|8d|karaoke|instrumental|cover|mashup|bass boosted|reprise)\b""",
        RegexOption.IGNORE_CASE,
    )

    /** An altered take of a song, by its title (and album, which often carries "Lofi Flip"). */
    fun altered(title: String, album: String = ""): Boolean = ALTERED.containsMatchIn(title) || ALTERED.containsMatchIn(album)

    /** One key per song: its own name (no brackets, "From …", "feat.") and its main artist. */
    fun songKey(title: String, singer: String): String =
        SongMatcher.titleKey(title, singer) + "|" + (SongMatcher.artistKeys(singer).firstOrNull() ?: "")

    fun <T> clean(list: List<T>, title: (T) -> String, singer: (T) -> String, album: (T) -> String = { "" }): List<T> {
        val unique = list.distinctBy { songKey(title(it), singer(it)) }
        val plain = unique.filterNot { altered(title(it), album(it)) }
        // A list that is mostly lofi (say, a lofi station) stays as it is.
        return if (plain.size * 2 >= unique.size) plain else unique
    }

    fun songs(list: List<SongsModel>): List<SongsModel> = clean(list, { it.title }, { it.singer }, { it.album })
}

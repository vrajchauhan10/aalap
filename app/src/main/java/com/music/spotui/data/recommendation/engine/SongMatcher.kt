package com.music.spotui.data.recommendation.engine

import java.text.Normalizer
import kotlin.math.abs
import kotlin.math.max

/**
 * Decides when two listings are the same song, so results from different services
 * merge into one canonical id.
 *
 * ISRC wins when both sides have one. Otherwise the title is normalised: bracketed
 * parts ("(Official Video)", "(From …)", "[Remix]"), upload noise ("Lyrical", "Full
 * Song", "Official Audio") and "feat." credits are dropped, and common Hindi/Punjabi
 * transliteration differences fold together ("Badnaam" = "Badnam", "Dil" = "Dill").
 * The titles must then agree (≥ 0.85 token overlap) and share an artist.
 *
 * Pure Kotlin (no Android types), so it runs in plain unit tests.
 */
object SongMatcher {

    private val NOISE_WORDS = setOf(
        "official", "video", "audio", "lyric", "lyrics", "lyrical", "full", "song", "songs",
        "remix", "remastered", "hd", "4k", "mv", "visualizer", "visualiser", "explicit",
        "version", "new", "latest",
    )

    private val BRACKETS = Regex("""\(.*?\)|\[.*?\]|\{.*?\}""")
    private val FEAT = Regex("""\s(feat\.?|ft\.?|featuring)\s.*$""", RegexOption.IGNORE_CASE)
    private val NON_WORD = Regex("""[^\p{L}\p{N} ]""")
    private val SPACES = Regex("""\s+""")
    private val MARKS = Regex("""\p{M}+""")

    /** Keys are computed for the same titles/artists over and over; remember recent ones. */
    private val titleCache = java.util.concurrent.ConcurrentHashMap<String, String>()
    private val artistCache = java.util.concurrent.ConcurrentHashMap<String, List<String>>()
    private fun <V> MutableMap<String, V>.remember(key: String, compute: () -> V): V =
        this[key] ?: compute().also { if (size > 20_000) clear(); this[key] = it }

    private val ARTIST_SPLIT = Regex("""\s*(,|&|\bx\b|\band\b|\bfeat\.?|\bft\.?|\bwith\b|/|;)\s*""", RegexOption.IGNORE_CASE)

    /** Folds spelling variants: accents, doubled vowels, w/v. "Badnaam" → "badnam". */
    fun fold(word: String): String {
        var w = Normalizer.normalize(word, Normalizer.Form.NFD).replace(MARKS, "")
        w = w.replace("ee", "i").replace("oo", "u").replace("w", "v")
        val out = StringBuilder(w.length)
        for (ch in w) if (out.isEmpty() || out.last() != ch || !ch.isLetter()) out.append(ch)
        return out.toString()
    }

    private fun basicNorm(s: String) = s.lowercase()
        .replace(BRACKETS, " ")
        .replace(FEAT, " ")
        .replace(NON_WORD, " ")
        .replace(SPACES, " ").trim()

    /** Each credited artist, normalised. "Kamal Kahlon & Param Singh" → [kamal kahlon, param singh]. */
    fun artistKeys(artists: String): List<String> = artistCache.remember(artists) {
        artists.split(ARTIST_SPLIT)
            .map { a -> basicNorm(a).split(' ').filter { it.isNotBlank() }.joinToString(" ") { fold(it) } }
            .filter { it.isNotBlank() }
            .distinct()
    }

    /** The comparable core of a title. Uploads like "Artist - Title | Label" keep only "Title". */
    fun titleKey(title: String, artists: String = ""): String = titleCache.remember("$title\u0000$artists") { computeTitleKey(title, artists) }

    private fun computeTitleKey(title: String, artists: String): String {
        val artistSet = artistKeys(artists).toSet()
        val parts = title.split(" - ", " | ", " – ", " — ").map { it.trim() }.filter { it.isNotBlank() }
        val core = parts.firstOrNull { p ->
            val k = basicNorm(p).split(' ').filter { it.isNotBlank() }.joinToString(" ") { fold(it) }
            k.isNotBlank() && k !in artistSet
        } ?: title
        val words = basicNorm(core).split(' ').filter { it.isNotBlank() }.map { fold(it) }
        val kept = words.filter { it !in NOISE_WORDS }
        return (kept.ifEmpty { words }).joinToString(" ")
    }

    /** Key used as the canonical id when there is no ISRC. */
    fun canonicalKey(c: Candidate): String =
        "key:${titleKey(c.title, c.artists)}|${artistKeys(c.artists).firstOrNull().orEmpty()}"

    /** Token overlap of two title keys, 0..1 (shared tokens ÷ the longer title). */
    fun titleSimilarity(a: String, b: String): Double {
        val ta = a.split(' ').filter { it.isNotBlank() }.toSet()
        val tb = b.split(' ').filter { it.isNotBlank() }.toSet()
        if (ta.isEmpty() || tb.isEmpty()) return 0.0
        return ta.intersect(tb).size.toDouble() / max(ta.size, tb.size)
    }

    fun artistsOverlap(a: String, b: String): Boolean {
        val ka = artistKeys(a); val kb = artistKeys(b)
        return ka.any { x -> kb.any { y -> x == y || (x.length > 3 && y.contains(x)) || (y.length > 3 && x.contains(y)) } }
    }

    /** True when [a] and [b] are the same recording (or a re-upload of it). */
    fun sameSong(a: Candidate, b: Candidate): Boolean {
        if (a.isrc.isNotBlank() && b.isrc.isNotBlank()) return a.isrc.equals(b.isrc, ignoreCase = true)
        if (a.spotifyId.isNotBlank() && a.spotifyId == b.spotifyId) return true
        if (a.ytId.isNotBlank() && a.ytId == b.ytId) return true
        if (a.saavnId.isNotBlank() && a.saavnId == b.saavnId) return true
        val ta = titleKey(a.title, a.artists)
        val tb = titleKey(b.title, b.artists)
        if (ta.isBlank() || tb.isBlank()) return false
        if (ta != tb && titleSimilarity(ta, tb) < 0.85) return false
        if (a.artists.isBlank() || b.artists.isBlank()) return ta == tb
        if (artistsOverlap(a.artists, b.artists)) return true
        // Same title and the same length: one listing credits a label or channel instead.
        return ta == tb && a.durationSec > 0 && b.durationSec > 0 && abs(a.durationSec - b.durationSec) <= 3
    }
}

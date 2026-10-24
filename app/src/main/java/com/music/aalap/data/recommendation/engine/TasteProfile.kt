package com.music.aalap.data.recommendation.engine

import kotlin.math.max

/**
 * What the app has learned about the listener, rebuilt automatically from how the app
 * is used. Likes are only one signal:
 *
 *  - plays, weighted by recency (60-day half-life) and whether the song was finished
 *    or skipped (a skip counts against the artist),
 *  - replays of the same song,
 *  - liked songs, followed artists and imported favourites,
 *  - the languages you finish songs in (Hindi, Punjabi, Gujarati, English…),
 *  - what you play at this time of day (morning vs late night),
 *  - how often you skip an artist.
 *
 * Pure scoring (no Android types); [Personalizer] builds it from the database.
 */
class TasteProfile(
    val artistWeight: Map<String, Double>,
    val skipRate: Map<String, Double>,
    val songAffinity: Map<String, Double>,
    val liked: Set<String>,
    val followed: Set<String>,
    val language: Map<String, Double>,
    val dayPart: Map<String, Double>,
    val playCount: Int,
    val recentIds: Set<String>,
    val recentSkips: Set<String>,
    /** 0..1 per feel: the feels you finish (vs skip). Empty until there's enough history. */
    val feelPref: Map<Feel, Double> = emptyMap(),
    /** The feels you play at this time of day, as raw shares (a gentle start for a new session). */
    val dayPartFeel: Map<Feel, Double> = emptyMap(),
    /** Share of known songs to aim for in a queue, from how you treat new vs known songs. */
    val familiarTarget: Double = 0.35,
    /** Songs you've finished at least once. */
    val heard: Set<String> = emptySet(),
    /** Old favourites: finished 3+ times, not played in 45+ days. */
    val rediscover: Set<String> = emptySet(),
) {
    /** Artists you play a fair bit (a song by anyone else can be a surprise). */
    val usualArtists: Set<String> by lazy { artistWeight.filterValues { it >= 0.15 }.keys }

    /** "For new users with no history, use globalScore only." */
    val isNew: Boolean get() = playCount < 3

    /**
     * 0..1: how well [c] (canonical id [id]) fits this listener. Mostly what you
     * actually finish (artists, feels, languages, time of day); a like is one signal
     * among many, so likes alone can't decide what plays.
     */
    fun taste(c: Candidate, id: String?, vibe: Vibe? = null): Double {
        val keys = SongMatcher.artistKeys(c.artists)
        if (keys.isEmpty()) return 0.0
        val artist = keys.mapIndexed { i, k -> (artistWeight[k] ?: 0.0) * (if (i == 0) 1.0 else 0.7) }.max()
        val parts = mutableListOf(
            0.32 to artist,
            0.06 to (if (id != null && id in liked) 1.0 else 0.0),
            0.08 to (if (keys.any { it in followed }) 1.0 else 0.0),
            0.12 to (id?.let { songAffinity[it] } ?: 0.0),
        )
        if (vibe != null && vibe.known && feelPref.isNotEmpty()) {
            parts += 0.16 to vibe.weights.entries.sumOf { (f, w) -> w * (feelPref[f] ?: 0.5) }
        }
        val lang = c.language.lowercase()
        if (language.isNotEmpty() && lang.isNotBlank()) parts += 0.14 to (language[lang] ?: 0.0)
        keys.mapNotNull { dayPart[it] }.maxOrNull()?.let { parts += 0.12 to it }
        val base = parts.sumOf { it.first * it.second } / parts.sumOf { it.first }
        val penalty = 1.0 - 0.6 * (skipRate[keys.first()] ?: 0.0)
        return (base * penalty).coerceIn(0.0, 1.0)
    }

    companion object {
        val EMPTY = TasteProfile(emptyMap(), emptyMap(), emptyMap(), emptySet(), emptySet(), emptyMap(), emptyMap(), 0, emptySet(), emptySet())

        /** 0..1 from how often B followed A for this user; recent habits count fully. */
        fun habit(count: Int, lastAt: Long, now: Long): Double {
            if (count <= 0) return 0.0
            val strength = 1.0 - 1.0 / (1.0 + count)            // 1 → 0.5, 2 → 0.67, 4 → 0.8
            val ageDays = (now - lastAt).coerceAtLeast(0) / 86_400_000.0
            return strength * (if (ageDays <= 90) 1.0 else 0.6)
        }

        /**
         * What one play says about liking a song (before fading with age): a full listen +1,
         * half a song about +0.67, a skip -0.6, a skip in the first 10 seconds -0.9.
         * [heard] is the share of the song heard (0..1).
         */
        fun playWeight(skipped: Boolean, listenedMs: Long, heard: Double): Double =
            if (skipped) (if (listenedMs in 1 until 10_000) -0.9 else -0.6) else 0.35 + 0.65 * heard.coerceIn(0.0, 1.0)

        /** finalScore = 0.6·global + 0.2·taste + 0.2·habit (global only for new listeners). */
        fun final(global: Double, taste: Double, habit: Double, isNew: Boolean): Double =
            if (isNew) global else 0.6 * global + 0.2 * taste + 0.2 * habit

        /** Normalises values to 0..1 by the largest, with a square root so one favourite doesn't flatten the rest. */
        fun <K> normalise(raw: Map<K, Double>): Map<K, Double> {
            val top = raw.values.maxOrNull()?.takeIf { it > 0 } ?: return emptyMap()
            return raw.filterValues { it > 0 }.mapValues { kotlin.math.sqrt(it.value / max(top, 1e-9)) }
        }
    }
}

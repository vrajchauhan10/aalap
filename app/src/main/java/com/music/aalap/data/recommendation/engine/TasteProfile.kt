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
) {
    /** "For new users with no history, use globalScore only." */
    val isNew: Boolean get() = playCount < 3

    /** 0..1: how well [c] (canonical id [id]) fits this listener. */
    fun taste(c: Candidate, id: String?): Double {
        val keys = SongMatcher.artistKeys(c.artists)
        if (keys.isEmpty()) return 0.0
        val artist = keys.mapIndexed { i, k -> (artistWeight[k] ?: 0.0) * (if (i == 0) 1.0 else 0.7) }.max()
        val parts = mutableListOf(
            0.35 to artist,
            0.15 to (if (id != null && id in liked) 1.0 else 0.0),
            0.10 to (if (keys.any { it in followed }) 1.0 else 0.0),
            0.10 to (id?.let { songAffinity[it] } ?: 0.0),
        )
        val lang = c.language.lowercase()
        if (language.isNotEmpty() && lang.isNotBlank()) parts += 0.15 to (language[lang] ?: 0.0)
        keys.mapNotNull { dayPart[it] }.maxOrNull()?.let { parts += 0.15 to it }
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

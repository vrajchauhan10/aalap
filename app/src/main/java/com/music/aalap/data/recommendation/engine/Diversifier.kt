package com.music.aalap.data.recommendation.engine

import java.util.Random

/**
 * Keeps the queue varied:
 *  - never the same artist twice in a row,
 *  - nothing played or skipped recently (the caller passes those ids),
 *  - not always the #1: one of the top [TOP_N] is sampled, weighted by score, so the
 *    queue is different every time while staying strong.
 */
object Diversifier {
    const val TOP_N = 5

    fun pick(
        scored: List<Scored>,
        previousArtists: String,
        exclude: Set<String>,
        rnd: Random,
        topN: Int = TOP_N,
    ): Scored? {
        val allowed = scored.filter { it.song.id !in exclude }
        if (allowed.isEmpty()) return null
        val fresh = allowed.filter { previousArtists.isBlank() || !SongMatcher.artistsOverlap(it.song.artists, previousArtists) }
        // Only when every candidate shares the artist is a repeat allowed.
        val pool = fresh.ifEmpty { allowed }.sortedByDescending { it.final }.take(topN)
        return sampleWeighted(pool, rnd) { it.final }
    }

    fun <T> sampleWeighted(items: List<T>, rnd: Random, weight: (T) -> Double): T? {
        if (items.isEmpty()) return null
        val weights = items.map { weight(it).coerceAtLeast(0.0) + 1e-6 }
        var r = rnd.nextDouble() * weights.sum()
        for (i in items.indices) {
            r -= weights[i]
            if (r <= 0) return items[i]
        }
        return items.last()
    }

    /** Re-orders [list] so no artist plays twice in a row, otherwise keeping the order. */
    fun <T> spreadArtists(list: List<T>, artists: (T) -> String): List<T> {
        val pending = list.toMutableList()
        val out = ArrayList<T>(list.size)
        while (pending.isNotEmpty()) {
            val last = out.lastOrNull()?.let(artists).orEmpty()
            val idx = pending.indexOfFirst { last.isBlank() || !SongMatcher.artistsOverlap(artists(it), last) }
                .takeIf { it >= 0 } ?: 0
            out += pending.removeAt(idx)
        }
        return out
    }
}

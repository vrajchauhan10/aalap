package com.music.spotui.data.recommendation.engine

/**
 * Reciprocal Rank Fusion of the sources' "what plays next" lists:
 *
 *     globalScore = Σ weight(source) / (60 + rank in source) + crossSourceBonus
 *
 * Ranks start at 1. The bonus is +0.3 when two sources picked the song and +0.6 when
 * three or more did. Songs only one source found are kept: regional music is often
 * only known to one service.
 *
 * Pure Kotlin, so the unit tests run it directly.
 */
object Fusion {
    const val K = 60.0
    const val BONUS_TWO = 0.3
    const val BONUS_THREE = 0.6

    data class Fused(
        val id: String,
        val score: Double,
        /** 1-based rank of the song in each source that returned it. */
        val ranks: Map<Source, Int>,
    ) {
        val sources: Set<Source> get() = ranks.keys
    }

    /**
     * [ranked]: for each source, canonical ids best-first (already matched/deduped
     * to canonical ids; a repeated id keeps its best rank). Returns best-first.
     */
    fun fuse(ranked: Map<Source, List<String>>): List<Fused> {
        val ranks = LinkedHashMap<String, MutableMap<Source, Int>>()
        for ((source, ids) in ranked) {
            var rank = 0
            val seen = HashSet<String>()
            for (id in ids) {
                if (!seen.add(id)) continue
                rank++
                ranks.getOrPut(id) { LinkedHashMap() }[source] = rank
            }
        }
        return ranks.map { (id, r) ->
            val rrf = r.entries.sumOf { (source, rank) -> source.weight / (K + rank) }
            val bonus = when {
                r.size >= 3 -> BONUS_THREE
                r.size == 2 -> BONUS_TWO
                else -> 0.0
            }
            Fused(id, rrf + bonus, r)
        }.sortedWith(
            compareByDescending<Fused> { it.score }
                .thenBy { it.ranks.values.min() }
                .thenBy { it.id },
        )
    }
}

package com.music.aalap.data.recommendation.engine

/**
 * Which songs you're tired of, from how you skipped them. Pure (no Android), so it's tested.
 *
 *  - Skipped at all in the last 14 days: left out for now (as before).
 *  - Skipped in the first 10 seconds twice in the last 90 days: left out for 90 days, not
 *    just two weeks. Before this, a song you kept cutting off came straight back after 14 days.
 *  - Finishing a song later forgives the skips before it, so a song you skipped once on a
 *    bad day but then played through isn't held against you.
 */
object SkipMemory {
    const val EARLY_MS = 10_000L
    private const val DAY = 86_400_000L
    private const val RECENT_DAYS = 14
    private const val EARLY_DAYS = 90
    private const val EARLY_SKIPS = 2

    class Heard(val id: String, val at: Long, val listenedMs: Long, val skipped: Boolean) {
        val early: Boolean get() = skipped && listenedMs in 1 until EARLY_MS
    }

    fun tiredOf(plays: List<Heard>, now: Long): Set<String> =
        plays.groupBy { it.id }.filterValues { ps ->
            val finishedAt = ps.filter { !it.skipped }.maxOfOrNull { it.at } ?: Long.MIN_VALUE
            val held = ps.filter { it.skipped && it.at > finishedAt }
            held.any { now - it.at < RECENT_DAYS * DAY } ||
                held.count { it.early && now - it.at < EARLY_DAYS * DAY } >= EARLY_SKIPS
        }.keys
}

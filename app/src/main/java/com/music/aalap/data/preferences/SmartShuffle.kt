package com.music.aalap.data.preferences

import kotlin.random.Random

/**
 * Shuffle that doesn't keep landing on what you just heard. Pure, so it's tested.
 * Songs you haven't heard in the last two days come first, in random order; songs heard
 * since go to the end, the longest-ago first; songs you're tired of (see
 * [com.music.aalap.data.recommendation.engine.SkipMemory]) go last. Nothing is removed:
 * a long shuffle still reaches every song.
 */
object SmartShuffle {
    const val RECENT_MS = 48 * 3_600_000L

    fun <T> order(
        songs: List<T>,
        lastHeardAt: (T) -> Long?,
        tired: (T) -> Boolean,
        now: Long,
        random: Random = Random.Default,
    ): List<T> {
        val (worn, rest) = songs.partition(tired)
        val (recent, fresh) = rest.partition { s -> lastHeardAt(s)?.let { now - it < RECENT_MS } == true }
        return fresh.shuffled(random) + recent.sortedBy { lastHeardAt(it) } + worn.shuffled(random)
    }
}

/**
 * [songs] in [SmartShuffle] order, from this phone's listening history (the last 500 plays,
 * already in memory-sized JSON, so this is quick enough for a tap).
 */
fun smartShuffled(context: android.content.Context, songs: List<com.music.aalap.data.entity.SongsModel>): List<com.music.aalap.data.entity.SongsModel> {
    val plays = ListeningStats.plays(getListeningHistory(context))
    val last = HashMap<Int, Long>()
    plays.forEach { p -> if (p.entry.ts > (last[p.entry.songId] ?: 0L)) last[p.entry.songId] = p.entry.ts }
    val now = System.currentTimeMillis()
    val tired = com.music.aalap.data.recommendation.engine.SkipMemory.tiredOf(
        plays.map { com.music.aalap.data.recommendation.engine.SkipMemory.Heard(it.entry.songId.toString(), it.entry.ts, it.listenedMs, it.skipped) },
        now,
    )
    return SmartShuffle.order(songs, { last[it.id] }, { it.id.toString() in tired }, now)
}

package com.music.spotui.data.recommendation

import android.content.Context
import com.music.spotui.data.api.Api
import com.music.spotui.data.entity.SongsModel
import com.music.spotui.data.preferences.HistoryEntry
import com.music.spotui.data.preferences.getLikedSongIds
import com.music.spotui.data.preferences.getListeningHistory

/**
 * The one recommendation engine every surface uses (autoplay, queue top-ups, search
 * radio, Fresh picks, mood mixes). Spotify supplies the candidates — its own song
 * radio, the same thing it queues after a track — and your listening decides the order:
 *
 *  - habits: songs you usually play right after the current one come first,
 *  - affinity: artists you play most and songs you've liked rank higher,
 *  - variety: never the same artist twice in a row.
 */
object Recommender {

    /** What your history says about your taste. Rebuilt at most once a minute. */
    class Taste(
        val artistScore: Map<String, Float>,   // main artist (lowercase) → 0..1
        val playedTrackIds: Set<String>,       // Spotify ids you've played
        val playedSongIds: Set<Int>,
        val likedSongIds: Set<Int>,
        /** previous Spotify id → (next Spotify id → times you played it next) */
        val nextAfter: Map<String, Map<String, Int>>,
        val entryById: Map<String, HistoryEntry>,
    )

    @Volatile private var cached: Pair<Long, Taste>? = null

    fun taste(context: Context): Taste {
        cached?.let { (at, t) -> if (System.currentTimeMillis() - at < 60_000) return t }
        val history = getListeningHistory(context) // newest first
        val counts = history.map { mainArtist(it.singer) }.filter { it.isNotBlank() }.groupingBy { it }.eachCount()
        val top = counts.values.maxOrNull()?.toFloat() ?: 1f
        // Consecutive plays within 30 minutes count as "played B after A".
        val next = HashMap<String, HashMap<String, Int>>()
        for (i in history.size - 1 downTo 1) {
            val a = history[i]; val b = history[i - 1]
            if (a.spotifyTrackId.isBlank() || b.spotifyTrackId.isBlank() || a.spotifyTrackId == b.spotifyTrackId) continue
            if (b.ts - a.ts > 30 * 60_000L) continue
            val m = next.getOrPut(a.spotifyTrackId) { HashMap() }
            m[b.spotifyTrackId] = (m[b.spotifyTrackId] ?: 0) + 1
        }
        val t = Taste(
            artistScore = counts.mapValues { it.value / top },
            playedTrackIds = history.mapNotNull { it.spotifyTrackId.ifBlank { null } }.toSet(),
            playedSongIds = history.map { it.songId }.toSet(),
            likedSongIds = runCatching { getLikedSongIds(context) }.getOrDefault(emptySet()),
            nextAfter = next,
            entryById = history.filter { it.spotifyTrackId.isNotBlank() && it.url.isNotBlank() }
                .associateBy { it.spotifyTrackId },
        )
        cached = System.currentTimeMillis() to t
        return t
    }

    fun mainArtist(singer: String) = singer.substringBefore(",").trim().lowercase()

    private fun artistAffinity(t: Taste, s: SongsModel): Float =
        s.singer.split(",").maxOfOrNull { t.artistScore[it.trim().lowercase()] ?: 0f } ?: 0f

    /**
     * Re-orders Spotify's [candidates] for you. Spotify's order still counts most;
     * [familiar] (0..1) is how much songs you already know should be pulled up
     * (autoplay likes some; Fresh picks wants none).
     */
    fun rerank(t: Taste, candidates: List<SongsModel>, familiar: Float): List<SongsModel> {
        val n = candidates.size.coerceAtLeast(1)
        val scored = candidates.mapIndexed { i, s ->
            val spotify = 1f - i.toFloat() / n
            val liked = if (s.id in t.likedSongIds) 1f else 0f
            val known = if (s.spotifyTrackId in t.playedTrackIds || s.id in t.playedSongIds) 1f else 0f
            s to (spotify * 1.0f + artistAffinity(t, s) * 0.55f + liked * 0.35f + known * familiar * 0.4f)
        }.sortedByDescending { it.second }.map { it.first }
        return spreadArtists(scored)
    }

    /** Never the same main artist twice in a row, keeping the order otherwise. */
    fun spreadArtists(list: List<SongsModel>): List<SongsModel> {
        val pending = list.toMutableList()
        val out = ArrayList<SongsModel>(list.size)
        while (pending.isNotEmpty()) {
            val last = out.lastOrNull()?.let { mainArtist(it.singer) }
            val idx = pending.indexOfFirst { mainArtist(it.singer) != last }.takeIf { it >= 0 } ?: 0
            out += pending.removeAt(idx)
        }
        return out
    }

    private fun HistoryEntry.toSong() = SongsModel(
        songId, title, album, singer, image, url, spotifyTrackId, durationMs = durationMs,
    )

    /**
     * What to play next after [seeds] (oldest → newest): your usual follow-ups for the
     * latest seed, then Spotify's radio ranked for you. Used by autoplay and queue top-ups.
     */
    suspend fun next(context: Context, seeds: List<String>): List<SongsModel> {
        val t = taste(context)
        val radio = runCatching { Api(context).getRecommendations(seeds) }.getOrDefault(emptyList())
        val last = seeds.lastOrNull().orEmpty()
        val habits = t.nextAfter[last].orEmpty().entries
            .filter { it.value >= 1 }
            .sortedByDescending { it.value }
            .take(3)
            .mapNotNull { t.entryById[it.key]?.toSong() }
        val ranked = rerank(t, radio, familiar = 0.5f)
        return (habits + ranked).distinctBy { it.spotifyTrackId.ifBlank { it.id.toString() } }
            .filter { it.spotifyTrackId !in seeds }
    }

    /**
     * Fresh picks: new-to-you songs from the radios of several favourites, mixed so no
     * single seed dominates, at most two per artist. [salt] changes the selection on
     * every refresh.
     */
    suspend fun freshPicks(context: Context, salt: Long, limit: Int = 25): List<SongsModel> {
        val t = taste(context)
        val favourites = getListeningHistory(context).filter { it.spotifyTrackId.isNotBlank() }
            .groupBy { it.spotifyTrackId }.entries.sortedByDescending { it.value.size }
            .take(15).map { it.key }
        if (favourites.isEmpty()) return emptyList()
        val rnd = java.util.Random(salt)
        val seeds = favourites.shuffled(rnd).take(3)
        val api = Api(context)
        val radios = seeds.map { seed ->
            runCatching { api.getRecommendations(listOf(seed)) }.getOrDefault(emptyList())
                .filter { it.spotifyTrackId !in t.playedTrackIds && it.id !in t.playedSongIds && it.id !in t.likedSongIds }
        }
        // Round-robin across the seeds' radios, so the row isn't one song's radio in order.
        val mixed = ArrayList<SongsModel>()
        var i = 0
        while (radios.any { i < it.size }) {
            radios.forEach { r -> r.getOrNull(i)?.let { mixed += it } }
            i++
        }
        val perArtist = HashMap<String, Int>()
        val unique = mixed.distinctBy { it.spotifyTrackId.ifBlank { it.id.toString() } }.filter {
            val a = mainArtist(it.singer)
            (perArtist[a] ?: 0) < 2 && run { perArtist[a] = (perArtist[a] ?: 0) + 1; true }
        }
        return rerank(t, unique, familiar = 0f).take(limit * 2).shuffled(rnd).take(limit).let(::spreadArtists)
    }
}

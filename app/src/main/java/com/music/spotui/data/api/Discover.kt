package com.music.spotui.data.api

import android.content.Context
import android.util.Log
import com.music.spotui.data.entity.SongsModel
import com.music.spotui.data.preferences.getListeningHistory
import kotlinx.coroutines.flow.toList
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/**
 * Moods shown at the top of Home. Each is backed by Spotify's own mood playlists
 * (found by [query]), so a song lands in a mood the way listeners categorise it.
 */
enum class Mood(val label: String, val query: String, val blurb: String, val colors: Pair<Long, Long>) {
    ENERGETIC("Energetic", "energetic hindi workout", "High-tempo tracks to get you moving", 0xFFFF8A3D to 0xFFE0461B),
    HAPPY("Happy", "happy hindi songs", "Feel-good songs that lift the mood", 0xFFFFC56B to 0xFFE08A00),
    SAD("Sad", "sad hindi songs", "Slow, emotional songs for quiet moments", 0xFF5B7CFA to 0xFF2A3A8C),
    HEARTBROKEN("Heartbroken", "heartbreak hindi songs", "For when it hurts a little", 0xFF9B5DE5 to 0xFF4B2A8C),
    ROMANTIC("Romantic", "romantic hindi songs", "Love songs, old and new", 0xFFE5397A to 0xFF8C1F4A),
    CHILL("Chill", "chill hindi lofi", "Lo-fi and easy listening", 0xFF2BB3C0 to 0xFF15606B),
    PARTY("Party", "bollywood party hits", "Dance-floor fillers", 0xFFFF5E7E to 0xFFB8235A),
    DEVOTIONAL("Devotional", "bhakti devotional songs", "Bhajans, aartis and spiritual songs", 0xFFFFB347 to 0xFFB86E00),
    FOCUS("Focus", "focus instrumental indian", "Instrumentals to help you concentrate", 0xFF6E8B74 to 0xFF2F4A36),
    ROAD_TRIP("Road trip", "road trip hindi songs", "Windows down, volume up", 0xFF3BA55C to 0xFF1E5E33),
}

/** Spotify's daily-updated chart playlists. */
data class Chart(val id: String, val title: String, val subtitle: String, val colors: Pair<Long, Long>)

val CHARTS = listOf(
    Chart("37i9dQZEVXbLZ52XmnySJg", "Top 50 India", "Most played in India today", 0xFFFF8A3D to 0xFF7A2E0E),
    Chart("37i9dQZEVXbMDoHDwVN2tF", "Top 50 Global", "Most played worldwide today", 0xFF3D6BE0 to 0xFF1A2A6C),
    Chart("37i9dQZEVXbMWDif5SCBJq", "Viral 50 India", "Blowing up in India right now", 0xFFE5397A to 0xFF6C1238),
    Chart("37i9dQZEVXbLiRSasKsNU9", "Viral 50 Global", "Trending around the world", 0xFF2BB3C0 to 0xFF0F4C55),
)

/**
 * Daily discovery for Home: mood mixes, fresh picks and all-time favourites.
 * Results are cached per calendar day, so the page changes every day and stays
 * instant within a day (pull-to-refresh forces a rebuild).
 */
object Discover {
    private const val TAG = "Discover"
    private const val PREF = "discover_cache"

    private fun today() = LocalDate.now().toString()
    private fun prefs(c: Context) = c.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    /** Forget today's results so the next load rebuilds them. */
    fun clearToday(context: Context) {
        val p = prefs(context)
        p.edit().apply { p.all.keys.filter { it.startsWith(today()) }.forEach { remove(it) } }.apply()
    }

    /** Artists you play most (main credit), most-played first. */
    fun topArtists(context: Context, limit: Int = 15): List<String> =
        getListeningHistory(context).map { it.singer.substringBefore(",").trim() }
            .filter { it.isNotBlank() }
            .groupingBy { it }.eachCount().entries
            .sortedByDescending { it.value }.take(limit).map { it.key }

    /** Your most-played songs of all time that can be replayed. */
    fun allTimeFavourites(context: Context, limit: Int = 12): List<SongsModel> =
        getListeningHistory(context).filter { it.url.isNotBlank() }
            .groupBy { it.songId }.values
            .sortedByDescending { it.size }.take(limit)
            .map { plays -> plays.first().let {
                SongsModel(it.songId, it.title, it.album, it.singer, it.image, it.url, it.spotifyTrackId, durationMs = it.durationMs)
            } }

    /**
     * A mood mix: tracks from the top Spotify playlists for the mood, with songs by
     * your most-played artists first, the rest in a different order each day.
     */
    suspend fun moodMix(context: Context, mood: Mood): List<SongsModel> {
        val key = "${today()}:mood:${mood.name}"
        read(context, key)?.let { return it }
        val api = Api(context)
        val playlists = runCatching {
            api.getCategoryPlaylists(mood.query).toList()
                .filterIsInstance<Response.Success<List<com.music.spotui.data.entity.LibraryEntry>>>()
                .lastOrNull()?.data
        }.getOrNull().orEmpty().take(3)
        val tracks = playlists.flatMap { pl ->
            runCatching {
                api.getPlaylistSongs(pl.spotifyId).toList()
                    .filterIsInstance<Response.Success<List<SongsModel>>>()
                    .firstOrNull()?.data
            }.getOrNull().orEmpty().take(40)
        }.distinctBy { it.spotifyTrackId.ifBlank { it.title + it.singer } }
        val mine = topArtists(context).map { it.lowercase() }.toSet()
        val (yours, rest) = tracks.partition { t -> t.singer.lowercase().split(",").any { it.trim() in mine } }
        val dayShuffle = java.util.Random(LocalDate.now().toEpochDay() + mood.ordinal)
        val result = (yours.shuffled(dayShuffle) + rest.shuffled(dayShuffle)).take(60)
        Log.d(TAG, "mood ${mood.name}: ${playlists.size} playlists, ${tracks.size} tracks, ${yours.size} by your artists")
        if (result.isNotEmpty()) write(context, key, result)
        return result
    }

    /** Recommendations seeded by one of your favourite songs, a different one each day. */
    suspend fun dailyPicks(context: Context): List<SongsModel> {
        val key = "${today()}:picks"
        read(context, key)?.let { return it }
        val seeds = getListeningHistory(context).filter { it.spotifyTrackId.isNotBlank() }
            .groupBy { it.spotifyTrackId }.entries.sortedByDescending { it.value.size }
            .take(10).map { it.key }
        if (seeds.isEmpty()) return emptyList()
        val seed = seeds[(LocalDate.now().toEpochDay() % seeds.size).toInt()]
        val picks = runCatching { Api(context).getRecommendations(listOf(seed)) }.getOrDefault(emptyList()).take(30)
        if (picks.isNotEmpty()) write(context, key, picks)
        return picks
    }

    // ── tiny per-day cache (only today's keys are kept) ──

    private fun read(context: Context, key: String): List<SongsModel>? {
        val raw = prefs(context).getString(key, null) ?: return null
        return runCatching {
            val a = JSONArray(raw)
            (0 until a.length()).map { i ->
                val o = a.getJSONObject(i)
                SongsModel(
                    o.getInt("id"), o.optString("title"), o.optString("album"), o.optString("singer"),
                    o.optString("cover"), o.optString("url"), o.optString("sid"), o.optBoolean("explicit"), o.optInt("dur"),
                )
            }
        }.getOrNull()?.takeIf { it.isNotEmpty() }
    }

    private fun write(context: Context, key: String, songs: List<SongsModel>) {
        val a = JSONArray()
        songs.forEach { s ->
            a.put(JSONObject().apply {
                put("id", s.id); put("title", s.title); put("album", s.album); put("singer", s.singer)
                put("cover", s.coverUri); put("url", s.url); put("sid", s.spotifyTrackId)
                put("explicit", s.explicit); put("dur", s.durationMs)
            })
        }
        val p = prefs(context)
        p.edit().apply {
            p.all.keys.filter { !it.startsWith(today()) }.forEach { remove(it) } // drop older days
            putString(key, a.toString())
        }.apply()
    }
}

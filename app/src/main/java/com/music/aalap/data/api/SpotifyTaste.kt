package com.music.aalap.data.api

import android.content.Context
import com.metrolist.spotify.Spotify
import org.json.JSONArray

/**
 * What Spotify knows about your listening (your top artists over the last month and
 * the last six months), refreshed once a day. One of the taste signals, next to your
 * plays in Aalap, YouTube Music history, likes and follows from every service.
 */
object SpotifyTaste {
    private const val PREF = "spotify_taste"
    private const val EVERY_MS = 24 * 3_600_000L

    private fun prefs(c: Context) = c.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    /** Top artists, best first. */
    fun topArtists(c: Context): List<String> = runCatching {
        val a = JSONArray(prefs(c).getString("artists", "[]"))
        (0 until a.length()).map { a.getString(it) }
    }.getOrDefault(emptyList())

    suspend fun refreshIfDue(c: Context, force: Boolean = false) {
        if (!force && System.currentTimeMillis() - prefs(c).getLong("at", 0) < EVERY_MS) return
        val fromSpotify = if (SpotifyTokenProvider.ensureToken(c)) {
            val short = Spotify.topArtists("short_term", 30).getOrNull()?.items.orEmpty().map { it.name }
            val medium = Spotify.topArtists("medium_term", 50).getOrNull()?.items.orEmpty().map { it.name }
            // Interleave so recent favourites and long-time ones both rank high.
            val merged = LinkedHashSet<String>()
            for (i in 0 until maxOf(short.size, medium.size)) { short.getOrNull(i)?.let(merged::add); medium.getOrNull(i)?.let(merged::add) }
            merged.toList()
        } else emptyList()
        // Spotify often refuses this request (429, too many requests). Then the same idea
        // comes from what Aalap already knows: the artists across your liked songs on every
        // service, most-liked first, then the artists you follow.
        val artists = fromSpotify.ifEmpty { fromLibrary(c) }
        if (artists.isEmpty()) return
        prefs(c).edit()
            .putString("artists", JSONArray(artists.take(60)).toString())
            .putString("from", if (fromSpotify.isNotEmpty()) "spotify" else "library")
            .putLong("at", System.currentTimeMillis())
            .apply()
        com.music.aalap.data.recommendation.engine.Personalizer.invalidate()
    }

    /** Your top artists from your liked songs (Spotify and YouTube Music) and follows. */
    private fun fromLibrary(c: Context): List<String> {
        val counts = HashMap<String, Int>()
        (YouTubeLibrary.spotifyLiked(c) + YouTubeLibrary.likedSongs(c)).forEach { s ->
            s.singer.split(",").map { it.trim() }.filter { it.isNotBlank() }.take(2).forEach { counts[it] = (counts[it] ?: 0) + 1 }
        }
        val liked = counts.entries.sortedByDescending { it.value }.map { it.key }
        val followed = com.music.aalap.data.preferences.getCachedFollowedArtists(c).map { it.name }
        return (liked + followed).distinctBy { it.lowercase() }
    }
}

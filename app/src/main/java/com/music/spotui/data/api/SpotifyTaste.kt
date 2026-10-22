package com.music.spotui.data.api

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
        if (!SpotifyTokenProvider.ensureToken(c)) return
        val short = Spotify.topArtists("short_term", 30).getOrNull()?.items.orEmpty().map { it.name }
        val medium = Spotify.topArtists("medium_term", 50).getOrNull()?.items.orEmpty().map { it.name }
        // Interleave so recent favourites and long-time ones both rank high.
        val merged = LinkedHashSet<String>()
        for (i in 0 until maxOf(short.size, medium.size)) { short.getOrNull(i)?.let(merged::add); medium.getOrNull(i)?.let(merged::add) }
        if (merged.isEmpty()) return
        prefs(c).edit().putString("artists", JSONArray(merged.toList()).toString()).putLong("at", System.currentTimeMillis()).apply()
        com.music.spotui.data.recommendation.engine.Personalizer.invalidate()
    }
}

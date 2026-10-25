package com.music.aalap.data.api

import android.content.Context
import com.music.aalap.data.entity.LibraryEntry
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.toList
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/**
 * Trending playlists for Library → Playlists: the editorial charts and hits lists everyone
 * is playing now (Top 50 India, Trending Now India, Hot Hits Hindi…), each found by name,
 * the official one preferred. Cached for the day.
 */
object TrendingPlaylists {
    private const val PREF = "trending_playlists_v4"

    /** What to look for, most relevant to India first; then the global charts. */
    private val QUERIES = listOf(
        "Trending Now India", "Top 50 India", "Hot Hits Hindi", "Bollywood Butter", "Hot Hits Punjabi",
        "This Is Arijit Singh", "Romantic Bollywood", "Top 50 Global", "Today's Top Hits", "Viral Hits",
        "Bollywood Central", "Desi Hits", "I-Pop Hits",
    )

    fun cached(c: Context): List<LibraryEntry> = runCatching {
        val p = c.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        if (p.getString("day", null) != LocalDate.now().toString()) return emptyList()
        val a = JSONArray(p.getString("list", "[]"))
        List(a.length()) { i -> a.getJSONObject(i).let { o -> LibraryEntry(o.getString("id"), o.getString("name"), o.getString("sub"), o.getString("cover"), true) } }
    }.getOrDefault(emptyList())

    suspend fun load(c: Context): List<LibraryEntry> {
        cached(c).takeIf { it.isNotEmpty() }?.let { return it }
        val api = Api(c)
        val found = coroutineScope {
            QUERIES.map { q ->
                async {
                    runCatching {
                        val list = api.getCategoryPlaylists(q).toList()
                            .filterIsInstance<Response.Success<List<LibraryEntry>>>().lastOrNull()?.data.orEmpty()
                        // The official list (Spotify's own shows as "Aalap").
                        // Every word of the name we looked for (so "Top 50 Global" isn't "Your Top Songs 2022").
                        val words = q.lowercase().split(' ')
                        fun matches(e: LibraryEntry) = e.name.lowercase().let { n -> words.all { it in n } }
                        // Only the official one: a fan-made copy of a chart isn't the chart.
                        list.firstOrNull { it.subtitle.endsWith("Aalap") && matches(it) }
                    }.getOrNull()
                }
            }.awaitAll()
        }.filterNotNull().distinctBy { it.spotifyId }.map { it.copy(subtitle = "Trending today") }
        if (found.isNotEmpty()) {
            val a = JSONArray()
            found.forEach { e -> a.put(JSONObject().put("id", e.spotifyId).put("name", e.name).put("sub", e.subtitle).put("cover", e.coverUri)) }
            c.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putString("day", LocalDate.now().toString()).putString("list", a.toString()).apply()
        }
        return found
    }
}

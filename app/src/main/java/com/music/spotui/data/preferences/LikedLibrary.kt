package com.music.spotui.data.preferences

import android.content.Context
import com.music.spotui.data.api.YouTubeLibrary
import com.music.spotui.data.entity.SongsModel
import org.json.JSONArray
import org.json.JSONObject

/** Songs liked in Aalap, with their details, newest first. */
internal object AalapLikes {
    private const val PREF = "aalap_likes"

    private fun prefs(c: Context) = c.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    fun all(c: Context): List<SongsModel> = runCatching {
        val a = JSONArray(prefs(c).getString("songs", "[]"))
        (0 until a.length()).map { i ->
            a.getJSONObject(i).let { o ->
                SongsModel(o.getInt("id"), o.optString("title"), o.optString("album"), o.optString("singer"),
                    o.optString("cover"), o.optString("url"), o.optString("sid"), o.optBoolean("explicit"), o.optInt("dur"))
            }
        }
    }.getOrDefault(emptyList())

    private fun save(c: Context, songs: List<SongsModel>) {
        val a = JSONArray()
        songs.forEach { s ->
            a.put(JSONObject().apply {
                put("id", s.id); put("title", s.title); put("album", s.album); put("singer", s.singer)
                put("cover", s.coverUri); put("url", s.url); put("sid", s.spotifyTrackId)
                put("explicit", s.explicit); put("dur", s.durationMs)
            })
        }
        prefs(c).edit().putString("songs", a.toString()).apply()
    }

    fun add(c: Context, song: SongsModel) = save(c, listOf(song) + all(c).filterNot { it.id == song.id })
    fun remove(c: Context, id: Int) = all(c).let { list -> if (list.any { it.id == id }) save(c, list.filterNot { it.id == id }) }
}

/**
 * Liked Songs: the one list every screen shows and counts (Library, Profile, the Liked
 * Songs page). Your Spotify and YouTube Music likes, each song once, plus songs you
 * liked in Aalap that aren't on either yet (newest first), minus anything you've
 * unliked here since the last sync.
 */
object LikedLibrary {
    fun all(c: Context): List<SongsModel> {
        val likedIds = getLikedSongIds(c)
        val services = YouTubeLibrary.unifiedLiked(c)
        val serviceIds = services.mapTo(HashSet()) { it.id }
        val extra = AalapLikes.all(c).filter { it.id !in serviceIds && it.id in likedIds }
        // A song liked here that is the same song as one already in the list (another
        // service's copy) counts once.
        val extraKept = if (extra.isEmpty()) emptyList() else {
            val keep = YouTubeLibrary.mergeLiked(services, extra).mapTo(HashSet()) { it.id }
            extra.filter { it.id in keep }
        }
        // A song with no name can't be shown or played, so it isn't listed or counted.
        return (extraKept + services).filter { it.id in likedIds && it.title.isNotBlank() }.distinctBy { it.id }
    }

    fun count(c: Context) = all(c).size

    /** Clears the "song 0" like an old version could save when nothing was playing. */
    fun cleanUp(c: Context) {
        val p = c.getSharedPreferences("LikedSongs", Context.MODE_PRIVATE)
        if (p.contains("0")) p.edit().remove("0").apply()
    }
}

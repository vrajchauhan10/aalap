package com.music.spotui.data.local

import android.content.Context
import com.music.spotui.data.entity.LibraryEntry
import com.music.spotui.data.entity.SongsModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * Playlists you make in Aalap. They live on the phone and can hold any song, from
 * Spotify or YouTube Music alike, so they work across the whole unified library.
 */
object AalapPlaylists {
    private const val PREF = "aalap_playlists"
    const val PREFIX = "aalap-pl:"

    data class Playlist(val id: String, val name: String, val createdAt: Long, val songs: List<SongsModel>)

    /** Bumped on every change, so screens showing playlists refresh. */
    private val _version = MutableStateFlow(0)
    val version: StateFlow<Int> = _version

    fun isLocal(id: String) = id.startsWith(PREFIX)

    private fun prefs(c: Context) = c.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    private fun SongsModel.toJson() = JSONObject().apply {
        put("id", id); put("title", title); put("album", album); put("singer", singer); put("cover", coverUri)
        put("url", url); put("sid", spotifyTrackId); put("explicit", explicit); put("dur", durationMs)
    }

    private fun JSONObject.toSong() = SongsModel(
        getInt("id"), optString("title"), optString("album"), optString("singer"), optString("cover"),
        optString("url"), optString("sid"), optBoolean("explicit"), optInt("dur"),
    )

    @Synchronized
    fun all(c: Context): List<Playlist> = runCatching {
        val a = JSONArray(prefs(c).getString("list", "[]"))
        (0 until a.length()).map { i ->
            a.getJSONObject(i).let { o ->
                val s = o.optJSONArray("songs") ?: JSONArray()
                Playlist(o.getString("id"), o.optString("name"), o.optLong("created"), (0 until s.length()).map { s.getJSONObject(it).toSong() })
            }
        }
    }.getOrDefault(emptyList())

    fun get(c: Context, id: String): Playlist? = all(c).firstOrNull { it.id == id }

    @Synchronized
    private fun save(c: Context, list: List<Playlist>) {
        val a = JSONArray()
        list.forEach { p ->
            a.put(JSONObject().apply {
                put("id", p.id); put("name", p.name); put("created", p.createdAt)
                put("songs", JSONArray().also { s -> p.songs.forEach { s.put(it.toJson()) } })
            })
        }
        prefs(c).edit().putString("list", a.toString()).commit()
        _version.value++
    }

    /** Creates a playlist (newest first) and returns its id. */
    fun create(c: Context, name: String, first: SongsModel? = null): String {
        val id = PREFIX + java.util.UUID.randomUUID()
        save(c, listOf(Playlist(id, name.trim().ifBlank { "My playlist" }, System.currentTimeMillis(), listOfNotNull(first))) + all(c))
        return id
    }

    fun rename(c: Context, id: String, name: String) =
        save(c, all(c).map { if (it.id == id) it.copy(name = name.trim().ifBlank { it.name }) else it })

    fun delete(c: Context, id: String) = save(c, all(c).filterNot { it.id == id })

    fun contains(c: Context, id: String, song: SongsModel) = get(c, id)?.songs?.any { it.id == song.id } == true

    fun add(c: Context, id: String, song: SongsModel) =
        save(c, all(c).map { if (it.id == id && it.songs.none { s -> s.id == song.id }) it.copy(songs = it.songs + song) else it })

    fun remove(c: Context, id: String, songId: Int) =
        save(c, all(c).map { if (it.id == id) it.copy(songs = it.songs.filterNot { s -> s.id == songId }) else it })

    fun toggle(c: Context, id: String, song: SongsModel) = if (contains(c, id, song)) remove(c, id, song.id) else add(c, id, song)

    fun asLibraryEntries(c: Context): List<LibraryEntry> = all(c).map { p ->
        LibraryEntry(
            spotifyId = p.id,
            name = p.name,
            subtitle = "${p.songs.size} ${if (p.songs.size == 1) "song" else "songs"} · You",
            coverUri = p.songs.firstOrNull()?.coverUri.orEmpty(),
            isPlaylist = true,
        )
    }
}

package com.music.aalap.data.local

import android.content.Context
import com.music.aalap.data.api.Response
import com.music.aalap.data.entity.AlbumsModel
import com.music.aalap.data.entity.SongsModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Lets playlists, albums and Liked Songs open with no internet. Each list's songs (and its
 * title and cover) are kept on the phone when it loads online; with no internet the page shows
 * the songs from it you've downloaded instead of "no data". Up to [MAX_LISTS] lists, oldest
 * forgotten first, a few KB each.
 */
object OfflineLists {
    private const val MAX_LISTS = 300

    /** No internet right now, or offline mode is on. */
    val noInternet: Boolean
        get() = com.music.aalap.data.preferences.OfflineState.isOffline ||
            !com.music.aalap.data.preferences.OfflineState.networkAvailable

    private fun dir(c: Context) = File(c.filesDir, "offline_lists").apply { mkdirs() }
    private fun file(c: Context, key: String, kind: String) =
        File(dir(c), kind + "_" + Integer.toHexString(key.hashCode()) + "_" + key.length + ".json")

    fun saveSongs(c: Context, key: String, songs: List<SongsModel>) = runCatching {
        if (songs.isEmpty()) return@runCatching
        file(c, key, "s").writeText(songsToJson(songs))
        trim(c)
    }

    fun songs(c: Context, key: String): List<SongsModel>? = runCatching {
        file(c, key, "s").takeIf { it.exists() }?.readText()?.let(::songsFromJson)
    }.getOrNull()

    fun saveHeader(c: Context, key: String, a: AlbumsModel) = runCatching {
        file(c, key, "h").writeText(
            JSONObject().put("id", a.id).put("artists", a.artists).put("cover", a.coverUri)
                .put("name", a.name).put("time", a.time).put("type", a.type).toString()
        )
    }

    fun header(c: Context, key: String): AlbumsModel? = runCatching {
        file(c, key, "h").takeIf { it.exists() }?.readText()?.let {
            val o = JSONObject(it)
            AlbumsModel(o.getInt("id"), o.optString("artists"), o.optString("cover"), o.optString("name"), o.optString("time"), o.optString("type"))
        }
    }.getOrNull()

    private fun trim(c: Context) {
        val files = dir(c).listFiles().orEmpty()
        if (files.size <= MAX_LISTS * 2) return
        files.sortedBy { it.lastModified() }.take(files.size - MAX_LISTS * 2).forEach { it.delete() }
    }

    /** Of [songs], the ones that can play right now with no internet (downloaded). */
    fun playableNow(c: Context, songs: List<SongsModel>): List<SongsModel> {
        val ids = com.music.aalap.data.preferences.getDownloadedSongs(c).mapTo(HashSet()) { it.id }
        return songs.filter { it.id in ids }
    }

    /**
     * Wraps a list's live load: keeps what loaded for later, and with no internet (or when the
     * load fails) answers from what was kept, showing only the downloaded songs offline.
     */
    fun songsFlow(c: Context, key: String, live: suspend () -> Flow<Response<List<SongsModel>>>): Flow<Response<List<SongsModel>>> = flow {
        if (noInternet) { emit(offlineAnswer(c, key)); return@flow }
        var answered = false
        runCatching {
            live().collect { r ->
                when (r) {
                    is Response.Success -> { saveSongs(c, key, r.data); answered = true; emit(r) }
                    is Response.Error -> {
                        val kept = songs(c, key)
                        if (kept != null) { answered = true; emit(Response.Success(if (noInternet) playableNow(c, kept) else kept)) }
                        else emit(r)
                    }
                    is Response.Loading -> emit(r)
                }
            }
        }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it; if (!answered) emit(offlineAnswer(c, key)) }
    }

    fun headerFlow(c: Context, key: String, live: suspend () -> Flow<Response<AlbumsModel>>): Flow<Response<AlbumsModel>> = flow {
        if (noInternet) { emit(header(c, key)?.let { Response.Success(it) } ?: Response.Error(NO_INTERNET)); return@flow }
        live().collect { r ->
            when (r) {
                is Response.Success -> { saveHeader(c, key, r.data); emit(r) }
                is Response.Error -> emit(header(c, key)?.let { Response.Success(it) } ?: r)
                else -> emit(r)
            }
        }
    }

    const val NO_INTERNET = "No internet. Showing what's downloaded on this phone."

    private fun offlineAnswer(c: Context, key: String): Response<List<SongsModel>> {
        val kept = songs(c, key) ?: return Response.Error(NO_INTERNET)
        return Response.Success(playableNow(c, kept))
    }

    /**
     * Search with no internet: your downloads whose title, artist or album contain every word you
     * typed (any order, any case). Best matches first: title starts with it, then title has it.
     */
    fun searchDownloads(c: Context, query: String): List<SongsModel> =
        search(com.music.aalap.data.preferences.getDownloadedSongs(c), query)

    fun search(songs: List<SongsModel>, query: String): List<SongsModel> {
        fun norm(t: String) = t.lowercase().replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()
        val q = norm(query)
        if (q.isEmpty()) return emptyList()
        val words = q.split(' ').filter { it.isNotBlank() }
        return songs.filter { s -> val hay = norm("${s.title} ${s.singer} ${s.album}"); words.all { it in hay } }
            .sortedBy { s ->
                val t = norm(s.title)
                when { t.startsWith(q) -> 0; q in t -> 1; norm(s.singer).startsWith(q) -> 2; else -> 3 }
            }
    }

    private fun songsToJson(list: List<SongsModel>) = JSONArray().also { a ->
        list.forEach { m ->
            a.put(JSONObject().put("id", m.id).put("title", m.title).put("album", m.album).put("singer", m.singer)
                .put("cover", m.coverUri).put("url", m.url).put("sid", m.spotifyTrackId).put("explicit", m.explicit).put("dur", m.durationMs))
        }
    }.toString()

    private fun songsFromJson(s: String): List<SongsModel> {
        val a = JSONArray(s)
        return (0 until a.length()).map { i ->
            a.getJSONObject(i).let { o ->
                SongsModel(o.getInt("id"), o.optString("title"), o.optString("album"), o.optString("singer"),
                    o.optString("cover"), o.optString("url"), o.optString("sid"), o.optBoolean("explicit"), o.optInt("dur"))
            }
        }
    }
}

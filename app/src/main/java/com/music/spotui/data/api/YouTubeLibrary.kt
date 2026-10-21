package com.music.spotui.data.api

import android.content.Context
import android.util.Log
import com.metrolist.innertube.YouTube
import com.music.spotui.data.entity.ArtistsModel
import com.music.spotui.data.entity.SongsModel
import com.music.spotui.data.recommendation.engine.Candidate
import com.music.spotui.data.recommendation.engine.Personalizer
import com.music.spotui.data.recommendation.engine.RecoDb
import com.music.spotui.data.recommendation.engine.SongBridge
import com.music.spotui.data.recommendation.engine.SongMatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject

/**
 * The YouTube Music side of the unified library: your liked songs, the artists you
 * subscribe to, and what you've played recently there.
 *
 * Every song is matched to the same canonical song the Spotify side uses, so a song
 * liked on both shows once (as its Spotify listing, with its album art), and a
 * YouTube-only song plays the exact video you liked. Liked songs join the app's
 * likes, artists join your followed artists, and all of it feeds the taste profile.
 */
object YouTubeLibrary {
    private const val TAG = "YouTubeLibrary"
    private const val PREF = "youtube_library"
    private const val SYNC_EVERY_MS = 3_600_000L
    private val lock = Mutex()

    private fun prefs(c: Context) = c.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    fun lastSync(c: Context): Long = prefs(c).getLong("synced_at", 0)

    fun likedSongs(c: Context): List<SongsModel> = runCatching {
        val a = JSONArray(prefs(c).getString("songs", "[]"))
        (0 until a.length()).map { i ->
            a.getJSONObject(i).let { o ->
                SongsModel(
                    o.getInt("id"), o.optString("title"), o.optString("album"), o.optString("singer"),
                    o.optString("cover"), o.optString("url"), o.optString("sid"), o.optBoolean("explicit"), o.optInt("dur"),
                ).also { s -> o.optString("yt").takeIf { it.isNotBlank() }?.let { com.music.spotui.di.SongPlayer.rememberVideoId(s.url, it) } }
            }
        }
    }.getOrDefault(emptyList())

    fun artists(c: Context): List<ArtistsModel> = runCatching {
        val hidden = hiddenArtists(c)
        val a = JSONArray(prefs(c).getString("artists", "[]"))
        (0 until a.length()).map { i -> a.getJSONObject(i).let { ArtistsModel(it.optString("name"), it.optString("cover"), "") } }
            .filter { SongMatcher.artistKeys(it.name).firstOrNull() !in hidden }
    }.getOrDefault(emptyList())

    /** Artists unfollowed in Aalap (keys): kept out of the library even if a service still lists them. */
    fun hiddenArtists(c: Context): Set<String> = prefs(c).getStringSet("hidden_artists", null).orEmpty()

    fun setArtistHidden(c: Context, name: String, hidden: Boolean) {
        val key = SongMatcher.artistKeys(name).firstOrNull() ?: return
        val now = hiddenArtists(c).toMutableSet().apply { if (hidden) add(key) else remove(key) }
        prefs(c).edit().putStringSet("hidden_artists", now).apply()
    }

    /** Artist → plays in your recent YouTube Music history (a taste signal). */
    fun historyArtistCounts(c: Context): Map<String, Int> = runCatching {
        val o = JSONObject(prefs(c).getString("history_artists", "{}"))
        o.keys().asSequence().associateWith { o.getInt(it) }
    }.getOrDefault(emptyMap())

    /** Syncs when signed in and the last sync is older than 6 hours (or [force]). */
    suspend fun syncIfDue(c: Context, force: Boolean = false): Boolean {
        if (!com.music.spotui.data.preferences.isYoutubeLoggedIn(c)) return false
        if (!force && System.currentTimeMillis() - lastSync(c) < SYNC_EVERY_MS) return false
        return sync(c)
    }

    suspend fun sync(c: Context): Boolean = lock.withLock {
        coroutineScope {
            val likedJob = async { YouTube.likedSongs().onFailure { Log.w(TAG, "liked songs: $it") }.getOrNull() }
            val artistsJob = async { YouTube.libraryArtists().onFailure { Log.w(TAG, "artists: $it") }.getOrNull() }
            val historyJob = async { YouTube.history().onFailure { Log.w(TAG, "history: $it") }.getOrNull() }
            val liked = likedJob.await()
            val artists = artistsJob.await()
            val history = historyJob.await()
            if (liked == null && artists == null && history == null) return@coroutineScope false

            val db = RecoDb.get(c)
            val edit = prefs(c).edit()
            if (liked != null) {
                val songs = db.resolveAll(liked.map {
                    Candidate(title = it.title, artists = it.artists, durationSec = it.durationSec, album = it.album, cover = it.thumbnail, ytId = it.videoId)
                })
                val models = songs.map(SongBridge::toSongsModel).distinctBy { it.id }
                db.addLiked(RecoDb.userId(c), songs.map { it.id })
                com.music.spotui.data.preferences.syncYouTubeLikedSongIds(c, models.mapTo(HashSet()) { it.id.toString() })
                val a = JSONArray()
                songs.zip(models).forEach { (s, m) ->
                    a.put(JSONObject().apply {
                        put("id", m.id); put("title", m.title); put("album", m.album); put("singer", m.singer)
                        put("cover", m.coverUri); put("url", m.url); put("sid", m.spotifyTrackId)
                        put("explicit", m.explicit); put("dur", m.durationMs); put("yt", s.info.ytId)
                    })
                }
                edit.putString("songs", a.toString())
            }
            if (artists != null) {
                val a = JSONArray()
                artists.forEach { a.put(JSONObject().put("name", it.name).put("cover", it.thumbnail)) }
                edit.putString("artists", a.toString())
            }
            if (history != null) {
                val counts = history.flatMap { h -> h.artists.split(",").map(String::trim).filter(String::isNotBlank).take(2) }
                    .groupingBy { it }.eachCount()
                edit.putString("history_artists", JSONObject(counts as Map<*, *>).toString())
                // Recent YouTube plays also count as songs you know, for Your Mix and the engine.
                db.resolveAll(history.map { Candidate(title = it.title, artists = it.artists, durationSec = it.durationSec, album = it.album, cover = it.thumbnail, ytId = it.videoId) })
            }
            edit.putLong("synced_at", System.currentTimeMillis()).commit()
            invalidateUnified()
            Personalizer.invalidate()
            Log.d(TAG, "synced: ${liked?.size} liked, ${artists?.size} artists, ${history?.size} recent")
            true
        }
    }

    private fun toJson(list: List<SongsModel>) = JSONArray().also { a ->
        list.forEach { m ->
            a.put(JSONObject().apply {
                put("id", m.id); put("title", m.title); put("album", m.album); put("singer", m.singer)
                put("cover", m.coverUri); put("url", m.url); put("sid", m.spotifyTrackId)
                put("explicit", m.explicit); put("dur", m.durationMs)
            })
        }
    }.toString()

    /** The full Spotify liked list from the last complete fetch (so the merged list is available instantly). */
    fun saveSpotifyLiked(c: Context, songs: List<SongsModel>) {
        prefs(c).edit().putString("spotify_liked", toJson(songs)).commit()
        invalidateUnified()
    }

    fun spotifyLiked(c: Context): List<SongsModel> = runCatching {
        val a = JSONArray(prefs(c).getString("spotify_liked", "[]"))
        (0 until a.length()).map { i ->
            a.getJSONObject(i).let { o ->
                SongsModel(o.getInt("id"), o.optString("title"), o.optString("album"), o.optString("singer"),
                    o.optString("cover"), o.optString("url"), o.optString("sid"), o.optBoolean("explicit"), o.optInt("dur"))
            }
        }
    }.getOrDefault(emptyList())

    @Volatile private var unifiedCache: List<SongsModel>? = null

    /**
     * The one liked list Aalap shows everywhere: Spotify + YouTube Music, each song once.
     * Kept in memory and rebuilt only after a sync; call it off the main thread.
     */
    fun unifiedLiked(c: Context): List<SongsModel> =
        unifiedCache ?: mergeLiked(spotifyLiked(c), likedSongs(c)).also { unifiedCache = it }

    /** Drops the in-memory merged list (after either side changed). */
    fun invalidateUnified() { unifiedCache = null }

    /**
     * Spotify's liked songs and YouTube Music's in one list, a song liked on both only
     * once. Both lists are newest first, so they're interleaved by position.
     */
    fun mergeLiked(spotify: List<SongsModel>, youtube: List<SongsModel>): List<SongsModel> {
        val spotifyIds = spotify.mapTo(HashSet()) { it.id }
        // Index Spotify songs by main artist, so each YouTube song is only compared with
        // the same artist's songs (not with the whole list).
        val byArtist = HashMap<String, MutableList<SongsModel>>()
        val byTitle = HashMap<String, MutableList<SongsModel>>()
        spotify.forEach { s ->
            SongMatcher.artistKeys(s.singer).firstOrNull()?.let { byArtist.getOrPut(it) { ArrayList() } += s }
            byTitle.getOrPut(SongMatcher.titleKey(s.title, s.singer)) { ArrayList() } += s
        }
        fun same(a: SongsModel, b: SongsModel) =
            SongMatcher.sameSong(SongBridge.toCandidate(a).copy(spotifyId = ""), SongBridge.toCandidate(b).copy(spotifyId = ""))
        val ytOnly = youtube.filter { y ->
            if (y.id in spotifyIds) return@filter false
            val sameArtist = SongMatcher.artistKeys(y.singer).flatMap { byArtist[it].orEmpty() }
            val sameTitle = byTitle[SongMatcher.titleKey(y.title, y.singer)].orEmpty()
            (sameArtist + sameTitle).none { same(it, y) }
        }.distinctBy { it.id }
        if (ytOnly.isEmpty()) return spotify
        if (spotify.isEmpty()) return ytOnly
        val out = ArrayList<SongsModel>(spotify.size + ytOnly.size)
        var i = 0; var j = 0
        while (i < spotify.size || j < ytOnly.size) {
            val pi = if (i < spotify.size) i.toDouble() / spotify.size else Double.MAX_VALUE
            val pj = if (j < ytOnly.size) j.toDouble() / ytOnly.size else Double.MAX_VALUE
            if (pi <= pj) out += spotify[i++] else out += ytOnly[j++]
        }
        return out.distinctBy { it.id }
    }

    /** Followed artists from both services, each artist once. */
    fun mergeArtists(spotify: List<ArtistsModel>, youtube: List<ArtistsModel>): List<ArtistsModel> {
        val hidden = hiddenArtists(com.music.spotui.MyApplication.instance)
        val sp = spotify.filter { SongMatcher.artistKeys(it.name).firstOrNull() !in hidden }.distinctBy { SongMatcher.artistKeys(it.name).firstOrNull() ?: it.name }
        val keys = sp.mapNotNullTo(HashSet()) { SongMatcher.artistKeys(it.name).firstOrNull() }
        return sp + youtube.filter { y -> SongMatcher.artistKeys(y.name).firstOrNull()?.let { it !in keys } ?: false }
    }
}

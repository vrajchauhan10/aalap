package com.music.spotui.data.preferences

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Local listening history: one entry per playback start, newest first, capped.
 * Backs the History & stats screen; individual entries can be deleted and the
 * whole log cleared.
 */
data class HistoryEntry(
    val ts: Long,
    val songId: Int,
    val title: String,
    val singer: String,
    val album: String = "",
    val image: String = "",
    // Playback query + Spotify id so a history row can be played again, and the track
    // length for listening-time stats. Empty/0 on entries logged before 2.0.
    val url: String = "",
    val spotifyTrackId: String = "",
    val durationMs: Int = 0,
    // How long the music actually played (paused/buffering time not counted), measured
    // by ListenClock. -1 on entries logged before it existed.
    val listenedMs: Long = -1L,
) {
    val canReplay: Boolean get() = url.isNotBlank()
}

private const val PREF = "ListeningHistory"
private const val KEY = "entries"
private const val MAX_ENTRIES = 500

private fun HistoryEntry.toJson() = JSONObject().apply {
    put("ts", ts); put("songId", songId); put("title", title)
    put("singer", singer); put("album", album); put("image", image)
    put("url", url); put("spotifyTrackId", spotifyTrackId); put("durationMs", durationMs)
    put("listenedMs", listenedMs)
}

private fun JSONObject.toEntry() = HistoryEntry(
    ts = optLong("ts"), songId = optInt("songId", -1), title = optString("title"),
    singer = optString("singer"), album = optString("album"), image = optString("image"),
    url = optString("url"), spotifyTrackId = optString("spotifyTrackId"), durationMs = optInt("durationMs"),
    listenedMs = optLong("listenedMs", -1L),
)

fun getListeningHistory(context: Context): List<HistoryEntry> {
    val raw = context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(KEY, null)
        ?: return emptyList()
    return runCatching {
        val arr = JSONArray(raw)
        (0 until arr.length()).map { arr.getJSONObject(it).toEntry() }
    }.getOrDefault(emptyList())
}

private fun save(context: Context, entries: List<HistoryEntry>) {
    val arr = JSONArray().also { a -> entries.forEach { a.put(it.toJson()) } }
    context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        .edit().putString(KEY, arr.toString()).apply()
}

fun addListeningHistory(context: Context, entry: HistoryEntry) {
    if (entry.title.isBlank()) return
    val existing = getListeningHistory(context)
    // Don't log the same track twice in a row (pause/resume, seek restarts).
    if (existing.firstOrNull()?.songId == entry.songId) return
    save(context, (listOf(entry) + existing).take(MAX_ENTRIES))
}

/** Records how long [songId]'s latest history entry has really played so far. */
fun setListenedTime(context: Context, songId: Int, listenedMs: Long) {
    val entries = getListeningHistory(context)
    val i = entries.indexOfFirst { it.songId == songId }
    if (i < 0 || i > 2) return // only the song playing now (or just before)
    if (entries[i].listenedMs >= listenedMs) return
    save(context, entries.toMutableList().also { it[i] = it[i].copy(listenedMs = listenedMs) })
}

/**
 * Measures real listening: time counts only while audio is actually playing (not
 * paused, buffering or stopped), and is saved into the song's history entry every
 * 15 s and whenever the song changes, so closing the app loses at most a few seconds.
 */
object ListenClock {
    private var songId = -1
    private var accumulated = 0L
    private var playingSince = 0L
    private var context: Context? = null
    private val main = android.os.Handler(android.os.Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() { flush(); if (playingSince > 0) main.postDelayed(this, 15_000) }
    }

    private fun now() = android.os.SystemClock.elapsedRealtime()

    private fun total() = accumulated + if (playingSince > 0) now() - playingSince else 0L

    /** A song started (or the same song restarted on repeat: it keeps counting). */
    @Synchronized fun songStarted(c: Context, id: Int) {
        context = c.applicationContext
        if (id == songId) return
        flush()
        songId = id
        accumulated = 0L
        if (playingSince > 0) playingSince = now()
    }

    /** Audio started or stopped coming out (ExoPlayer's isPlaying). */
    @Synchronized fun playingChanged(playing: Boolean) {
        if (playing && playingSince == 0L) {
            playingSince = now()
            main.removeCallbacks(tick); main.postDelayed(tick, 15_000)
        } else if (!playing && playingSince > 0) {
            accumulated += now() - playingSince
            playingSince = 0L
            main.removeCallbacks(tick)
            flush()
        }
    }

    @Synchronized private fun flush() {
        val c = context ?: return
        if (songId < 0) return
        val ms = total()
        if (ms > 0) setListenedTime(c, songId, ms)
    }
}

fun removeListeningHistory(context: Context, entry: HistoryEntry) {
    save(context, getListeningHistory(context).filterNot { it.ts == entry.ts && it.songId == entry.songId })
}

fun clearListeningHistory(context: Context) {
    context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().remove(KEY).apply()
}

private const val KEY_LIST_CLEARED_AT = "list_cleared_at"

/**
 * "Clear all" on History hides the recent-plays list without deleting the plays, so
 * stats, top artists, Wrapped and recommendations keep everything.
 */
fun getHistoryListClearedAt(context: Context): Long =
    context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getLong(KEY_LIST_CLEARED_AT, 0L)

fun clearHistoryList(context: Context) =
    context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
        .putLong(KEY_LIST_CLEARED_AT, System.currentTimeMillis()).apply()

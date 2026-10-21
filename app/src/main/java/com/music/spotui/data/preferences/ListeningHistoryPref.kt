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
}

private fun JSONObject.toEntry() = HistoryEntry(
    ts = optLong("ts"), songId = optInt("songId", -1), title = optString("title"),
    singer = optString("singer"), album = optString("album"), image = optString("image"),
    url = optString("url"), spotifyTrackId = optString("spotifyTrackId"), durationMs = optInt("durationMs"),
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

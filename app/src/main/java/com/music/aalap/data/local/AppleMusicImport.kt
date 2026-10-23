package com.music.aalap.data.local

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject

/**
 * Imports an Apple Music library so its artists count toward the listener's taste.
 * Apple has no public API for this without a developer account, so the listener
 * exports their library and picks the file here. Accepted:
 *
 *  - Apple's data export (privacy.apple.com): "Apple Music Library Tracks.json",
 *    or "Apple Music - Play History Daily Tracks.csv",
 *  - the Music app on a Mac / iTunes on Windows: File → Library → Export Library… (Library.xml),
 *  - any CSV with Title/Name and Artist columns.
 */
object AppleMusicImport {
    private const val PREF = "apple_music_import"
    private const val KEY = "tracks"
    private const val KEY_AT = "imported_at"

    data class Track(val title: String, val artist: String, val plays: Int)

    fun tracks(context: Context): List<Track> = runCatching {
        val a = JSONArray(context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(KEY, "[]"))
        (0 until a.length()).map { a.getJSONObject(it).let { o -> Track(o.optString("t"), o.optString("a"), o.optInt("p")) } }
    }.getOrDefault(emptyList())

    fun importedAt(context: Context): Long = context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getLong(KEY_AT, 0)

    /** Artist → weight (tracks in the library, plus plays). Read by the taste profile. */
    fun artistCounts(context: Context): Map<String, Int> =
        tracks(context).filter { it.artist.isNotBlank() }
            .groupBy { it.artist }
            .mapValues { (_, ts) -> ts.size + ts.sumOf { it.plays } / 10 }

    fun clear(context: Context) = context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().clear().apply()

    /** Reads [uri] and stores what it found. Returns the number of tracks imported. */
    fun import(context: Context, uri: Uri): Int {
        val text = context.contentResolver.openInputStream(uri)?.use { it.bufferedReader().readText() }.orEmpty()
        val found = when {
            text.trimStart().startsWith("[") -> parseJson(text)
            text.contains("<plist") || text.contains("<key>Tracks</key>") -> parseXml(text)
            else -> parseCsv(text)
        }.filter { it.title.isNotBlank() }
            .groupBy { "${it.title.lowercase()}|${it.artist.lowercase()}" }
            .map { (_, same) -> same.first().copy(plays = same.sumOf { it.plays }) }
        if (found.isEmpty()) return 0
        val a = JSONArray()
        found.forEach { a.put(JSONObject().put("t", it.title).put("a", it.artist).put("p", it.plays)) }
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
            .putString(KEY, a.toString()).putLong(KEY_AT, System.currentTimeMillis()).apply()
        com.music.aalap.data.recommendation.engine.Personalizer.invalidate()
        return found.size
    }

    private fun parseJson(text: String): List<Track> {
        val a = JSONArray(text)
        return (0 until a.length()).mapNotNull { i ->
            val o = a.optJSONObject(i) ?: return@mapNotNull null
            Track(
                title = o.optString("Title").ifBlank { o.optString("Song Name") }.ifBlank { o.optString("Name") },
                artist = o.optString("Artist").ifBlank { o.optString("Artist Name") },
                plays = o.optInt("Play Count", o.optInt("Track Play Count", 0)),
            )
        }
    }

    /** iTunes/Music "Library.xml": one <dict> per track with Name / Artist / Play Count keys. */
    private fun parseXml(text: String): List<Track> {
        fun value(dict: String, key: String): String? =
            Regex("<key>${Regex.escape(key)}</key>\\s*<(string|integer)>(.*?)</\\1>", RegexOption.DOT_MATCHES_ALL)
                .find(dict)?.groupValues?.get(2)
                ?.replace("&amp;", "&")?.replace("&#38;", "&")?.replace("&lt;", "<")?.replace("&gt;", ">")?.replace("&apos;", "'")?.replace("&quot;", "\"")
        return Regex("<dict>(.*?)</dict>", RegexOption.DOT_MATCHES_ALL).findAll(text).mapNotNull { m ->
            val d = m.groupValues[1]
            val name = value(d, "Name") ?: return@mapNotNull null
            if (value(d, "Track ID") == null) return@mapNotNull null
            Track(name, value(d, "Artist").orEmpty(), value(d, "Play Count")?.toIntOrNull() ?: 0)
        }.toList()
    }

    private fun parseCsv(text: String): List<Track> {
        val lines = text.lineSequence().filter { it.isNotBlank() }.toList()
        if (lines.size < 2) return emptyList()
        val header = splitCsv(lines.first()).map { it.trim().lowercase() }
        val title = header.indexOfFirst { it == "title" || it == "song name" || it == "name" || it == "track name" }
        val artist = header.indexOfFirst { it == "artist" || it == "artist name" }
        val desc = header.indexOf("track description") // "Artist - Title"
        val plays = header.indexOfFirst { it.contains("play count") }
        return lines.drop(1).mapNotNull { line ->
            val c = splitCsv(line)
            val count = c.getOrNull(plays)?.trim()?.toIntOrNull() ?: 0
            when {
                title >= 0 -> Track(c.getOrNull(title).orEmpty().trim(), c.getOrNull(artist).orEmpty().trim(), count)
                desc >= 0 -> c.getOrNull(desc)?.split(" - ", limit = 2)?.takeIf { it.size == 2 }
                    ?.let { Track(it[1].trim(), it[0].trim(), count) }
                else -> null
            }
        }
    }

    private fun splitCsv(line: String): List<String> {
        val out = ArrayList<String>()
        val cur = StringBuilder()
        var quoted = false
        var i = 0
        while (i < line.length) {
            val ch = line[i]
            when {
                ch == '"' && quoted && i + 1 < line.length && line[i + 1] == '"' -> { cur.append('"'); i++ }
                ch == '"' -> quoted = !quoted
                ch == ',' && !quoted -> { out += cur.toString(); cur.clear() }
                else -> cur.append(ch)
            }
            i++
        }
        out += cur.toString()
        return out
    }
}

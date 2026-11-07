package com.music.aalap.ui.share

import com.music.aalap.data.entity.SongsModel
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * Sharing a playlist: a message anyone can read (the name and the songs), with a small
 * Aalap code at the end. Shared into Aalap (or pasted), the code becomes a playlist on that
 * phone. No server: the whole playlist travels in the message, so it works offline too.
 */
object PlaylistShare {
    const val PREFIX = "AALAP-PLAYLIST:"
    private const val MAX_SONGS = 100

    fun message(name: String, songs: List<SongsModel>): String {
        val list = songs.take(15).mapIndexed { i, s -> "${i + 1}. ${s.title} · ${s.singer.substringBefore(",").trim()}" }
        val more = if (songs.size > 15) "\n…and ${songs.size - 15} more" else ""
        return "🎵 $name on Aalap (${songs.size} songs)\n\n${list.joinToString("\n")}$more\n\n" +
            "Have Aalap? Share this message to Aalap to save the playlist.\n${encode(name, songs)}"
    }

    fun encode(name: String, songs: List<SongsModel>): String {
        val o = JSONObject().put("v", 1).put("n", name).put("s", JSONArray(songs.take(MAX_SONGS).map {
            JSONArray(listOf(it.id, it.title, it.singer, it.album, it.coverUri, it.url, it.spotifyTrackId, it.durationMs))
        }))
        val bytes = ByteArrayOutputStream().also { out -> GZIPOutputStream(out).use { it.write(o.toString().toByteArray()) } }.toByteArray()
        return PREFIX + java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    /** The playlist in [text] (a message can have other words around the code), or null. */
    fun decode(text: String): Pair<String, List<SongsModel>>? = runCatching {
        val code = Regex("""${Regex.escape(PREFIX)}([A-Za-z0-9_-]+)""").find(text)?.groupValues?.get(1) ?: return null
        val bytes = java.util.Base64.getUrlDecoder().decode(code)
        if (bytes.size > 64 * 1024) return null
        // Read at most 512 KB unpacked, so a crafted code can't balloon in memory.
        val raw = GZIPInputStream(bytes.inputStream()).use { input ->
            val out = ByteArrayOutputStream(); val buf = ByteArray(8192); var n: Int
            while (input.read(buf).also { n = it } > 0) { out.write(buf, 0, n); if (out.size() > 512 * 1024) return null }
            out.toString()
        }
        val o = JSONObject(raw)
        val arr = o.getJSONArray("s")
        val songs = (0 until minOf(arr.length(), MAX_SONGS)).mapNotNull { i ->
            val a = arr.getJSONArray(i)
            val url = a.optString(5)
            // Only songs Aalap itself makes; nothing that could point playback somewhere odd.
            if (url.isBlank() || url.startsWith("file:") || url.startsWith("content:")) return@mapNotNull null
            SongsModel(a.optInt(0), a.optString(1).take(200), a.optString(3).take(200), a.optString(2).take(300), a.optString(4).take(500), url.take(500), a.optString(6).take(40), durationMs = a.optInt(7))
        }.filter { it.title.isNotBlank() }
        if (songs.isEmpty()) null else o.optString("n").take(80).ifBlank { "Shared playlist" } to songs
    }.getOrNull()
}

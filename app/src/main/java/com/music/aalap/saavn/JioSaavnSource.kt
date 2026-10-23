package com.music.aalap.saavn

import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import kotlin.math.abs

/**
 * JioSaavn as an audio source: a large Indian catalogue (Bollywood, Gujarati, Punjabi,
 * regional) served as AAC up to 320 kbps straight from its CDN. Independent of YouTube,
 * so playback keeps working when YouTube changes its player or throttles a connection.
 *
 * A track is only used when it really is the same recording: title, artist and length
 * (within a few seconds) must agree, and remixes / lo-fi / covers are avoided unless the
 * requested track is one.
 */
object JioSaavnSource {
    private const val TAG = "JioSaavn"
    private const val SEARCH =
        "https://www.jiosaavn.com/api.php?__call=search.getResults&_format=json&_marker=0&api_version=4&ctx=web6dot0&n=10&p=1&q="
    private const val DES_KEY = "38346591"
    private const val UA = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"

    data class Match(
        val url: String,
        val kbps: Int,
        val title: String,
        val artists: String,
        val durationSec: Int,
    )

    private val VERSION_WORDS = listOf(
        "remix", "lofi", "lo-fi", "slowed", "reverb", "cover", "karaoke", "instrumental",
        "unplugged", "mashup", "8d", "sped up", "reprise", "live",
        "vocals", "acapella", "a cappella", "a capella", "beats", "ringtone",
    )

    private fun clean(s: String): String = android.text.Html.fromHtml(s, android.text.Html.FROM_HTML_MODE_LEGACY).toString()

    private fun norm(s: String) = clean(s).lowercase()
        .replace(Regex("""\(.*?\)|\[.*?]"""), " ")
        .replace(Regex("""\bfrom\b.*$"""), " ")
        .replace(Regex("""[^\p{L}\p{N} ]"""), " ")
        .replace(Regex("""\s+"""), " ").trim()

    private fun tokens(s: String) = norm(s).split(' ').filter { it.isNotBlank() }.toSet()

    private fun titleSimilarity(a: String, b: String): Float {
        val ta = tokens(a); val tb = tokens(b)
        if (ta.isEmpty() || tb.isEmpty()) return 0f
        val inter = ta.intersect(tb).size.toFloat()
        return inter / minOf(ta.size, tb.size) * 0.7f + inter / ta.union(tb).size * 0.3f
    }

    private fun artistMatch(wanted: String, have: String): Boolean {
        val w = wanted.split(",", "&").map { norm(it) }.filter { it.isNotBlank() }
        val h = norm(have)
        return w.any { h.contains(it) || (h.length > 3 && it.contains(h)) }
    }

    private fun versionWords(s: String) = VERSION_WORDS.filter { clean(s).lowercase().contains(it) }.toSet()

    /**
     * Finds [title] by [artist] on JioSaavn. [relaxed] accepts a looser match (used only
     * as the last resort before giving up on a song).
     */
    suspend fun resolve(
        title: String,
        artist: String,
        durationSec: Int,
        fallbackQuery: String,
        relaxed: Boolean = false,
    ): Match? = withContext(Dispatchers.IO) {
        val query = if (title.isNotBlank()) "${norm(title)} ${artist.substringBefore(",").trim()}".trim() else fallbackQuery
        val results = runCatching { search(query) }.onFailure { Log.w(TAG, "search failed: $it") }.getOrNull()
            ?: return@withContext null
        val wantTitle = title.ifBlank { fallbackQuery }
        val wantVersion = versionWords(wantTitle)
        val best = results.mapNotNull { r ->
            val mi = r.optJSONObject("more_info") ?: return@mapNotNull null
            val rTitle = clean(r.optString("title"))
            val artists = mi.optJSONObject("artistMap")?.let { am ->
                (am.optJSONArray("primary_artists")?.let { a -> (0 until a.length()).map { a.getJSONObject(it).optString("name") } }.orEmpty() +
                    am.optJSONArray("featured_artists")?.let { a -> (0 until a.length()).map { a.getJSONObject(it).optString("name") } }.orEmpty())
                    .joinToString(", ")
            }.orEmpty()
            val dur = mi.optString("duration").toIntOrNull() ?: 0
            val enc = mi.optString("encrypted_media_url").ifBlank { return@mapNotNull null }
            val sim = titleSimilarity(wantTitle, rTitle)
            val artistOk = artist.isBlank() || artistMatch(artist, artists)
            val durDiff = if (durationSec > 0 && dur > 0) abs(durationSec - dur) else -1
            val versionOk = versionWords(rTitle) == wantVersion
            val accepted = if (!relaxed) {
                sim >= 0.6f && artistOk && versionOk && (durDiff in 0..8 || durDiff == -1)
            } else {
                sim >= 0.5f && (artistOk || durDiff in 0..5) && (durDiff in 0..20 || durDiff == -1)
            }
            if (!accepted) return@mapNotNull null
            val score = sim * 2f + (if (artistOk) 1f else 0f) + (if (versionOk) 0.5f else 0f) +
                (if (durDiff in 0..3) 1f else if (durDiff in 4..8) 0.5f else 0f)
            Triple(r, score, Triple(rTitle, artists, dur to enc))
        }.maxByOrNull { it.second } ?: return@withContext null.also {
            Log.d(TAG, "no ${if (relaxed) "relaxed " else ""}match for '$wantTitle' / '$artist' (${results.size} results)")
        }

        val (rTitle, artists, durEnc) = best.third
        val (dur, enc) = durEnc
        val has320 = best.first.optJSONObject("more_info")?.optString("320kbps") == "true"
        val base = runCatching { decrypt(enc) }.getOrNull() ?: return@withContext null
        val candidates = (if (has320) listOf(320, 160, 96) else listOf(160, 96))
        for (kbps in candidates) {
            val url = base.replace(Regex("""_(96|160|320)\.mp4"""), "_$kbps.mp4")
            if (reachable(url)) {
                Log.d(TAG, "matched '$wantTitle' → '$rTitle' by $artists (${dur}s, $kbps kbps)")
                return@withContext Match(url, kbps, rTitle, artists, dur)
            }
        }
        null
    }

    private fun artistNames(mi: JSONObject?): String = mi?.optJSONObject("artistMap")?.let { am ->
        (am.optJSONArray("primary_artists")?.let { a -> (0 until a.length()).map { a.getJSONObject(it).optString("name") } }.orEmpty() +
            am.optJSONArray("featured_artists")?.let { a -> (0 until a.length()).map { a.getJSONObject(it).optString("name") } }.orEmpty())
            .distinct().joinToString(", ") { clean(it) }
    }.orEmpty()

    private fun JSONObject.toCandidate(): com.music.aalap.data.recommendation.engine.Candidate {
        val mi = optJSONObject("more_info")
        return com.music.aalap.data.recommendation.engine.Candidate(
            title = clean(optString("title")),
            artists = artistNames(mi).ifBlank { clean(optString("subtitle")).substringBefore(" - ") },
            durationSec = mi?.optString("duration")?.toIntOrNull() ?: 0,
            album = clean(mi?.optString("album").orEmpty()),
            // Bigger artwork than the 150px default.
            cover = optString("image").replace("150x150", "500x500"),
            saavnId = optString("id"),
            language = optString("language"),
            explicit = optString("explicit_content") == "1",
        )
    }

    /**
     * JioSaavn's listing of a song (id + language), with the same strict matching as
     * playback: title, artist, version and length must agree.
     */
    suspend fun findSong(title: String, artist: String, durationSec: Int): com.music.aalap.data.recommendation.engine.Candidate? =
        withContext(Dispatchers.IO) {
            val results = runCatching { search("${norm(title)} ${artist.substringBefore(",").trim()}".trim()) }.getOrNull()
                ?: return@withContext null
            val wantVersion = versionWords(title)
            results.filter { r ->
                val mi = r.optJSONObject("more_info") ?: return@filter false
                val dur = mi.optString("duration").toIntOrNull() ?: 0
                val durDiff = if (durationSec > 0 && dur > 0) abs(durationSec - dur) else -1
                titleSimilarity(title, clean(r.optString("title"))) >= 0.6f &&
                    (artist.isBlank() || artistMatch(artist, artistNames(mi))) &&
                    versionWords(clean(r.optString("title"))) == wantVersion &&
                    (durDiff in 0..8 || durDiff == -1)
            }.maxByOrNull { titleSimilarity(title, clean(it.optString("title"))) }?.toCandidate()
        }

    /** JioSaavn's song radio after [songId]: what its listeners hear next, best first. */
    suspend fun radio(songId: String, count: Int = 25): List<com.music.aalap.data.recommendation.engine.Candidate> =
        withContext(Dispatchers.IO) {
            val api = "https://www.jiosaavn.com/api.php?_format=json&_marker=0&api_version=4&ctx=android&__call="
            val ids = URLEncoder.encode("[\"$songId\"]", "UTF-8")
            val station = JSONObject(get("${api}webradio.createEntityStation&entity_type=queue&entity_id=$ids"))
                .optString("stationid").ifBlank { return@withContext emptyList() }
            val body = JSONObject(get("${api}webradio.getSong&k=$count&next=1&stationid=${URLEncoder.encode(station, "UTF-8")}"))
            (0 until count).mapNotNull { i -> body.optJSONObject(i.toString())?.optJSONObject("song")?.toCandidate() }
                .filter { it.saavnId != songId && it.title.isNotBlank() }
        }

    private fun get(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 5_000
            conn.readTimeout = 6_000
            conn.setRequestProperty("User-Agent", UA)
            conn.setRequestProperty("Accept", "application/json")
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    private fun search(query: String): List<JSONObject> {
        val conn = URL(SEARCH + URLEncoder.encode(query, "UTF-8")).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 5_000
            conn.readTimeout = 6_000
            conn.setRequestProperty("User-Agent", UA)
            conn.setRequestProperty("Accept", "application/json")
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val arr = JSONObject(body).optJSONArray("results") ?: return emptyList()
            return (0 until arr.length()).map { arr.getJSONObject(it) }
        } finally {
            conn.disconnect()
        }
    }

    /** JioSaavn media URLs are DES/ECB-encrypted with a fixed public key. */
    private fun decrypt(encrypted: String): String {
        val cipher = Cipher.getInstance("DES/ECB/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(DES_KEY.toByteArray(), "DES"))
        return String(cipher.doFinal(Base64.decode(encrypted, Base64.DEFAULT))).trim()
    }

    private fun reachable(url: String): Boolean = runCatching {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.requestMethod = "HEAD"
            c.connectTimeout = 4_000
            c.readTimeout = 4_000
            c.setRequestProperty("User-Agent", UA)
            c.responseCode in 200..299
        } finally {
            c.disconnect()
        }
    }.getOrDefault(false)
}

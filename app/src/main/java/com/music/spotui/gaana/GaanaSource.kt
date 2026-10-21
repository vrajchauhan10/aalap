package com.music.spotui.gaana

import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.math.abs

/**
 * Gaana as the last audio fallback: another large Indian catalogue, streamed as HLS.
 * Matched by ISRC when known (exact recording), else by title, artist and length.
 */
object GaanaSource {
    private const val TAG = "Gaana"
    private const val API = "https://gaana.com/apiv2"
    private const val KEY = "gy1t#b@jl(b\$wtme"
    private const val UA = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"

    data class Match(val url: String, val title: String, val artists: String, val durationSec: Int)

    private fun norm(s: String) = s.lowercase()
        .replace(Regex("""\(.*?\)|\[.*?]"""), " ")
        .replace(Regex("""[^\p{L}\p{N} ]"""), " ")
        .replace(Regex("""\s+"""), " ").trim()

    private fun similarity(a: String, b: String): Float {
        val ta = norm(a).split(' ').filter { it.isNotBlank() }.toSet()
        val tb = norm(b).split(' ').filter { it.isNotBlank() }.toSet()
        if (ta.isEmpty() || tb.isEmpty()) return 0f
        return ta.intersect(tb).size.toFloat() / minOf(ta.size, tb.size)
    }

    suspend fun resolve(title: String, artist: String, durationSec: Int, isrc: String, fallbackQuery: String): Match? =
        withContext(Dispatchers.IO) {
            val query = if (title.isNotBlank()) "${norm(title)} ${artist.substringBefore(",").trim()}" else fallbackQuery
            val hits = runCatching { search(query) }.onFailure { Log.w(TAG, "search failed: $it") }.getOrNull()
                ?: return@withContext null
            val wantTitle = title.ifBlank { fallbackQuery }
            val firstArtist = norm(artist.substringBefore(","))
            // ISRC is the same recording everywhere: take it when present.
            val byIsrc = if (isrc.isNotBlank()) hits.firstOrNull { it.optString("isrc").equals(isrc, true) } else null
            val candidates = byIsrc?.let { listOf(it) } ?: hits.filter { h ->
                similarity(wantTitle, h.optString("ti")) >= 0.6f &&
                    (firstArtist.isBlank() || norm(h.optString("sti")).contains(firstArtist))
            }.take(3)
            for (c in candidates) {
                val detail = runCatching { songDetail(c.optString("seo")) }.getOrNull() ?: continue
                val dur = detail.optString("duration").toIntOrNull() ?: 0
                if (byIsrc == null && durationSec > 0 && dur > 0 && abs(dur - durationSec) > 8) continue
                val urls = detail.optJSONObject("urls") ?: continue
                val enc = listOf("high", "medium", "auto").firstNotNullOfOrNull { q ->
                    urls.optJSONObject(q)?.optString("message")?.ifBlank { null }
                } ?: continue
                val url = runCatching { decrypt(enc) }.getOrNull()?.takeIf { it.startsWith("http") } ?: continue
                Log.d(TAG, "matched '$wantTitle' → '${detail.optString("track_title")}' (${dur}s${if (byIsrc != null) ", ISRC" else ""})")
                return@withContext Match(url, detail.optString("track_title"), c.optString("sti"), dur)
            }
            Log.d(TAG, "no match for '$wantTitle' / '$artist'")
            null
        }

    private fun post(params: String): JSONObject {
        val conn = URL("$API?$params").openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.connectTimeout = 5_000
            conn.readTimeout = 7_000
            conn.setRequestProperty("User-Agent", UA)
            conn.setFixedLengthStreamingMode(0) // Gaana rejects POSTs without a length
            conn.outputStream.close()
            return JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
        } finally {
            conn.disconnect()
        }
    }

    private fun search(query: String): List<JSONObject> {
        val json = post("country=IN&page=0&secType=track&type=search&keyword=${URLEncoder.encode(query, "UTF-8")}")
        val groups = json.optJSONArray("gr") ?: return emptyList()
        val out = ArrayList<JSONObject>()
        for (g in 0 until groups.length()) {
            val gd = groups.getJSONObject(g).optJSONArray("gd") ?: continue
            for (i in 0 until gd.length()) gd.getJSONObject(i).takeIf { it.optString("ty") == "Track" }?.let(out::add)
        }
        return out
    }

    private fun songDetail(seo: String): JSONObject? =
        post("seokey=${URLEncoder.encode(seo, "UTF-8")}&type=songDetail").optJSONArray("tracks")?.optJSONObject(0)

    /** Message = [offset digit][…][16-char IV at offset][base64 ciphertext]; AES-128-CBC. */
    private fun decrypt(message: String): String {
        val offset = message[0].digitToInt()
        val iv = message.substring(offset, offset + 16).toByteArray()
        var data = message.substring(offset + 16)
        data += "=".repeat((4 - data.length % 4) % 4)
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(KEY.toByteArray(), "AES"), IvParameterSpec(iv))
        return String(cipher.doFinal(Base64.decode(data, Base64.DEFAULT))).trim()
    }
}

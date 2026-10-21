package com.music.spotui.data.recommendation.engine.sources

import com.music.spotui.BuildConfig
import com.music.spotui.data.recommendation.engine.Candidate
import com.music.spotui.data.recommendation.engine.Song
import com.music.spotui.data.recommendation.engine.Source
import com.music.spotui.data.recommendation.engine.SourceResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Last.fm track.getSimilar (scrobble co-listening). Needs a free API key: put
 * `lastfmApiKey=…` in ~/.gradle/gradle.properties and rebuild. Without one the
 * adapter is off and JioSaavn is the third source.
 */
object LastFmSource : NextSongSource {
    override val source = Source.LASTFM
    override val enabled: Boolean get() = BuildConfig.LASTFM_API_KEY.isNotBlank()

    override suspend fun getNextSongs(seed: Song): SourceResult = withContext(Dispatchers.IO) {
        val url = "https://ws.audioscrobbler.com/2.0/?method=track.getsimilar&autocorrect=1&limit=40&format=json" +
            "&api_key=${BuildConfig.LASTFM_API_KEY}" +
            "&artist=${URLEncoder.encode(seed.artists.substringBefore(",").trim(), "UTF-8")}" +
            "&track=${URLEncoder.encode(seed.title, "UTF-8")}"
        val conn = URL(url).openConnection() as HttpURLConnection
        val body = try {
            conn.connectTimeout = 5_000; conn.readTimeout = 6_000
            conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
        val arr = JSONObject(body).optJSONObject("similartracks")?.optJSONArray("track") ?: return@withContext SourceResult(emptyList())
        SourceResult((0 until arr.length()).map { i ->
            val t = arr.getJSONObject(i)
            Candidate(
                title = t.optString("name"),
                artists = t.optJSONObject("artist")?.optString("name").orEmpty(),
                durationSec = t.optInt("duration"),
                lastfmMbid = t.optString("mbid"),
            )
        })
    }
}

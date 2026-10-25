package com.music.aalap.di

import android.content.Context
import android.util.Log
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.SongItem
import java.util.concurrent.ConcurrentHashMap

/**
 * A song's official music video from YouTube, for the player background when Spotify
 * sends no Canvas. Spotify only sends Canvas to accounts that have it switched on in the
 * Spotify app, so this is what makes Aalap's own "Song videos" setting work for everyone.
 *
 * The video goes through the same YouTube pipeline as the audio (the request token,
 * the throttle-parameter solver and a check that the stream really plays), choosing a
 * video-only H.264 stream of 480p or less. It plays muted, in step with the song.
 */
object SongVideo {
    private const val TAG = "SongVideo"
    private val cache = ConcurrentHashMap<String, Found>()

    private class Found(val url: String?, val at: Long)

    private fun words(s: String) = s.lowercase().replace(Regex("""\(.*?\)|\[.*?\]"""), " ").split(Regex("[^\\p{L}\\p{N}]+")).filter { it.length >= 2 }.toSet()

    suspend fun url(context: Context, title: String, artist: String, durationMs: Int): String? {
        val key = "$title|$artist".lowercase()
        // Stream links last a few hours; a miss is remembered for the session.
        cache[key]?.takeIf { it.url == null || System.currentTimeMillis() - it.at < 3 * 3_600_000L }?.let { return it.url }
        val url = runCatching { find(context, title, artist, durationMs) }.onFailure { Log.w(TAG, "find: $it") }.getOrNull()
        cache[key] = Found(url, System.currentTimeMillis())
        return url
    }

    /** The music video for the song: a real music video, about the song's length. */
    private suspend fun videoId(title: String, artist: String, durationMs: Int): String? {
        val firstArtist = artist.substringBefore(",").trim()
        val items = YouTube.search("$title $firstArtist", YouTube.SearchFilter.FILTER_VIDEO).getOrNull()?.items.orEmpty().filterIsInstance<SongItem>()
        val want = words(title)
        val artistWords = words(firstArtist)
        // YouTube marks official music videos (OMV); those first.
        val ranked = items.take(8).sortedByDescending { if (it.musicVideoType?.contains("OMV") == true) 1 else 0 }
        return ranked.firstOrNull { v ->
            val t = words(v.title)
            val a = v.artists.flatMap { words(it.name) }.toSet() + t
            val titleOk = want.isNotEmpty() && want.count { it in t } >= (want.size + 1) / 2
            val artistOk = artistWords.isEmpty() || artistWords.any { it in a }
            // Not a lyric video, a still-image "audio" upload, a cover…
            val notOther = listOf("lyric", "lyrics", "audio", "cover", "karaoke", "live", "reaction", "slowed", "8d", "remix").none { it in t && it !in want }
            // Videos often run a little longer than the song (an intro); not a mix or a short.
            val lengthOk = durationMs <= 0 || v.duration == null || (v.duration!! * 1000 - durationMs) in -20_000..90_000
            titleOk && artistOk && notOther && lengthOk
        }?.id
    }

    private suspend fun find(context: Context, title: String, artist: String, durationMs: Int): String? {
        val id = videoId(title, artist, durationMs) ?: return null
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        val data = com.metrolist.music.utils.YTPlayerUtils.playerResponseForPlayback(
            id, null, com.metrolist.music.constants.AudioQuality.AUTO, cm,
            formatPicker = { r ->
                r.streamingData?.adaptiveFormats.orEmpty()
                    .filter { it.mimeType.startsWith("video/mp4") && "avc1" in it.mimeType && (it.height ?: 0) in 240..480 }
                    .maxByOrNull { it.height ?: 0 }
            },
        ).getOrNull() ?: return null
        Log.d(TAG, "'$title' → $id ${data.format.height}p")
        return data.streamUrl
    }
}

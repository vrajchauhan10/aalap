package com.music.spotui.data.recommendation.engine

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder

/**
 * The queue as a REST endpoint (debug builds only; Aalap has no server, the engine
 * runs on the phone):
 *
 *     GET /queue?seed_song_id=…&user_id=…&length=5
 *
 * Listens on 127.0.0.1:8765 inside the phone. From a computer:
 *
 *     adb forward tcp:8765 tcp:8765
 *     curl "http://localhost:8765/queue?seed_song_id=q:daru%20badnam%20kamal%20kahlon"
 *
 * seed_song_id: a canonical id ("isrc:…" / "key:…"), "spotify:<track id>" or
 * "q:<search text>". user_id defaults to this device's account.
 * Each queue entry lists its sources and every score.
 */
object QueueEndpoint {
    private const val TAG = "QueueEndpoint"
    const val PORT = 8765
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun start(context: Context) {
        val app = context.applicationContext
        scope.launch {
            val server = runCatching { ServerSocket(PORT, 8, InetAddress.getByName("127.0.0.1")) }
                .onFailure { Log.w(TAG, "can't listen on $PORT: $it") }.getOrNull() ?: return@launch
            Log.d(TAG, "GET http://127.0.0.1:$PORT/queue ready")
            while (true) {
                val socket = runCatching { server.accept() }.getOrNull() ?: continue
                scope.launch { socket.use { handle(app, it) } }
            }
        }
    }

    private fun handle(context: Context, socket: Socket) {
        val line = socket.getInputStream().bufferedReader().readLine().orEmpty() // "GET /queue?… HTTP/1.1"
        val target = line.split(" ").getOrNull(1).orEmpty()
        if (target.startsWith("/ytlibrary")) {
            // Debug: force a YouTube Music library sync and report what came in.
            val ok = runBlocking { com.music.spotui.data.api.YouTubeLibrary.sync(context) }
            val songs = com.music.spotui.data.api.YouTubeLibrary.likedSongs(context)
            val body = JSONObject().put("synced", ok).put("liked", songs.size)
                .put("artists", JSONArray(com.music.spotui.data.api.YouTubeLibrary.artists(context).map { it.name }))
                .put("history_artists", JSONObject(com.music.spotui.data.api.YouTubeLibrary.historyArtistCounts(context) as Map<*, *>))
                .put("first_liked", JSONArray(songs.take(8).map { "${it.title} — ${it.singer}${if (it.spotifyTrackId.isNotBlank()) " [matched]" else ""}" }))
            val bytes = body.toString(2).toByteArray()
            socket.getOutputStream().apply {
                write("HTTP/1.1 200 OK\r\nContent-Type: application/json; charset=utf-8\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray())
                write(bytes); flush()
            }
            return
        }
        val (status, body) = if (!line.startsWith("GET ") || target.substringBefore("?") != "/queue") {
            404 to JSONObject().put("error", "use GET /queue?seed_song_id=…&user_id=…&length=5")
        } else runCatching {
            val params = target.substringAfter("?", "").split("&").filter { "=" in it }.associate {
                URLDecoder.decode(it.substringBefore("="), "UTF-8") to URLDecoder.decode(it.substringAfter("="), "UTF-8")
            }
            200 to runBlocking { queue(context, params) }
        }.getOrElse { 500 to JSONObject().put("error", it.toString()) }
        val bytes = body.toString(2).toByteArray()
        socket.getOutputStream().apply {
            write("HTTP/1.1 $status ${if (status == 200) "OK" else "Error"}\r\nContent-Type: application/json; charset=utf-8\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray())
            write(bytes)
            flush()
        }
    }

    private suspend fun queue(context: Context, params: Map<String, String>): JSONObject {
        val started = System.currentTimeMillis()
        val seedParam = params["seed_song_id"]?.trim().orEmpty()
        require(seedParam.isNotBlank()) { "seed_song_id is required" }
        val userId = params["user_id"]?.ifBlank { null } ?: RecoDb.userId(context)
        val length = params["length"]?.toIntOrNull()?.coerceIn(1, 25) ?: QueueEngine.DEFAULT_LENGTH
        QueueEngine.ensureImported(context)
        val db = RecoDb.get(context)
        val seed: Song = when {
            seedParam.startsWith("spotify:") -> db.songBySpotifyId(seedParam.removePrefix("spotify:"))
                ?: com.music.spotui.data.api.Api(context).trackCandidate(seedParam.removePrefix("spotify:"))?.let { db.resolve(it) }
                ?: error("unknown Spotify track ${seedParam.removePrefix("spotify:")}")
            seedParam.startsWith("q:") -> com.music.spotui.data.api.Api(context).searchCandidate(seedParam.removePrefix("q:"))
                ?.let { db.resolve(it) } ?: error("no song found for '${seedParam.removePrefix("q:")}'")
            else -> db.song(seedParam) ?: error("unknown canonical id $seedParam")
        }
        val items = JSONArray()
        QueueEngine.chain(context, userId, seed, length, emptySet()) { item ->
            val s = item.scored
            items.put(JSONObject().apply {
                put("position", items.length() + 1)
                put("canonical_id", s.song.id)
                put("title", s.song.title)
                put("artist", s.song.artists)
                put("via", item.via)
                put("sources", JSONArray(s.sources.map { it.name }))
                put("global_score", s.global); put("taste_score", s.taste)
                put("habit_score", s.habit); put("final_score", s.final)
                put("ids", JSONObject().put("isrc", s.song.info.isrc).put("yt", s.song.info.ytId)
                    .put("spotify", s.song.info.spotifyId).put("jiosaavn", s.song.info.saavnId))
            })
        }
        return JSONObject().apply {
            put("seed", JSONObject().put("canonical_id", seed.id).put("title", seed.title).put("artist", seed.artists))
            put("user_id", userId)
            put("queue", items)
            put("took_ms", System.currentTimeMillis() - started)
        }
    }
}

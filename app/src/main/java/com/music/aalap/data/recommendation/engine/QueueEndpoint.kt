package com.music.aalap.data.recommendation.engine

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.toList
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
        if (target.startsWith("/vaulttest")) {
            // Save the library under a test email, restore it, save again: both copies must match.
            val c = context.applicationContext
            val test = "qa-roundtrip@aalap.test"
            val counts = { listOf("LikedSongs", "aalap_playlists", "ListeningHistory", "LikedSongsFromSpotify")
                .joinToString(", ") { n -> "$n=" + c.getSharedPreferences(n, Context.MODE_PRIVATE).all.size } }
            val before = counts()
            com.music.aalap.data.local.UserVault.save(c, test)
            val dir = java.io.File(c.filesDir, "users")
            val f = dir.listFiles().orEmpty().maxByOrNull { it.lastModified() }!!
            val first = f.readText()
            val restored = com.music.aalap.data.local.UserVault.restore(c, test)
            com.music.aalap.data.local.UserVault.save(c, test)
            val second = f.readText()
            f.delete()
            val bytes = "before: $before\nafter:  ${counts()}\nrestored=$restored identical=${first == second} size=${first.length}".toByteArray()
            socket.getOutputStream().apply {
                write("HTTP/1.1 200 OK\r\nContent-Type: text/plain\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray())
                write(bytes); flush()
            }
            return
        }
        if (target.startsWith("/poster")) {
            // /poster?i=N shows design N on the Aaj ka Aalap poster (-1 = today's), for checking each one.
            val i = target.substringAfter("i=", "-1").toIntOrNull() ?: -1
            android.os.Handler(android.os.Looper.getMainLooper()).post { com.music.aalap.ui.components.PosterDesignOverride.intValue = i }
            val d = com.music.aalap.ui.components.PosterDesigns.getOrNull(i)
            val bytes = (d?.let { "$i ${it.craft} · ${it.origin}" } ?: "today").toByteArray()
            socket.getOutputStream().apply {
                write("HTTP/1.1 200 OK\r\nContent-Type: text/plain; charset=utf-8\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray())
                write(bytes); flush()
            }
            return
        }
        if (target.startsWith("/yttrending")) {
            val out = runBlocking { com.metrolist.innertube.YouTube.trendingSongs().map { l -> l.take(15).map { "${it.title} — ${it.artists}" } }.getOrElse { listOf("error: $it") } }
            val bytes = JSONArray(out).toString(2).toByteArray()
            socket.getOutputStream().apply {
                write("HTTP/1.1 200 OK\r\nContent-Type: application/json; charset=utf-8\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray())
                write(bytes); flush()
            }
            return
        }
        if (target.startsWith("/vibe?ids=")) {
            // Debug: the feel, energy and language the engine sees for canonical ids (comma-separated).
            val ids = URLDecoder.decode(target.substringAfter("ids="), "UTF-8").split(",").filter { it.isNotBlank() }
            QueueEngine.ensureImported(context)
            VibeStore.learnInBackground(context)
            val songs = RecoDb.get(context).songs(ids)
            val vibes = VibeStore.vibes(context, songs.values)
            val out = JSONObject()
            ids.forEach { id ->
                val s = songs[id] ?: return@forEach
                val v = vibes[id] ?: Vibe.UNKNOWN
                out.put(id, JSONObject().put("title", s.title).put("artist", s.artists).put("feel", v.toString())
                    .put("dominant", v.dominant?.label ?: "unknown").put("energy", v.energy).put("language", VibeStore.language(context, s))
                    .put("why", VibeStore.explain(context, s))
                    .put("liked", id in Personalizer.profile(context).liked))
            }
            val bytes = out.toString(2).toByteArray()
            socket.getOutputStream().apply {
                write("HTTP/1.1 200 OK\r\nContent-Type: application/json; charset=utf-8\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray())
                write(bytes); flush()
            }
            return
        }
        if (target.startsWith("/learn")) {
            // Debug: build the mood stations and charts now (labels songs' feels and trending).
            val c = context.applicationContext
            c.getSharedPreferences("vibe_store", Context.MODE_PRIVATE).edit().clear().apply()
            VibeStore.learnInBackground(c)
            val bytes = "{\"started\": true, \"mood_tagged\": ${RecoDb.get(c).tagCount("mood")}, \"chart_tagged\": ${RecoDb.get(c).tagCount("chart")}}".toByteArray()
            socket.getOutputStream().apply {
                write("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray())
                write(bytes); flush()
            }
            return
        }
        if (target.startsWith("/pl?id=")) {
            val id = target.substringAfter("id=")
            val n = runBlocking {
                runCatching { com.music.aalap.data.api.Api(context).getPlaylistSongs(id).toList()
                    .filterIsInstance<com.music.aalap.data.api.Response.Success<List<com.music.aalap.data.entity.SongsModel>>>().lastOrNull()?.data?.size ?: -1 }.getOrDefault(-2)
            }
            val bytes = "{\"songs\": $n}".toByteArray()
            socket.getOutputStream().apply {
                write("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray())
                write(bytes); flush()
            }
            return
        }
        if (target.startsWith("/search?q=")) {
            val q = URLDecoder.decode(target.substringAfter("q="), "UTF-8")
            val out = runBlocking {
                com.metrolist.spotify.Spotify.search(q, listOf("playlist"), limit = 10).getOrNull()?.playlists?.items.orEmpty()
                    .map { "${it.id} | ${it.name} | ${it.owner?.displayName}" }
            }
            val bytes = JSONArray(out).toString(2).toByteArray()
            socket.getOutputStream().apply {
                write("HTTP/1.1 200 OK\r\nContent-Type: application/json; charset=utf-8\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray())
                write(bytes); flush()
            }
            return
        }
        if (target.startsWith("/check")) {
            // Debug: load every chart, mood and Explore category and count what comes back.
            val out = runBlocking { checkCategories(context) }
            val bytes = out.toString(2).toByteArray()
            socket.getOutputStream().apply {
                write("HTTP/1.1 200 OK\r\nContent-Type: application/json; charset=utf-8\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray())
                write(bytes); flush()
            }
            return
        }
        if (target.startsWith("/ytlibrary")) {
            // Debug: force a YouTube Music library sync and report what came in.
            val ok = runBlocking { com.music.aalap.data.api.YouTubeLibrary.sync(context) }
            val songs = com.music.aalap.data.api.YouTubeLibrary.likedSongs(context)
            val body = JSONObject().put("synced", ok).put("liked", songs.size)
                .put("artists", JSONArray(com.music.aalap.data.api.YouTubeLibrary.artists(context).map { it.name }))
                .put("history_artists", JSONObject(com.music.aalap.data.api.YouTubeLibrary.historyArtistCounts(context) as Map<*, *>))
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

    private suspend fun checkCategories(context: Context): JSONObject {
        val api = com.music.aalap.data.api.Api(context)
        suspend fun playlistCount(id: String): Int = runCatching {
            api.getPlaylistSongs(id).toList().filterIsInstance<com.music.aalap.data.api.Response.Success<List<com.music.aalap.data.entity.SongsModel>>>().lastOrNull()?.data?.size ?: -1
        }.getOrDefault(-2)
        val charts = JSONObject()
        com.music.aalap.data.api.CHARTS.forEach { c -> charts.put(c.title, playlistCount(c.id)) }
        val moods = JSONObject()
        com.music.aalap.data.api.Mood.entries.forEach { m ->
            moods.put(m.label, runCatching { com.music.aalap.data.api.Discover.moodMix(context, m).size }.getOrDefault(-2))
        }
        val explore = JSONObject()
        com.music.aalap.ui.screens.EXPLORE.forEach { t ->
            val lists = runCatching {
                api.getCategoryPlaylists(t.query).toList().filterIsInstance<com.music.aalap.data.api.Response.Success<List<com.music.aalap.data.entity.LibraryEntry>>>().lastOrNull()?.data.orEmpty()
            }.getOrDefault(emptyList())
            val firstSongs = lists.firstOrNull()?.let { playlistCount(it.spotifyId) } ?: -1
            explore.put(t.title, "${lists.size} playlists, first has $firstSongs songs" + (lists.firstOrNull()?.let { " (${it.name})" } ?: ""))
        }
        return JSONObject().put("charts", charts).put("moods", moods).put("explore", explore)
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
                ?: com.music.aalap.data.api.Api(context).trackCandidate(seedParam.removePrefix("spotify:"))?.let { db.resolve(it) }
                ?: error("unknown Spotify track ${seedParam.removePrefix("spotify:")}")
            seedParam.startsWith("q:") -> com.music.aalap.data.api.Api(context).searchCandidate(seedParam.removePrefix("q:"))
                ?.let { db.resolve(it) } ?: error("no song found for '${seedParam.removePrefix("q:")}'")
            else -> db.song(seedParam) ?: error("unknown canonical id $seedParam")
        }
        val items = JSONArray()
        // The seed is the song you chose; from=liked acts as if you tapped it inside Liked Songs.
        val seedModel = SongBridge.toSongsModel(seed)
        if (params["from"] == "liked") {
            val liked = com.music.aalap.data.preferences.LikedLibrary.all(context)
            com.music.aalap.data.recommendation.PlayContext.choseFromList(seedModel, liked, "Liked Songs")
        } else com.music.aalap.data.recommendation.PlayContext.chose(seedModel)
        QueueEngine.chain(context, userId, seed, length, setOf(seedModel.id)) { item ->
            val s = item.scored
            items.put(JSONObject().apply {
                put("position", items.length() + 1)
                put("canonical_id", s.song.id)
                put("title", s.song.title)
                put("artist", s.song.artists)
                put("via", item.via)
                put("liked", s.song.id in Personalizer.profile(context, userId).liked)
                put("sources", JSONArray(s.sources.map { it.name }))
                put("global_score", s.global); put("taste_score", s.taste)
                put("habit_score", s.habit); put("final_score", s.final)
                put("session_score", s.session); put("fit_score", s.fit); put("trending", s.trend > 0)
                put("feel", s.vibe.toString()); put("dominant", s.vibe.dominant?.label ?: "unknown"); put("energy", s.vibe.energy)
                put("language", VibeStore.language(context, s.song))
                put("tags", JSONArray(s.tags.toList()))
                put("ids", JSONObject().put("isrc", s.song.info.isrc).put("yt", s.song.info.ytId)
                    .put("spotify", s.song.info.spotifyId).put("jiosaavn", s.song.info.saavnId))
            })
        }
        val sv = VibeStore.vibe(context, seed)
        return JSONObject().apply {
            put("seed", JSONObject().put("canonical_id", seed.id).put("title", seed.title).put("artist", seed.artists)
                .put("feel", sv.toString()).put("dominant", sv.dominant?.label ?: "unknown").put("energy", sv.energy)
                .put("language", VibeStore.language(context, seed)))
            put("user_id", userId)
            put("queue", items)
            put("took_ms", System.currentTimeMillis() - started)
        }
    }
}

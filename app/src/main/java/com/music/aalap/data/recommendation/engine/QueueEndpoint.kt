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
            // A log-out and log-in, without touching Spotify: save the library under a test
            // email, clear it (as log-out does), restore it (as log-in does); every file must
            // come back exactly as it was.
            val c = context.applicationContext
            val test = "qa-roundtrip@aalap.test"
            fun snapshot() = com.music.aalap.data.local.UserVault.FILES.associateWith { n ->
                c.getSharedPreferences(n, Context.MODE_PRIVATE).all.toSortedMap().toString()
            }
            val before = snapshot()
            com.music.aalap.data.local.UserVault.save(c, test)
            com.music.aalap.data.local.UserVault.clear(c)
            val cleared = snapshot().count { it.value != "{}" }
            val restored = com.music.aalap.data.local.UserVault.restore(c, test)
            val after = snapshot()
            java.io.File(c.filesDir, "users").listFiles().orEmpty().filter { it.name.startsWith(
                java.security.MessageDigest.getInstance("SHA-256").digest(test.toByteArray()).joinToString("") { "%02x".format(it) }) }.forEach { it.delete() }
            val diff = before.keys.filter { before[it] != after[it] }
            val bytes = ("files=${before.size} nonEmpty=${before.count { it.value != "{}" }} " +
                "afterLogout=$cleared restored=$restored same=${diff.isEmpty()} differ=$diff\n" +
                before.entries.joinToString("\n") { (k, v) -> "$k: ${c.getSharedPreferences(k, Context.MODE_PRIVATE).all.size} keys" }).toByteArray()
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
        if (target.startsWith("/voiceui")) {
            // Debug: shows the voice card in a state (s=listen&l=0.7, think, found, fail, off).
            val st = target.substringAfter("s=", "off").substringBefore("&")
            val l = target.substringAfter("l=", "0.5").toFloatOrNull() ?: 0.5f
            com.music.aalap.di.VoiceUi.state.value = when (st) {
                "listen" -> com.music.aalap.di.VoiceUi.State.Listening("play kesariya", l)
                "think" -> com.music.aalap.di.VoiceUi.State.Thinking("kesariya")
                "found" -> com.music.aalap.di.VoiceUi.State.Found("Kesariya", "Arijit Singh · Brahmastra", "")
                "fail" -> com.music.aalap.di.VoiceUi.State.Failed("Didn't catch that. Tap the mic and try again.")
                else -> null
            }
            socket.getOutputStream().apply { write("HTTP/1.1 200 OK\r\nContent-Length: 2\r\nConnection: close\r\n\r\nok".toByteArray()); flush() }
            return
        }
        if (target.startsWith("/voicetest?q=")) {
            // Debug: what a spoken request would play, without playing ("|" separates the recogniser's hearings).
            val words = URLDecoder.decode(target.substringAfter("q="), "UTF-8").split("|")
            val out = runBlocking { com.music.aalap.di.VoicePlay.dryRun(context, com.music.aalap.di.VoicePlay.Request(words.first(), alternatives = words.drop(1))) }
            val bytes = out.toByteArray()
            socket.getOutputStream().apply {
                write("HTTP/1.1 200 OK\r\nContent-Type: text/plain; charset=utf-8\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray())
                write(bytes); flush()
            }
            return
        }
        if (target.startsWith("/voicesession?q=")) {
            // Debug: a voice request through the media session, as Google Assistant sends it
            // with the screen off (MediaControllerCompat.transportControls.playFromSearch).
            val q = URLDecoder.decode(target.substringAfter("q="), "UTF-8")
            val token = com.music.aalap.ui.notification.PlaybackService.debugCompatToken
            val msg = if (token == null) "no session yet" else {
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    android.support.v4.media.session.MediaControllerCompat(context, token).transportControls.playFromSearch(q, android.os.Bundle())
                }
                "sent"
            }
            Thread.sleep(12_000)
            val bytes = JSONObject().put("request", q).put("result", msg).put("voice", com.music.aalap.di.VoicePlay.last).toString(2).toByteArray()
            socket.getOutputStream().apply {
                write("HTTP/1.1 200 OK\r\nContent-Type: application/json; charset=utf-8\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray())
                write(bytes); flush()
            }
            return
        }
        if (target.startsWith("/reco5")) {
            // Debug (5.0): /reco5?dial=SURPRISE  /reco5?langs=punjabi:2,english:1 (or langs=auto)
            //   /reco5?feedback=BLOCK_ARTIST&q=…  /reco5?clearfeedback=1  /reco5?audio=1  /reco5?blend=1
            val c = context.applicationContext
            val p = target.substringAfter("?", "").split("&").filter { "=" in it }.associate {
                URLDecoder.decode(it.substringBefore("="), "UTF-8") to URLDecoder.decode(it.substringAfter("="), "UTF-8")
            }
            val out = JSONObject()
            p["dial"]?.let { com.music.aalap.data.preferences.RecoPrefs.setDiscovery(c, com.music.aalap.data.preferences.RecoPrefs.Discovery.valueOf(it)) }
            p["langs"]?.let { v ->
                com.music.aalap.data.preferences.RecoPrefs.setLanguageLevels(c, if (v == "auto") emptyMap() else v.split(",").associate { it.substringBefore(":") to it.substringAfter(":").toInt() })
            }
            if (p["clearfeedback"] != null) Feedback.clearAll(c)
            p["feedback"]?.let { kind ->
                val cand = runBlocking { com.music.aalap.data.api.Api(c).searchCandidate(p["q"].orEmpty()) } ?: error("no song for ${p["q"]}")
                val s = Feedback.record(c, SongBridge.toSongsModel(RecoDb.get(c).resolve(cand)), Feedback.Kind.valueOf(kind))
                out.put("recorded", "$kind ${s.title} — ${s.artists}")
            }
            if (p["audio"] != null) {
                val db = RecoDb.get(c)
                val rows = db.readableDatabase.rawQuery("SELECT a.song_id, s.title, s.artist, a.bpm, a.energy, a.loudness_db FROM song_audio a LEFT JOIN songs s ON s.canonical_id = a.song_id ORDER BY a.at DESC LIMIT 20", null)
                    .use { cur -> JSONArray().apply { while (cur.moveToNext()) put("${cur.getString(1)} — ${cur.getString(2)}: " + "%.0f bpm, energy %.2f, %.1f dB".format(cur.getDouble(3), cur.getDouble(4), cur.getDouble(5))) } }
                out.put("measured", rows).put("count", db.audioCount())
            }
            if (p["blend"] != null) {
                val mine = com.music.aalap.data.recommendation.Blend.myCard(c)
                val code = com.music.aalap.data.recommendation.Blend.encode(mine)
                // A made-up friend who loves Punjabi pop and party songs.
                val friend = com.music.aalap.data.recommendation.Blend.TasteCard("Test friend", listOf("Diljit Dosanjh", "Karan Aujla", "AP Dhillon", "Badshah"),
                    mapOf("PARTY" to 1.0, "ENERGETIC" to 0.9), listOf("Punjabi"),
                    listOf(Triple("Lover", "Diljit Dosanjh", ""), Triple("Softly", "Karan Aujla", ""), Triple("Brown Munde", "AP Dhillon", ""), Triple("Proper Patola", "Diljit Dosanjh, Badshah", ""), Triple("Naah", "Harrdy Sandhu", "")))
                val made = runBlocking { com.music.aalap.data.recommendation.Blend.make(c, friend) }
                val songs = made.songs
                out.put("my_code_chars", code.length).put("my_artists", JSONArray(mine.artists.take(6))).put("my_songs", mine.songs.size)
                    .put("score", made.match.score).put("band", com.music.aalap.data.recommendation.Blend.band(made.match.score).first)
                    .put("shared_artists", JSONArray(made.match.sharedArtists))
                    .put("blend_songs", songs.size).put("blend", JSONArray(songs.map { "${it.title} — ${it.singer.substringBefore(",")}" }))
            }
            p["deletepl"]?.let { name ->
                val gone = com.music.aalap.data.local.AalapPlaylists.all(c).filter { it.name == name }
                gone.forEach { com.music.aalap.data.local.AalapPlaylists.delete(c, it.id) }
                out.put("deleted_playlists", gone.size)
            }
            if (p["blends"] != null) {
                runBlocking { com.music.aalap.data.recommendation.BlendSync.pull(c) }
                val me = com.music.aalap.data.recommendation.Blend.myId(c)
                out.put("me", me).put("sync", com.music.aalap.data.recommendation.BlendSync.status.value)
                out.put("blends", JSONArray(com.music.aalap.data.recommendation.BlendSync.blends.value.map { b ->
                    val pts = b.points(me)
                    JSONObject().put("pair", b.pairId).put("friend", b.friendName(me)).put("synced", b.synced).put("day", b.day)
                        .put("songs", b.songs.size).put("match", b.match(me)).put("score", b.score(me))
                        .put("mine", pts.mine).put("theirs", pts.theirs).put("together", pts.together)
                        .put("history", JSONObject(b.history.mapKeys { it.key.toString() } as Map<*, *>))
                        .put("first", JSONArray(b.songs.take(3).map { "${it.title} — ${it.artists} [${it.by.take(6)}]" }))
                }))
            }
            p["removeblend"]?.let { name ->
                val me = com.music.aalap.data.recommendation.Blend.myId(c)
                com.music.aalap.data.recommendation.BlendSync.blends.value.filter { it.friendName(me) == name }
                    .forEach { com.music.aalap.data.recommendation.BlendSync.remove(c, it.pairId) }
            }
            p["blendheard"]?.toIntOrNull()?.let { i ->
                val b = com.music.aalap.data.recommendation.BlendSync.blends.value.firstOrNull()
                b?.songs?.getOrNull(i)?.let { com.music.aalap.data.recommendation.BlendSync.heard(c, it.toModel()); out.put("heard", it.title) }
            }
            out.put("dial", com.music.aalap.data.preferences.RecoPrefs.discovery(c).name)
                .put("langs", JSONObject(com.music.aalap.data.preferences.RecoPrefs.languageLevels(c) as Map<*, *>))
            val bytes = out.toString(2).toByteArray()
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
        if (target.startsWith("/availability")) {
            // Debug: today's charts and Aaj ka Aalap through the player's own source order;
            // which source each song would play from, and which have none.
            val out = runBlocking {
                val q = java.net.URLDecoder.decode(target.substringAfter("q=", ""), "UTF-8")
                val songs = if (q.isNotBlank()) {
                    // A singer's or a search's songs: several searches, each song once.
                    q.split("|").flatMap { one ->
                        com.music.aalap.data.api.Api(context).searchTracks(one).toList()
                            .filterIsInstance<com.music.aalap.data.api.Response.Success<List<com.music.aalap.data.entity.SongsModel>>>()
                            .lastOrNull()?.data.orEmpty()
                    }.distinctBy { it.id }
                } else (com.music.aalap.data.recommendation.HomeMixes.trendingSongs(context) +
                    com.music.aalap.data.recommendation.HomeMixes.todayCached().map { it.song }).distinctBy { it.id }
                val counts = HashMap<String, Int>(); val missing = JSONArray()
                songs.forEach { s ->
                    val src = com.music.aalap.di.SongPlayer.whereFrom(s, context)
                    counts[src] = (counts[src] ?: 0) + 1
                    if (src == "none") missing.put("${s.title} \u2014 ${s.singer}")
                }
                JSONObject().put("songs", songs.size).put("from", JSONObject(counts as Map<*, *>)).put("not_available", missing)
            }
            val bytes = out.toString(2).toByteArray()
            socket.getOutputStream().apply {
                write("HTTP/1.1 200 OK\r\nContent-Type: application/json; charset=utf-8\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray())
                write(bytes); flush()
            }
            return
        }
        if (target.startsWith("/sourcecheck")) {
            // Debug: for each searched song, what YouTube, JioSaavn and Gaana each find, and how fast.
            val q = java.net.URLDecoder.decode(target.substringAfter("q=", ""), "UTF-8")
            val out = runBlocking {
                val songs = com.music.aalap.data.api.Api(context).searchTracks(q).toList()
                    .filterIsInstance<com.music.aalap.data.api.Response.Success<List<com.music.aalap.data.entity.SongsModel>>>().lastOrNull()?.data.orEmpty().take(1)
                JSONArray(songs.map { s -> JSONObject(com.music.aalap.di.SongPlayer.auditSources(s, context) as Map<*, *>).put("song", "${s.title} \u2014 ${s.singer}") })
            }
            val bytes = out.toString(2).toByteArray()
            socket.getOutputStream().apply {
                write("HTTP/1.1 200 OK\r\nContent-Type: application/json; charset=utf-8\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray())
                write(bytes); flush()
            }
            return
        }
        if (target.startsWith("/failtest")) {
            // Debug: a song nothing can find, then a real one. Reports how long until the real
            // one is playing (the failed song should be skipped, never played as another song).
            val state = dagger.hilt.android.EntryPointAccessors.fromApplication(context.applicationContext,
                com.music.aalap.di.HeyAalapService.StateEntryPoint::class.java).currentSongState()
            val real = (com.music.aalap.data.recommendation.HomeMixes.todayCached().map { it.song } + com.music.aalap.data.recommendation.HomeMixes.yourMixCached(context)).first()
            val fake = com.music.aalap.data.entity.SongsModel(-424242, "Zzqxv Plonk Nonexistent", "Nowhere", "Qwertyuiop Nobody", "", "Zzqxv Plonk Nonexistent Qwertyuiop Nobody")
            val started = System.currentTimeMillis()
            android.os.Handler(android.os.Looper.getMainLooper()).post { com.music.aalap.di.SmartPlay.playAll(context, state, listOf(fake, real), "Fail test") }
            var playingReal = -1L
            while (System.currentTimeMillis() - started < 90_000) {
                Thread.sleep(500)
                val playing = java.util.concurrent.CompletableFuture<Boolean>().also { f -> android.os.Handler(android.os.Looper.getMainLooper()).post { f.complete(com.music.aalap.di.SongPlayer.isPlaying()) } }.get()
                if (state.songId.value == real.id && playing) { playingReal = System.currentTimeMillis() - started; break }
            }
            val bytes = JSONObject().put("real", real.title).put("seconds_until_real_plays", if (playingReal < 0) "never (90 s)" else playingReal / 1000.0)
                .put("now", state.title.value).toString(2).toByteArray()
            socket.getOutputStream().apply {
                write("HTTP/1.1 200 OK\r\nContent-Type: application/json; charset=utf-8\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray())
                write(bytes); flush()
            }
            return
        }
        if (target.startsWith("/matchaudit")) {
            // Debug: what YouTube recording today's mixes would play, song by song (nothing plays).
            val q = target.substringAfter("q=", "").substringBefore("&").let { java.net.URLDecoder.decode(it, "UTF-8") }
            val out = runBlocking {
                val songs = if (q.isNotBlank()) {
                    com.music.aalap.data.api.Api(context).searchTracks(q).toList()
                        .filterIsInstance<com.music.aalap.data.api.Response.Success<List<com.music.aalap.data.entity.SongsModel>>>()
                        .lastOrNull()?.data.orEmpty().take(6)
                } else (com.music.aalap.data.recommendation.HomeMixes.todayCached().map { it.song } +
                    com.music.aalap.data.recommendation.HomeMixes.yourMixCached(context)).distinctBy { it.id }
                JSONArray(songs.map { s ->
                    val m = com.music.aalap.di.SongPlayer.auditMatch(s)
                    JSONObject().put("shown", "${s.title} \u2014 ${s.singer}").put("plays", m ?: "NO MATCH").put("sec", s.durationMs / 1000)
                })
            }
            val bytes = out.toString(2).toByteArray()
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
                put("known", s.song.id in Personalizer.profile(context, userId).heard)
                put("bpm", s.vibe.bpm ?: JSONObject.NULL)
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

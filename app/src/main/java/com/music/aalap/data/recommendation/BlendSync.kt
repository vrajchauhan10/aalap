package com.music.aalap.data.recommendation

import android.content.Context
import android.util.Log
import com.music.aalap.BuildConfig
import com.music.aalap.data.entity.SongsModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Blends shared between two phones. One record per pair of people holds both taste
 * codes, today's 30 songs, who heard which of them, and the score each day.
 *
 * With Firebase set up (app/google-services.json, free plan) the record lives in
 * Firestore and both phones see the same thing: the same 30 songs (whichever phone
 * opens Aalap first each day makes them), each other's listening, and the same score.
 * It's read when Aalap opens and every minute while a Blend is on screen. A friend who made a Blend with your code finds it in their Library, and you in
 * yours, without sending anything back. Without Firebase (or for an old code) the Blend
 * works on this phone only.
 *
 * Firestore: blends/{uidA_uidB} = { members, names, tastes, day, songs, listened, history }.
 * Only the two members can read or change it (see the security rules in the changelog).
 */
object BlendSync {
    private const val TAG = "BlendSync"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    data class Song(
        val title: String, val artists: String, val album: String, val cover: String,
        val spotifyId: String, val durMs: Int,
        /** Who it's from: a member's id, or "new" (new to you both). */
        val by: String,
    ) {
        val key: String get() = BlendScore.key(title, artists, spotifyId)
        /** As the app's song (the same id and play query the rest of the app uses). */
        fun toModel(): SongsModel = SongsModel(
            id = if (spotifyId.isNotBlank()) ("track:$spotifyId".hashCode() and 0x7fffffff) else ("blend:$title|$artists".hashCode() and 0x7fffffff),
            title = title, album = album, singer = artists, coverUri = cover,
            url = com.music.aalap.di.SongPlayer.buildSpotifyPlayQuery(spotifyId, title, artists),
            spotifyTrackId = spotifyId, durationMs = durMs,
        )
    }

    data class Shared(
        val pairId: String,
        val members: List<String>,
        val names: Map<String, String>,
        /** Each member's latest taste code. */
        val tastes: Map<String, String>,
        /** The day ([java.time.LocalDate.toEpochDay]) of [songs]. */
        val day: Long,
        val songs: List<Song>,
        /** day → member → song keys heard. */
        val listened: Map<Long, Map<String, Set<String>>>,
        /** day → score that day. */
        val history: Map<Long, Int>,
        val synced: Boolean,
        /** This phone's copy in your playlists (local only). */
        val playlistId: String = "",
        /** Firestore's change stamp when read (to make today's songs only once). */
        val updateTime: String = "",
    ) {
        fun friend(me: String) = members.firstOrNull { it != me } ?: members.last()
        fun friendName(me: String) = names[friend(me)] ?: "Friend"

        /** Taste match from the two codes (the same on both phones). */
        fun match(me: String): Int {
            val mine = tastes[me]?.let { Blend.decode(it) } ?: return 50
            val theirs = tastes[friend(me)]?.let { Blend.decode(it) } ?: return 50
            return Blend.match(mine, theirs).score
        }

        fun days(me: String): Map<Long, BlendScore.Day> = listened.mapValues { (_, m) ->
            BlendScore.Day(m[me].orEmpty(), m[friend(me)].orEmpty())
        }

        fun points(me: String, today: Long = today()) = BlendScore.points(days(me), today)
        fun score(me: String, today: Long = today()) = BlendScore.score(match(me), points(me, today).total)
    }

    fun today(): Long = java.time.LocalDate.now().toEpochDay()

    val enabled: Boolean get() = BuildConfig.FIREBASE_PROJECT.isNotBlank()

    /** The Firebase sign-in id once signed in (anonymous: no account, nothing to type). */
    @Volatile var uid: String? = null
        private set

    private val _blends = MutableStateFlow<List<Shared>>(emptyList())
    val blends: StateFlow<List<Shared>> = _blends

    /** "live", "local" (no Firebase), "offline" or an error, for the Blend screens. */
    val status = MutableStateFlow(if (BuildConfig.FIREBASE_PROJECT.isNotBlank()) "connecting" else "local")

    @Volatile private var rest: FirestoreRest? = null
    @Volatile private var started = false

    // ── start ───────────────────────────────────────────────────────────────

    /** Loads the Blends kept on the phone, signs in and reads the shared ones (each time Aalap opens). */
    fun start(context: Context) {
        val app = context.applicationContext
        if (!started) { started = true; _blends.value = loadLocal(app); dropOldBlendPlaylists(app) }
        scope.launch {
            if (enabled) pull(app)
            refreshAll(app)
        }
    }

    /** Reads the shared Blends now (Aalap opening, a Blend screen open). */
    suspend fun pull(context: Context) {
        if (!enabled) return
        val app = context.applicationContext
        withContext(Dispatchers.IO) {
            runCatching {
                val r = rest ?: FirestoreRest(app).also { rest = it }
                val me = r.signIn()
                uid = me
                val docs = r.whereArrayContains("blends", "members", me)
                mergeRemote(app, docs.map { fromMap(it.id, it.fields).copy(updateTime = it.updateTime) })
                status.value = "live"
            }.onFailure { Log.w(TAG, "pull: $it"); status.value = if (status.value == "live") "live" else "offline" }
        }
    }

    @Synchronized
    private fun mergeRemote(app: Context, remote: List<Shared>) {
        val hidden = hidden(app)
        val local = _blends.value.associateBy { it.pairId }
        val merged = remote.filter { it.pairId !in hidden }.map { r -> r.copy(playlistId = local[r.pairId]?.playlistId.orEmpty()) } +
            _blends.value.filter { !it.synced }
        setAll(app, merged.map { mirror(app, it) })
    }

    // ── making and refreshing ───────────────────────────────────────────────

    /** Makes a shared Blend from [made] (or updates the one with this friend); returns its id. */
    suspend fun create(context: Context, made: Blend.Made): String {
        val app = context.applicationContext
        val me = Blend.myId(app)
        val friend = made.friend
        val shared = enabled && uid != null && friend.id.length >= 20 && friend.id != uid
        val friendId = friend.id.ifBlank { "local-" + friend.name.lowercase() }
        val pairId = listOf(me, friendId).sorted().joinToString("_")
        val today = today()
        val myCard = Blend.myCard(app)
        val songs = songsFrom(made, me, friendId)
        var rec = Shared(
            pairId, listOf(me, friendId).sorted(), mapOf(me to myCard.name, friendId to friend.name),
            mapOf(me to Blend.encode(myCard), friendId to Blend.encode(friend)),
            today, songs, emptyMap(), emptyMap(), shared,
        )
        val existing = _blends.value.firstOrNull { it.pairId == pairId }
        if (existing != null && existing.day == today) rec = rec.copy(songs = existing.songs, listened = existing.listened, history = existing.history)
        else if (existing != null) rec = rec.copy(listened = existing.listened, history = existing.history)
        rec = rec.copy(history = rec.history + (today to rec.score(me, today)))
        if (shared) {
            val r = rest ?: error("not connected")
            if (!r.create("blends", pairId, toMap(rec))) {
                // Already there (your friend made it too): newer tastes; today's songs if it has none yet.
                val doc = r.get("blends", pairId)
                val upd = hashMapOf<String, Any?>("tastes.$me" to rec.tastes[me], "tastes.$friendId" to rec.tastes[friendId],
                    "names.$me" to myCard.name, "names.$friendId" to friend.name)
                if (((doc?.fields?.get("day") as? Long) ?: 0L) < today) { upd["day"] = today; upd["songs"] = rec.songs.map(::songMap) }
                r.update("blends", pairId, upd)
                doc?.let { d -> rec = fromMap(pairId, d.fields).let { old -> rec.copy(listened = old.listened, history = old.history + rec.history,
                    songs = if (old.day >= today) old.songs else rec.songs) } }
            }
        }
        unhide(app, pairId)
        put(app, rec)
        return pairId
    }

    private fun songsFrom(made: Blend.Made, me: String, friendId: String): List<Song> = made.songs.mapIndexed { i, s ->
        Song(s.title, s.singer, s.album, s.coverUri, s.spotifyTrackId, s.durationMs,
            when (made.by.getOrNull(i)) { "me" -> me; "them" -> friendId; else -> "new" })
    }

    private val refreshing = java.util.concurrent.atomic.AtomicBoolean(false)

    /**
     * Once a day for each Blend: a fresh 30 from both tastes as they are today (made by
     * the first phone to open Aalap; the other one just receives it), your latest taste
     * code, and listening older than two weeks dropped.
     */
    suspend fun refreshAll(context: Context) {
        if (!refreshing.compareAndSet(false, true)) return
        try {
            val app = context.applicationContext
            val me = Blend.myId(app)
            val today = today()
            // Right after Aalap opens the connection may not be known yet: wait a little.
            repeat(15) { if (!com.music.aalap.data.preferences.OfflineState.isOffline) return@repeat; delay(1_000) }
            if (com.music.aalap.data.preferences.OfflineState.isOffline) { Log.d(TAG, "refresh: offline, later"); return }
            Log.d(TAG, "refresh: ${_blends.value.size} blends, today $today, days ${_blends.value.map { it.day }}")
            val myCode = runCatching { Blend.encode(Blend.myCard(app)) }.getOrNull()
            for (rec in _blends.value) {
                runCatching {
                    var r = rec
                    if (myCode != null && r.tastes[me] != myCode && myTasteDay(app, r.pairId) < today) {
                        r = r.copy(tastes = r.tastes + (me to myCode))
                        if (r.synced) rest?.update("blends", r.pairId, mapOf("tastes.$me" to myCode))
                        setMyTasteDay(app, r.pairId, today)
                    }
                    if (r.day < today) {
                        val friendCard = r.tastes[r.friend(me)]?.let { Blend.decode(it) } ?: run { Log.w(TAG, "refresh: no taste for friend"); return@runCatching }
                        val made = Blend.make(app, friendCard.copy(id = r.friend(me)))
                        if (made.songs.size < 10) return@runCatching
                        val songs = songsFrom(made, me, r.friend(me))
                        val old = r.listened.filterKeys { it >= today - 14 }
                        r = r.copy(day = today, songs = songs, listened = old)
                        r = r.copy(history = r.history.filterKeys { it >= today - 60 } + (today to r.score(me, today)))
                        if (r.synced) {
                            val api = rest ?: return@runCatching
                            // Only if nobody changed it since we read it: the first phone makes today's songs.
                            val upd = hashMapOf<String, Any?>("day" to today, "songs" to songs.map(::songMap), "history.$today" to r.history[today])
                            val stale = rec.listened.keys.filter { it < today - 14 }.map { "listened.$it" }
                            if (!api.update("blends", r.pairId, upd, delete = stale, ifUpdateTime = rec.updateTime.ifBlank { null })) {
                                pull(app) // the other phone made today's songs: take theirs
                                return@runCatching
                            }
                        }
                    }
                    if (r != rec) put(app, r)
                }.onFailure { Log.w(TAG, "refresh ${rec.pairId}: $it") }
            }
        } finally { refreshing.set(false) }
    }

    // ── listening ───────────────────────────────────────────────────────────

    private var hearing: Job? = null

    /**
     * A song started: if it's in a Blend's songs today, once it has played for 30 s
     * (paused time doesn't count) it's marked as heard by you, on both phones.
     */
    fun onSongStarted(context: Context, song: SongsModel) {
        hearing?.cancel()
        val key = BlendScore.key(song.title, song.singer, song.spotifyTrackId)
        val titleKey = BlendScore.key(song.title, song.singer, "")
        if (_blends.value.none { b -> b.songs.any { it.key == key || BlendScore.key(it.title, it.artists, "") == titleKey } }) return
        val app = context.applicationContext
        hearing = scope.launch {
            var played = 0
            while (played < 30) {
                delay(1_000)
                if (withContext(Dispatchers.Main) { com.music.aalap.di.SongPlayer.isPlaying() }) played++
            }
            heard(app, song)
        }
    }

    fun heard(context: Context, song: SongsModel) {
        val me = Blend.myId(context)
        val today = today()
        val titleKey = BlendScore.key(song.title, song.singer, "")
        for (rec in _blends.value) {
            val s = rec.songs.firstOrNull { it.key == BlendScore.key(song.title, song.singer, song.spotifyTrackId) || BlendScore.key(it.title, it.artists, "") == titleKey } ?: continue
            if (s.key in rec.listened[today]?.get(me).orEmpty()) continue
            val day = rec.listened[today].orEmpty()
            var r = rec.copy(listened = rec.listened + (today to (day + (me to (day[me].orEmpty() + s.key)))))
            r = r.copy(history = r.history + (today to r.score(me, today)))
            put(context, r)
            if (r.synced) scope.launch {
                runCatching {
                    rest?.update("blends", r.pairId, fields = mapOf("history.$today" to r.history[today]),
                        arrayUnion = mapOf("listened.$today.$me" to listOf(s.key)))
                }.onFailure { Log.w(TAG, "heard: $it") }
            }
            Log.d(TAG, "heard '${s.title}' in ${r.pairId}: score ${r.history[today]}")
        }
    }

    // ── removing ────────────────────────────────────────────────────────────

    /** Takes the Blend off this phone (your friend keeps theirs). */
    fun remove(context: Context, pairId: String) {
        val rec = _blends.value.firstOrNull { it.pairId == pairId } ?: return
        if (rec.playlistId.isNotBlank()) com.music.aalap.data.local.AalapPlaylists.delete(context, rec.playlistId)
        context.getSharedPreferences("blend_sync", Context.MODE_PRIVATE).edit().putStringSet("hidden", hidden(context) + pairId).apply()
        setAll(context, _blends.value.filterNot { it.pairId == pairId })
    }

    private fun hidden(c: Context) = c.getSharedPreferences("blend_sync", Context.MODE_PRIVATE).getStringSet("hidden", emptySet()).orEmpty()
    private fun unhide(c: Context, id: String) = c.getSharedPreferences("blend_sync", Context.MODE_PRIVATE).edit().putStringSet("hidden", hidden(c) - id).apply()
    private fun myTasteDay(c: Context, id: String) = c.getSharedPreferences("blend_sync", Context.MODE_PRIVATE).getLong("taste.$id", 0)
    private fun setMyTasteDay(c: Context, id: String, d: Long) = c.getSharedPreferences("blend_sync", Context.MODE_PRIVATE).edit().putLong("taste.$id", d).apply()

    // ── the phone's copy ────────────────────────────────────────────────────

    @Synchronized
    private fun put(c: Context, rec: Shared) {
        setAll(c, _blends.value.filterNot { it.pairId == rec.pairId } + mirror(c, rec))
    }

    @Synchronized
    private fun setAll(c: Context, list: List<Shared>) {
        _blends.value = list
        val a = JSONArray()
        list.forEach { a.put(JSONObject(toMap(it) + mapOf("synced" to it.synced, "playlistId" to it.playlistId, "pairId" to it.pairId))) }
        c.getSharedPreferences("blend_sync", Context.MODE_PRIVATE).edit().putString("list", a.toString()).apply()
    }

    /** Playlists are only the ones you make: the "Blend · You + …" copies earlier versions made go (once). */
    private fun dropOldBlendPlaylists(c: Context) {
        val p = c.getSharedPreferences("blend_sync", Context.MODE_PRIVATE)
        if (p.getBoolean("dropped_old_playlists", false)) return
        com.music.aalap.data.local.AalapPlaylists.all(c).filter { it.name.startsWith("Blend · You + ") }
            .forEach { com.music.aalap.data.local.AalapPlaylists.delete(c, it.id) }
        p.edit().putBoolean("dropped_old_playlists", true).apply()
    }

    /**
     * A Blend plays from Library → Blends, so it isn't copied into your playlists too;
     * a copy made by an earlier version is removed.
     */
    private fun mirror(c: Context, rec: Shared): Shared {
        if (rec.playlistId.isBlank()) return rec
        com.music.aalap.data.local.AalapPlaylists.delete(c, rec.playlistId)
        return rec.copy(playlistId = "")
    }

    private fun loadLocal(c: Context): List<Shared> = runCatching {
        val a = JSONArray(c.getSharedPreferences("blend_sync", Context.MODE_PRIVATE).getString("list", "[]"))
        (0 until a.length()).mapNotNull { i ->
            val o = a.getJSONObject(i)
            runCatching { fromMap(o.getString("pairId"), jsonToMap(o)).copy(synced = o.optBoolean("synced"), playlistId = o.optString("playlistId")) }.getOrNull()
        }
    }.getOrDefault(emptyList())

    // ── record ↔ map (Firestore document, and JSON on the phone) ────────────

    private fun songMap(s: Song) = mapOf("t" to s.title, "a" to s.artists, "al" to s.album, "c" to s.cover, "sid" to s.spotifyId, "d" to s.durMs.toLong(), "by" to s.by)

    fun toMap(r: Shared): Map<String, Any> = mapOf(
        "members" to r.members, "names" to r.names, "tastes" to r.tastes, "day" to r.day,
        "songs" to r.songs.map(::songMap),
        "listened" to r.listened.map { (d, m) -> d.toString() to m.mapValues { it.value.toList() } }.toMap(),
        "history" to r.history.map { (d, s) -> d.toString() to s.toLong() }.toMap(),
    )

    @Suppress("UNCHECKED_CAST")
    fun fromMap(id: String, m: Map<String, Any?>): Shared {
        fun str(x: Any?) = x?.toString().orEmpty()
        fun num(x: Any?) = (x as? Number)?.toLong() ?: x?.toString()?.toLongOrNull() ?: 0L
        val members = (m["members"] as List<*>).map(::str)
        val songs = (m["songs"] as? List<*>).orEmpty().mapNotNull { it as? Map<String, Any?> }.map {
            Song(str(it["t"]), str(it["a"]), str(it["al"]), str(it["c"]), str(it["sid"]), num(it["d"]).toInt(), str(it["by"]))
        }
        val listened = (m["listened"] as? Map<String, Any?>).orEmpty().mapNotNull { (d, v) ->
            val day = d.toLongOrNull() ?: return@mapNotNull null
            day to (v as? Map<String, Any?>).orEmpty().mapValues { (_, keys) -> (keys as? List<*>).orEmpty().map(::str).toSet() }
        }.toMap()
        val history = (m["history"] as? Map<String, Any?>).orEmpty().mapNotNull { (d, v) -> d.toLongOrNull()?.let { it to num(v).toInt() } }.toMap()
        return Shared(
            pairId = id, members = members,
            names = (m["names"] as? Map<String, Any?>).orEmpty().mapValues { str(it.value) },
            tastes = (m["tastes"] as? Map<String, Any?>).orEmpty().mapValues { str(it.value) },
            day = num(m["day"]), songs = songs, listened = listened, history = history, synced = true,
        )
    }

    private fun jsonToMap(o: JSONObject): Map<String, Any?> = o.keys().asSequence().associateWith { k -> unwrap(o.get(k)) }

    private fun unwrap(v: Any?): Any? = when (v) {
        is JSONObject -> jsonToMap(v)
        is JSONArray -> (0 until v.length()).map { unwrap(v.get(it)) }
        JSONObject.NULL -> null
        else -> v
    }
}

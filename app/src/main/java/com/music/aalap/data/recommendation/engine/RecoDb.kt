package com.music.aalap.data.recommendation.engine

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONArray

/**
 * The recommendation database (on the device, SQLite). Schema, also in
 * docs/recommendation/schema.sql:
 *
 *  - songs:        one row per canonical song with every service's id
 *  - source_cache: each source's ranked "next" list per seed, kept 24 hours
 *  - transitions:  how often this user played song B within 30 min after song A
 *  - user_taste:   snapshot of the learned taste (artist weights) and liked songs
 *  - play_history: every play, with whether it was skipped
 *  - song_tags:    what a song is known to be: a feel from a mood playlist it's on
 *                  (src "mood"), or on today's charts (tag TREND, src "chart")
 *  - song_audio:   tempo and energy measured from the sound while the song played
 *  - feedback:     "more / less like this", "don't play this song / artist"
 *
 * All access goes through this class; every method is safe off the main thread.
 */
class RecoDb private constructor(context: Context) :
    SQLiteOpenHelper(context.applicationContext, NAME, null, VERSION) {

    companion object {
        private const val NAME = "aalap_reco.db"
        private const val VERSION = 3

        @Volatile private var instance: RecoDb? = null
        fun get(context: Context): RecoDb =
            instance ?: synchronized(this) { instance ?: RecoDb(context).also { instance = it } }

        /** The Aalap account on this device (the app has one user per install). */
        fun userId(context: Context): String =
            com.music.aalap.data.preferences.getAalapEmail(context).trim().lowercase().ifBlank { "local" }

        val SCHEMA = listOf(
            """CREATE TABLE IF NOT EXISTS songs(
                canonical_id TEXT PRIMARY KEY,
                isrc TEXT, title TEXT NOT NULL, artist TEXT NOT NULL,
                title_key TEXT NOT NULL, artist_key TEXT NOT NULL,
                yt_id TEXT, spotify_id TEXT, saavn_id TEXT, lastfm_mbid TEXT,
                language TEXT, album TEXT, cover TEXT,
                duration_sec INTEGER NOT NULL DEFAULT 0, explicit INTEGER NOT NULL DEFAULT 0,
                updated_at INTEGER NOT NULL DEFAULT 0)""",
            "CREATE INDEX IF NOT EXISTS songs_isrc ON songs(isrc)",
            "CREATE INDEX IF NOT EXISTS songs_yt ON songs(yt_id)",
            "CREATE INDEX IF NOT EXISTS songs_spotify ON songs(spotify_id)",
            "CREATE INDEX IF NOT EXISTS songs_saavn ON songs(saavn_id)",
            "CREATE INDEX IF NOT EXISTS songs_title_key ON songs(title_key)",
            "CREATE INDEX IF NOT EXISTS songs_artist_key ON songs(artist_key)",
            """CREATE TABLE IF NOT EXISTS source_cache(
                seed_id TEXT NOT NULL, source TEXT NOT NULL,
                ranked_ids TEXT NOT NULL, fetched_at INTEGER NOT NULL,
                PRIMARY KEY(seed_id, source))""",
            """CREATE TABLE IF NOT EXISTS transitions(
                user_id TEXT NOT NULL, from_id TEXT NOT NULL, to_id TEXT NOT NULL,
                count INTEGER NOT NULL DEFAULT 0, last_at INTEGER NOT NULL DEFAULT 0,
                PRIMARY KEY(user_id, from_id, to_id))""",
            """CREATE TABLE IF NOT EXISTS user_taste(
                user_id TEXT PRIMARY KEY,
                artist_weights TEXT NOT NULL DEFAULT '{}',
                liked_ids TEXT NOT NULL DEFAULT '[]',
                updated_at INTEGER NOT NULL DEFAULT 0)""",
            """CREATE TABLE IF NOT EXISTS play_history(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                user_id TEXT NOT NULL, song_id TEXT NOT NULL,
                played_at INTEGER NOT NULL, skipped INTEGER NOT NULL DEFAULT 0,
                listened_ms INTEGER NOT NULL DEFAULT 0)""",
            "CREATE INDEX IF NOT EXISTS play_user_time ON play_history(user_id, played_at)",
            """CREATE TABLE IF NOT EXISTS song_tags(
                song_id TEXT NOT NULL, tag TEXT NOT NULL, src TEXT NOT NULL,
                weight REAL NOT NULL DEFAULT 1, at INTEGER NOT NULL DEFAULT 0,
                PRIMARY KEY(song_id, tag, src))""",
            "CREATE INDEX IF NOT EXISTS song_tags_tag ON song_tags(tag, at)",
            """CREATE TABLE IF NOT EXISTS song_audio(
                song_id TEXT PRIMARY KEY, bpm REAL NOT NULL, energy REAL NOT NULL,
                loudness_db REAL NOT NULL DEFAULT 0, beat REAL NOT NULL DEFAULT 0, at INTEGER NOT NULL DEFAULT 0)""",
            """CREATE TABLE IF NOT EXISTS feedback(
                user_id TEXT NOT NULL, kind TEXT NOT NULL, target TEXT NOT NULL, at INTEGER NOT NULL,
                PRIMARY KEY(user_id, kind, target))""",
        )
    }

    override fun onCreate(db: SQLiteDatabase) = SCHEMA.forEach(db::execSQL)
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = SCHEMA.forEach(db::execSQL)

    // ── songs ───────────────────────────────────────────────────────────────

    private fun Cursor.str(col: String) = getString(getColumnIndexOrThrow(col)).orEmpty()
    private fun Cursor.int(col: String) = getInt(getColumnIndexOrThrow(col))

    private fun Cursor.toSong() = Song(
        str("canonical_id"),
        Candidate(
            title = str("title"), artists = str("artist"), durationSec = int("duration_sec"),
            album = str("album"), cover = str("cover"), isrc = str("isrc"), ytId = str("yt_id"),
            spotifyId = str("spotify_id"), saavnId = str("saavn_id"), lastfmMbid = str("lastfm_mbid"),
            language = str("language"), explicit = int("explicit") == 1,
        ),
    )

    private fun query(sql: String, vararg args: String): List<Song> =
        readableDatabase.rawQuery(sql, args).use { c -> buildList { while (c.moveToNext()) add(c.toSong()) } }

    fun song(id: String): Song? = query("SELECT * FROM songs WHERE canonical_id = ?", id).firstOrNull()

    fun songs(ids: Collection<String>): Map<String, Song> {
        if (ids.isEmpty()) return emptyMap()
        val out = HashMap<String, Song>()
        ids.chunked(400).forEach { chunk ->
            val marks = chunk.joinToString(",") { "?" }
            query("SELECT * FROM songs WHERE canonical_id IN ($marks)", *chunk.toTypedArray()).forEach { out[it.id] = it }
        }
        return out
    }

    fun songBySpotifyId(spotifyId: String): Song? =
        if (spotifyId.isBlank()) null else query("SELECT * FROM songs WHERE spotify_id = ? LIMIT 1", spotifyId).firstOrNull()

    /**
     * RESOLVE: the canonical song for [c]. Looks up by ISRC, then each service id, then
     * a fuzzy title + artist match; merges any new ids into the row it finds, or adds a
     * new row (id "isrc:…" when there is an ISRC, else "key:title|artist").
     */
    @Synchronized
    fun resolve(c: Candidate): Song {
        val found = findExisting(c)
        if (found != null) {
            val merged = merge(found.info, c)
            if (merged != found.info) write(found.id, merged)
            return Song(found.id, merged)
        }
        val id = if (c.isrc.isNotBlank()) "isrc:${c.isrc.uppercase()}" else SongMatcher.canonicalKey(c)
        val existing = song(id)
        val info = if (existing != null) merge(existing.info, c) else c
        write(id, info)
        return Song(id, info)
    }

    /** Adds ids/details a source learned about song [id] (e.g. its YouTube video). */
    @Synchronized
    fun enrich(id: String, c: Candidate): Song? {
        val row = song(id) ?: return null
        val merged = merge(row.info, c)
        if (merged != row.info) write(id, merged)
        return Song(id, merged)
    }

    /** Resolves many at once in one transaction. */
    // Lock order everywhere: this object first, then the SQLite transaction (else two threads deadlock).
    @Synchronized
    fun resolveAll(list: List<Candidate>): List<Song> {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val out = list.map { resolve(it) }
            db.setTransactionSuccessful()
            return out
        } finally {
            db.endTransaction()
        }
    }

    private fun findExisting(c: Candidate): Song? {
        if (c.isrc.isNotBlank()) query("SELECT * FROM songs WHERE isrc = ? LIMIT 1", c.isrc.uppercase()).firstOrNull()?.let { return it }
        if (c.spotifyId.isNotBlank()) query("SELECT * FROM songs WHERE spotify_id = ? LIMIT 1", c.spotifyId).firstOrNull()?.let { return it }
        if (c.ytId.isNotBlank()) query("SELECT * FROM songs WHERE yt_id = ? LIMIT 1", c.ytId).firstOrNull()?.let { return it }
        if (c.saavnId.isNotBlank()) query("SELECT * FROM songs WHERE saavn_id = ? LIMIT 1", c.saavnId).firstOrNull()?.let { return it }
        val titleKey = SongMatcher.titleKey(c.title, c.artists)
        if (titleKey.isBlank()) return null
        // Exact title first (any artist spelling), then same main artist with a close title.
        query("SELECT * FROM songs WHERE title_key = ? LIMIT 20", titleKey)
            .firstOrNull { sameUnlessConflicting(it.info, c) }?.let { return it }
        val artistKey = SongMatcher.artistKeys(c.artists).firstOrNull() ?: return null
        return query("SELECT * FROM songs WHERE artist_key = ? LIMIT 200", artistKey)
            .firstOrNull { sameUnlessConflicting(it.info, c) }
    }

    /** Same song, unless both sides carry different ids of the same service (different recordings). */
    private fun sameUnlessConflicting(a: Candidate, b: Candidate): Boolean {
        fun clash(x: String, y: String) = x.isNotBlank() && y.isNotBlank() && !x.equals(y, ignoreCase = true)
        if (clash(a.isrc, b.isrc) || clash(a.spotifyId, b.spotifyId)) return false
        return SongMatcher.sameSong(a, b)
    }

    private fun merge(a: Candidate, b: Candidate) = a.copy(
        title = a.title.ifBlank { b.title },
        artists = a.artists.ifBlank { b.artists },
        durationSec = if (a.durationSec > 0) a.durationSec else b.durationSec,
        album = a.album.ifBlank { b.album },
        // Square album art beats a 16:9 video thumbnail.
        cover = if (a.cover.isBlank() || (a.ytId.isNotBlank() && a.cover.contains("ytimg") && b.cover.isNotBlank() && !b.cover.contains("ytimg"))) b.cover.ifBlank { a.cover } else a.cover,
        isrc = a.isrc.ifBlank { b.isrc.uppercase() },
        ytId = a.ytId.ifBlank { b.ytId },
        spotifyId = a.spotifyId.ifBlank { b.spotifyId },
        saavnId = a.saavnId.ifBlank { b.saavnId },
        lastfmMbid = a.lastfmMbid.ifBlank { b.lastfmMbid },
        language = a.language.ifBlank { b.language },
        explicit = a.explicit || b.explicit,
    )

    private fun write(id: String, c: Candidate) {
        val v = ContentValues().apply {
            put("canonical_id", id)
            put("isrc", c.isrc.uppercase()); put("title", c.title); put("artist", c.artists)
            put("title_key", SongMatcher.titleKey(c.title, c.artists))
            put("artist_key", SongMatcher.artistKeys(c.artists).firstOrNull().orEmpty())
            put("yt_id", c.ytId); put("spotify_id", c.spotifyId); put("saavn_id", c.saavnId)
            put("lastfm_mbid", c.lastfmMbid); put("language", c.language.lowercase())
            put("album", c.album); put("cover", c.cover)
            put("duration_sec", c.durationSec); put("explicit", if (c.explicit) 1 else 0)
            put("updated_at", System.currentTimeMillis())
        }
        writableDatabase.insertWithOnConflict("songs", null, v, SQLiteDatabase.CONFLICT_REPLACE)
    }

    /** Songs whose main artist is [artistKey] (for the artist's usual feel and language). */
    fun songsByArtist(artistKey: String, limit: Int = 120): List<Song> =
        if (artistKey.isBlank()) emptyList() else query("SELECT * FROM songs WHERE artist_key = ? LIMIT $limit", artistKey)

    // ── song_tags ───────────────────────────────────────────────────────────

    data class Tag(val tag: String, val src: String, val weight: Double, val at: Long)

    /** Tags [ids] with [tag] from [src] (re-tagging keeps the higher weight and the new time). */
    @Synchronized
    fun putTags(ids: Collection<String>, tag: String, src: String, weight: Double, at: Long = System.currentTimeMillis()) {
        if (ids.isEmpty()) return
        val db = writableDatabase
        db.beginTransaction()
        try {
            val st = db.compileStatement("UPDATE song_tags SET weight = MAX(weight, ?), at = ? WHERE song_id = ? AND tag = ? AND src = ?")
            ids.forEach { id ->
                st.clearBindings()
                st.bindDouble(1, weight); st.bindLong(2, at); st.bindString(3, id); st.bindString(4, tag); st.bindString(5, src)
                if (st.executeUpdateDelete() == 0) db.insert("song_tags", null, ContentValues().apply {
                    put("song_id", id); put("tag", tag); put("src", src); put("weight", weight); put("at", at)
                })
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun tags(ids: Collection<String>): Map<String, List<Tag>> {
        if (ids.isEmpty()) return emptyMap()
        val out = HashMap<String, MutableList<Tag>>()
        ids.chunked(400).forEach { chunk ->
            readableDatabase.rawQuery(
                "SELECT song_id, tag, src, weight, at FROM song_tags WHERE song_id IN (${chunk.joinToString(",") { "?" }})",
                chunk.toTypedArray(),
            ).use { c -> while (c.moveToNext()) out.getOrPut(c.getString(0)) { ArrayList() } += Tag(c.getString(1), c.getString(2), c.getDouble(3), c.getLong(4)) }
        }
        return out
    }

    fun idsWithTag(tag: String, sinceMs: Long, limit: Int = 500): List<String> =
        readableDatabase.rawQuery("SELECT song_id FROM song_tags WHERE tag = ? AND at >= ? ORDER BY at DESC LIMIT $limit", arrayOf(tag, sinceMs.toString()))
            .use { c -> buildList { while (c.moveToNext()) add(c.getString(0)) } }

    fun tagCount(src: String): Int =
        readableDatabase.rawQuery("SELECT COUNT(DISTINCT song_id) FROM song_tags WHERE src = ?", arrayOf(src)).use { if (it.moveToFirst()) it.getInt(0) else 0 }

    // ── song_audio ──────────────────────────────────────────────────────────

    data class Audio(val bpm: Double, val energy: Double)

    fun putAudio(songId: String, bpm: Double, energy: Double, loudnessDb: Double, beat: Double) {
        writableDatabase.insertWithOnConflict("song_audio", null, ContentValues().apply {
            put("song_id", songId); put("bpm", bpm); put("energy", energy); put("loudness_db", loudnessDb)
            put("beat", beat); put("at", System.currentTimeMillis())
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun audio(ids: Collection<String>): Map<String, Audio> {
        if (ids.isEmpty()) return emptyMap()
        val out = HashMap<String, Audio>()
        ids.chunked(400).forEach { chunk ->
            readableDatabase.rawQuery("SELECT song_id, bpm, energy FROM song_audio WHERE song_id IN (${chunk.joinToString(",") { "?" }})", chunk.toTypedArray())
                .use { c -> while (c.moveToNext()) out[c.getString(0)] = Audio(c.getDouble(1), c.getDouble(2)) }
        }
        return out
    }

    fun audioCount(): Int = readableDatabase.rawQuery("SELECT COUNT(*) FROM song_audio", null).use { if (it.moveToFirst()) it.getInt(0) else 0 }

    // ── feedback ────────────────────────────────────────────────────────────

    /** [kind]: MORE / LESS (target = song id), BLOCK_SONG (song id), BLOCK_ARTIST (artist key). */
    fun putFeedback(userId: String, kind: String, target: String) {
        writableDatabase.insertWithOnConflict("feedback", null, ContentValues().apply {
            put("user_id", userId); put("kind", kind); put("target", target); put("at", System.currentTimeMillis())
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun removeFeedback(userId: String, kind: String, target: String) {
        writableDatabase.delete("feedback", "user_id = ? AND kind = ? AND target = ?", arrayOf(userId, kind, target))
    }

    fun feedback(userId: String): List<Pair<String, String>> =
        readableDatabase.rawQuery("SELECT kind, target FROM feedback WHERE user_id = ? ORDER BY at DESC", arrayOf(userId))
            .use { c -> buildList { while (c.moveToNext()) add(c.getString(0) to c.getString(1)) } }

    // ── source_cache ────────────────────────────────────────────────────────

    fun cachedList(seedId: String, source: Source, maxAgeMs: Long): List<String>? =
        readableDatabase.rawQuery(
            "SELECT ranked_ids, fetched_at FROM source_cache WHERE seed_id = ? AND source = ?",
            arrayOf(seedId, source.name),
        ).use { c ->
            if (!c.moveToFirst()) return null
            if (System.currentTimeMillis() - c.getLong(1) > maxAgeMs) return null
            val a = JSONArray(c.getString(0))
            (0 until a.length()).map { a.getString(it) }
        }

    fun putList(seedId: String, source: Source, ids: List<String>) {
        writableDatabase.insertWithOnConflict("source_cache", null, ContentValues().apply {
            put("seed_id", seedId); put("source", source.name)
            put("ranked_ids", JSONArray(ids).toString()); put("fetched_at", System.currentTimeMillis())
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun pruneCache(maxAgeMs: Long) {
        writableDatabase.delete("source_cache", "fetched_at < ?", arrayOf((System.currentTimeMillis() - maxAgeMs).toString()))
    }

    // ── play_history + transitions ─────────────────────────────────────────

    data class Play(val rowId: Long, val songId: String, val playedAt: Long, val skipped: Boolean, val listenedMs: Long)

    fun plays(userId: String, sinceMs: Long = 0, limit: Int = 5000): List<Play> =
        readableDatabase.rawQuery(
            "SELECT id, song_id, played_at, skipped, listened_ms FROM play_history WHERE user_id = ? AND played_at >= ? ORDER BY played_at DESC LIMIT $limit",
            arrayOf(userId, sinceMs.toString()),
        ).use { c -> buildList { while (c.moveToNext()) add(Play(c.getLong(0), c.getString(1), c.getLong(2), c.getInt(3) == 1, c.getLong(4))) } }

    fun playCount(userId: String): Int =
        readableDatabase.rawQuery("SELECT COUNT(*) FROM play_history WHERE user_id = ?", arrayOf(userId))
            .use { if (it.moveToFirst()) it.getInt(0) else 0 }

    /**
     * Logs a play of [songId] at [at]. The previous play is closed first: if it lasted
     * under 30 s (or under half a short song) it counts as skipped, and the habit that
     * led to it is taken back. A play within 30 minutes of the previous one adds the
     * transition previous → this.
     */
    @Synchronized
    fun recordPlay(userId: String, songId: String, at: Long) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val recent = plays(userId, limit = 2)
            val prev = recent.getOrNull(0)
            if (prev != null && prev.songId == songId && at - prev.playedAt < 60_000) {
                db.setTransactionSuccessful(); return // same song restarted
            }
            if (prev != null) {
                val listened = (at - prev.playedAt).coerceAtLeast(0)
                val durMs = (song(prev.songId)?.info?.durationSec ?: 0) * 1000L
                val skipped = listened < minOf(30_000L, if (durMs > 0) durMs / 2 else 30_000L)
                db.execSQL("UPDATE play_history SET skipped = ?, listened_ms = ? WHERE id = ?",
                    arrayOf<Any>(if (skipped) 1 else 0, minOf(listened, if (durMs > 0) durMs else listened), prev.rowId))
                val before = recent.getOrNull(1)
                if (skipped && before != null && prev.playedAt - before.playedAt <= 30 * 60_000L) {
                    db.execSQL("UPDATE transitions SET count = MAX(count - 1, 0) WHERE user_id = ? AND from_id = ? AND to_id = ?",
                        arrayOf(userId, before.songId, prev.songId))
                }
                if (at - prev.playedAt <= 30 * 60_000L && prev.songId != songId) addTransition(db, userId, prev.songId, songId, at)
            }
            db.insert("play_history", null, ContentValues().apply {
                put("user_id", userId); put("song_id", songId); put("played_at", at)
            })
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private fun addTransition(db: SQLiteDatabase, userId: String, from: String, to: String, at: Long) {
        // UPDATE-then-INSERT: SQLite upsert needs Android 11, the app supports 8+.
        val st = db.compileStatement("UPDATE transitions SET count = count + 1, last_at = ? WHERE user_id = ? AND from_id = ? AND to_id = ?")
        st.bindLong(1, at); st.bindString(2, userId); st.bindString(3, from); st.bindString(4, to)
        if (st.executeUpdateDelete() == 0) {
            db.insert("transitions", null, ContentValues().apply {
                put("user_id", userId); put("from_id", from); put("to_id", to); put("count", 1); put("last_at", at)
            })
        }
    }

    data class Transition(val toId: String, val count: Int, val lastAt: Long)

    fun transitionsFrom(userId: String, fromId: String): List<Transition> =
        readableDatabase.rawQuery(
            "SELECT to_id, count, last_at FROM transitions WHERE user_id = ? AND from_id = ? AND count > 0 ORDER BY count DESC, last_at DESC LIMIT 30",
            arrayOf(userId, fromId),
        ).use { c -> buildList { while (c.moveToNext()) add(Transition(c.getString(0), c.getInt(1), c.getLong(2))) } }

    /** Imports plays logged before this database existed (oldest first). */
    // Lock order everywhere: this object first, then the SQLite transaction (else two threads deadlock).
    @Synchronized
    fun importHistory(userId: String, plays: List<Pair<Candidate, Long>>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            var prev: Pair<String, Long>? = null
            for ((c, at) in plays.sortedBy { it.second }) {
                val id = resolve(c).id
                db.insert("play_history", null, ContentValues().apply {
                    put("user_id", userId); put("song_id", id); put("played_at", at)
                })
                prev?.let { (pid, pat) -> if (pid != id && at - pat <= 30 * 60_000L) addTransition(db, userId, pid, id, at) }
                prev = id to at
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    /** Moves everything logged under [from] (e.g. "local", before an email was set) to [to]. */
    @Synchronized
    fun adoptUser(from: String, to: String) {
        if (from == to) return
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.execSQL("UPDATE play_history SET user_id = ? WHERE user_id = ?", arrayOf(to, from))
            db.execSQL("UPDATE OR IGNORE transitions SET user_id = ? WHERE user_id = ?", arrayOf(to, from))
            db.execSQL("UPDATE OR IGNORE user_taste SET user_id = ? WHERE user_id = ?", arrayOf(to, from))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    // ── user_taste ──────────────────────────────────────────────────────────

    fun likedIds(userId: String): Set<String> =
        readableDatabase.rawQuery("SELECT liked_ids FROM user_taste WHERE user_id = ?", arrayOf(userId)).use { c ->
            if (!c.moveToFirst()) return emptySet()
            val a = JSONArray(c.getString(0))
            (0 until a.length()).mapTo(HashSet()) { a.getString(it) }
        }

    fun addLiked(userId: String, ids: Collection<String>) {
        if (ids.isEmpty()) return
        val all = likedIds(userId) + ids
        upsertTaste(userId, "liked_ids", JSONArray(all.toList()).toString())
    }

    fun saveArtistWeights(userId: String, json: String) {
        upsertTaste(userId, "artist_weights", json)
    }

    @Synchronized
    private fun upsertTaste(userId: String, column: String, value: String) {
        val v = ContentValues().apply { put(column, value); put("updated_at", System.currentTimeMillis()) }
        if (writableDatabase.update("user_taste", v, "user_id = ?", arrayOf(userId)) == 0) {
            v.put("user_id", userId)
            writableDatabase.insert("user_taste", null, v)
        }
    }
}

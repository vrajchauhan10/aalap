package com.music.aalap.data.recommendation.engine

import android.content.Context
import android.util.Log
import android.util.LruCache
import com.music.aalap.data.entity.SongsModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Works out each song's [Vibe] (feel, energy, style) and language from what the app
 * knows, without any paid audio-analysis service:
 *
 *  1. its title and album words (lofi, remix, bhajan, dard, ishq, workout…),
 *  2. mood playlists it's on (the songs of the Party, Sad, Devotional… stations are
 *     labelled with that feel whenever a station is built, and in the background),
 *  3. the artist's other songs (an artist's usual feel and language),
 *  4. what the world plays right after it (its "up next" lists), which share its feel.
 *
 * Also keeps today's chart songs (Top 50 India, Trending India, Top 50 Global…) as
 * trending. Results are cached in memory; labels persist in the song_tags table.
 */
object VibeStore {
    private const val TAG = "VibeStore"
    const val TREND = "TREND"
    private const val NEIGHBOUR_AGE_MS = 14 * 86_400_000L
    private const val LEARN_EVERY_MS = 3 * 86_400_000L
    private const val CHARTS_EVERY_MS = 20 * 3_600_000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val cache = LruCache<String, Vibe>(8000)
    private val languages = LruCache<String, String>(8000)
    private data class ArtistInfo(val feel: Map<Feel, Double>, val mass: Double, val language: String, val at: Long, val audioEnergy: Double? = null)
    private val artists = ConcurrentHashMap<String, ArtistInfo>()
    @Volatile private var trending: Pair<Long, Set<String>>? = null

    /** Labels changed (new mood or chart songs): recompute what's cached. */
    fun invalidate() { cache.evictAll(); artists.clear(); trending = null }

    // ── a song's own evidence ──────────────────────────────────────────────

    /** A song's words read once (the same songs come up as neighbours of many candidates). */
    private val words = android.util.LruCache<String, Map<Feel, Double>>(4_000)

    private fun direct(s: Song, tags: List<RecoDb.Tag>?): Map<Feel, Double> {
        val key = s.title + "\u0000" + s.info.album
        val read = words.get(key) ?: Vibe.lexicon(s.title, s.info.album).also { words.put(key, it) }
        val out = HashMap(read)
        tags?.forEach { t ->
            val feel = runCatching { Feel.valueOf(t.tag) }.getOrNull() ?: return@forEach
            out[feel] = minOf(3.0, (out[feel] ?: 0.0) + 1.6 * t.weight)
        }
        return out
    }

    private fun artistInfo(db: RecoDb, key: String): ArtistInfo {
        if (key.isBlank()) return ArtistInfo(emptyMap(), 0.0, "", 0)
        artists[key]?.takeIf { System.currentTimeMillis() - it.at < 3_600_000L }?.let { return it }
        val songs = db.songsByArtist(key)
        val tags = db.tags(songs.map { it.id })
        // Share of the artist's songs with each feel as their main one (songs with no
        // evidence count too, so a few strongly labelled songs can't speak for all).
        val count = HashMap<Feel, Double>()
        var withEvidence = 0
        val lang = HashMap<String, Int>()
        songs.forEach { s ->
            val d = direct(s, tags[s.id])
            d.maxByOrNull { it.value }?.takeIf { it.value >= 0.5 }?.let { (f, _) -> count[f] = (count[f] ?: 0.0) + 1; withEvidence++ }
            s.info.language.takeIf { it.isNotBlank() }?.let { lang[it] = (lang[it] ?: 0) + 1 }
        }
        val n = songs.size.coerceAtLeast(1).toDouble()
        val share = count.mapValues { it.value / n }
        // Devotional and instrumental are what an artist does now and then, unless it's mostly what they do.
        val feel = share.mapValues { (f, v) -> if ((f == Feel.DEVOTIONAL || f == Feel.FOCUS) && v < 0.4) v * 0.3 else v }
        // The artist's typical measured energy (when at least two of their songs were measured).
        val measured = db.audio(songs.map { it.id }).values
        val info = ArtistInfo(
            feel = feel,
            mass = withEvidence.toDouble(),
            language = lang.maxByOrNull { it.value }?.key.orEmpty(),
            at = System.currentTimeMillis(),
            audioEnergy = measured.takeIf { it.size >= 2 }?.map { it.energy }?.average(),
        )
        artists[key] = info
        return info
    }

    /** Vibes for [songs] (by canonical id). */
    fun vibes(context: Context, songs: Collection<Song>): Map<String, Vibe> {
        val out = HashMap<String, Vibe>()
        val todo = songs.filter { s -> cache.get(s.id)?.also { out[s.id] = it } == null }.distinctBy { it.id }
        if (todo.isEmpty()) return out
        val db = RecoDb.get(context)
        val tags = db.tags(todo.map { it.id })
        // What the world plays after each song (when its lists were fetched lately).
        val neighbourIds = HashMap<String, List<String>>()
        todo.forEach { s ->
            val ids = Source.entries.flatMap { src -> db.cachedList(s.id, src, NEIGHBOUR_AGE_MS).orEmpty().take(8) }.distinct().take(16)
            if (ids.isNotEmpty()) neighbourIds[s.id] = ids
        }
        val audio = db.audio(todo.map { it.id })
        val nSongs = db.songs(neighbourIds.values.flatten().toSet())
        val nTags = db.tags(nSongs.keys)
        for (s in todo) {
            val raw = HashMap(direct(s, tags[s.id]))
            val key = SongMatcher.artistKeys(s.artists).firstOrNull().orEmpty()
            val a = artistInfo(db, key)
            // The artist's usual feel (shares of their songs), counting more the more of them are known.
            if (a.feel.isNotEmpty()) Vibe.addInto(raw, a.feel, 1.4 * minOf(1.0, a.mass / 5.0))
            neighbourIds[s.id]?.let { ids ->
                val n = HashMap<Feel, Double>()
                var k = 0
                ids.mapNotNull { nSongs[it] }.forEach { ns -> Vibe.addInto(n, direct(ns, nTags[ns.id]), 1.0); k++ }
                if (k > 0) Vibe.addInto(raw, n, 0.7 / k * minOf(k, 6))
            }
            // Measured from the sound: the song's own, else its artist's typical energy.
            val own = audio[s.id]
            val v = Vibe.of(raw, Vibe.styles(s.title, s.info.album, s.artists)).withAudio(own?.energy ?: a.audioEnergy, own?.bpm)
            cache.put(s.id, v)
            out[s.id] = v
            languages.put(s.id, s.info.language.ifBlank { a.language.ifBlank { guessLanguage(s.title, s.info.album) } }.lowercase())
        }
        return out
    }

    fun vibe(context: Context, song: Song): Vibe = vibes(context, listOf(song))[song.id] ?: Vibe.UNKNOWN

    /** Debug: where a song's feel comes from (its words and labels, its artist, what plays next to it). */
    fun explain(context: Context, song: Song): String {
        val db = RecoDb.get(context)
        val own = direct(song, db.tags(listOf(song.id))[song.id])
        val a = artistInfo(db, SongMatcher.artistKeys(song.artists).firstOrNull().orEmpty())
        val ids = Source.entries.flatMap { src -> db.cachedList(song.id, src, NEIGHBOUR_AGE_MS).orEmpty().take(8) }.distinct().take(16)
        val ns = db.songs(ids); val nt = db.tags(ns.keys)
        val n = HashMap<Feel, Double>(); ns.values.forEach { Vibe.addInto(n, direct(it, nt[it.id]), 1.0) }
        fun m(x: Map<Feel, Double>) = x.entries.sortedByDescending { it.value }.take(3).joinToString(" ") { "${it.key.label}:" + "%.2f".format(it.value) }
        return "own[${m(own)}] tags${db.tags(listOf(song.id))[song.id].orEmpty().map { it.tag + "/" + it.src }} " +
            "artist[${m(a.feel)} known=${a.mass.toInt()}] next(${ns.size})[${m(n)}]"
    }

    /** The song's language, or its artist's usual one. */
    fun language(context: Context, song: Song): String {
        song.info.language.takeIf { it.isNotBlank() }?.let { return it.lowercase() }
        languages.get(song.id)?.let { return it }
        val key = SongMatcher.artistKeys(song.artists).firstOrNull().orEmpty()
        return artistInfo(RecoDb.get(context), key).language.ifBlank { guessLanguage(song.title, song.info.album) }.lowercase().also { languages.put(song.id, it) }
    }

    /** Saves a song's measured tempo and energy (from [com.music.aalap.audio.AudioAnalysis]). */
    fun saveAudio(context: Context, song: SongsModel, r: com.music.aalap.audio.AudioFeatures.Result) {
        scope.launch {
            runCatching {
                val db = RecoDb.get(context)
                val s = db.resolve(SongBridge.toCandidate(song))
                db.putAudio(s.id, r.bpm, r.energy, r.loudnessDb, r.beatStrength)
                cache.remove(s.id)
                SongMatcher.artistKeys(s.artists).firstOrNull()?.let { artists.remove(it) }
                Log.d(TAG, "measured '${s.title}': " + "%.0f bpm, energy %.2f".format(r.bpm, r.energy))
            }
        }
    }

    fun guessLanguage(title: String, album: String = ""): String = LanguageGuess.of(title, album)

    // ── labels ─────────────────────────────────────────────────────────────

    /** The songs of a mood station ([moodName] = Party, Sad…) carry that feel. */
    fun tagMood(context: Context, songs: List<SongsModel>, moodName: String) {
        val feel = Vibe.forMoodName(moodName)
        if (songs.isEmpty() || feel.isEmpty()) return
        scope.launch {
            runCatching {
                val db = RecoDb.get(context)
                val cands = songs.map(SongBridge::toCandidate)
                // A cover or instrumental version on a mood playlist can match the original
                // song by title; only label songs whose artist is the playlist track's artist.
                val ids = db.resolveAll(cands).zip(cands).filter { (s, c) -> SongMatcher.artistsOverlap(s.artists, c.artists) }.map { it.first.id }
                feel.forEach { (f, w) -> db.putTags(ids, f.name, "mood", w) }
                invalidate()
                Log.d(TAG, "tagged ${ids.size} songs as $moodName")
            }.onFailure { Log.w(TAG, "tagMood failed: $it") }
        }
    }

    /** Songs on today's charts. */
    fun tagTrending(context: Context, songs: List<SongsModel>) {
        if (songs.isEmpty()) return
        val db = RecoDb.get(context)
        val ids = db.resolveAll(songs.map(SongBridge::toCandidate)).map { it.id }
        db.putTags(ids, TREND, "chart", 1.0)
        trending = null
    }

    /** Canonical ids of songs on the charts in the last 3 days. */
    fun trendingIds(context: Context): Set<String> {
        trending?.takeIf { System.currentTimeMillis() - it.first < 10 * 60_000L }?.let { return it.second }
        val ids = runCatching { RecoDb.get(context).idsWithTag(TREND, System.currentTimeMillis() - 3 * 86_400_000L).toSet() }.getOrDefault(emptySet())
        trending = System.currentTimeMillis() to ids
        return ids
    }

    private val learning = AtomicBoolean(false)

    /**
     * In the background, now and then: builds the mood stations (which labels their
     * songs with a feel) and reads the charts (trending). Every 3 days for moods,
     * daily for charts; never twice at once.
     */
    fun learnInBackground(context: Context) {
        val app = context.applicationContext
        if (!learning.compareAndSet(false, true)) return
        scope.launch {
            try {
                val p = app.getSharedPreferences("vibe_store", Context.MODE_PRIVATE)
                val now = System.currentTimeMillis()
                if (now - p.getLong("charts_at", 0) > CHARTS_EVERY_MS) {
                    val api = com.music.aalap.data.api.Api(app)
                    var n = 0
                    com.music.aalap.data.api.CHARTS.forEach { chart ->
                        runCatching {
                            val songs = api.getPlaylistSongs(chart.id).toList()
                                .filterIsInstance<com.music.aalap.data.api.Response.Success<List<SongsModel>>>()
                                .lastOrNull()?.data.orEmpty()
                            tagTrending(app, songs.take(60)); n += songs.size
                        }
                    }
                    if (n > 0) p.edit().putLong("charts_at", now).apply()
                    Log.d(TAG, "charts: $n trending songs")
                }
                if (now - p.getLong("moods_at", 0) > LEARN_EVERY_MS) {
                    var built = 0
                    com.music.aalap.data.api.Mood.entries.forEach { m ->
                        runCatching { if (com.music.aalap.data.api.Discover.moodMix(app, m).isNotEmpty()) built++ }
                    }
                    if (built >= 5) p.edit().putLong("moods_at", now).apply()
                    Log.d(TAG, "mood stations learned: $built")
                }
            } finally {
                learning.set(false)
            }
        }
    }
}

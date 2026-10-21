package com.music.spotui.data.recommendation.engine

import android.content.Context
import android.util.Log
import com.music.spotui.data.entity.SongsModel
import com.music.spotui.data.recommendation.engine.sources.JioSaavnRadioSource
import com.music.spotui.data.recommendation.engine.sources.LastFmSource
import com.music.spotui.data.recommendation.engine.sources.NextSongSource
import com.music.spotui.data.recommendation.engine.sources.SpotifyRadioSource
import com.music.spotui.data.recommendation.engine.sources.YouTubeMusicSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Random
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Builds the "up next" queue by chaining global listening behaviour:
 *
 *  1. RESOLVE the seed to its canonical song.
 *  2. FETCH each source's "played next" list in parallel (800 ms each for the first
 *     song; 4 s for songs 2…N, which build in the background), cached 24 h.
 *  3. NORMALIZE + DEDUPE every result to canonical ids.
 *  4. FUSE with Reciprocal Rank Fusion ([Fusion]).
 *  5. PERSONALIZE: 0.6·global + 0.2·taste + 0.2·habit ([Personalizer]).
 *  6. DIVERSIFY: no artist twice in a row, nothing recent or recently skipped, sampled
 *     from the top 5 ([Diversifier]).
 *  7. CHAIN: the pick becomes the next seed, until the queue has [DEFAULT_LENGTH] songs.
 *
 * Songs are emitted one by one, so the first is ready as soon as it's picked; the next
 * seed's sources start loading right away. A slow or failing source never blocks:
 * whatever it returns later still lands in the cache for next time.
 *
 * Fallbacks when no source answers: the listener's own transitions after the seed,
 * then the seed artist's top tracks.
 */
object QueueEngine {
    private const val TAG = "QueueEngine"
    const val DEFAULT_LENGTH = 5
    const val SOURCE_TIMEOUT_MS = 800L
    /** For the very first pick of a cold seed only: the song is already playing, so wait a bit longer before falling back. */
    const val FIRST_PICK_GRACE_MS = 2_500L
    const val CACHE_TTL_MS = 24 * 3_600_000L
    /**
     * Songs 2…N are built in the background while music plays (nothing waits on them),
     * so each source gets longer there: a source that answers in 2 s beats a fallback.
     */
    const val BACKGROUND_TIMEOUT_MS = 4_000L
    /** How many fused candidates per step go on to personalisation. */
    const val CANDIDATE_POOL = 40

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val inflight = ConcurrentHashMap<String, Deferred<List<String>?>>()

    fun sources(context: Context): List<NextSongSource> =
        listOf(YouTubeMusicSource, SpotifyRadioSource(context.applicationContext), JioSaavnRadioSource, LastFmSource)
            .filter { it.enabled }

    data class QueueItem(val scored: Scored, val via: String)

    // ── 2 + 3: fetch, normalise, cache ──────────────────────────────────────

    /** One network fetch per (seed, source) at a time; the result is cached even if nobody waits for it. */
    private fun fetchJob(context: Context, seed: Song, src: NextSongSource): Deferred<List<String>?> {
        val key = "${seed.id}|${src.source}"
        inflight[key]?.let { return it }
        val job = scope.async(start = CoroutineStart.LAZY) {
            try {
                val res = src.getNextSongs(seed)
                val db = RecoDb.get(context)
                res.seedUpdate?.let { db.enrich(seed.id, it) }
                val ids = db.resolveAll(res.ranked).map { it.id }.filter { it != seed.id }.distinct()
                if (ids.isNotEmpty()) db.putList(seed.id, src.source, ids)
                Log.d(TAG, "${src.source} after '${seed.title}': ${ids.size}")
                ids
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Log.w(TAG, "${src.source} failed for '${seed.title}': $e")
                null
            } finally {
                inflight.remove(key)
            }
        }
        inflight.putIfAbsent(key, job)?.let { return it }
        job.start()
        return job
    }

    /** Every source's ranked canonical ids for [seed]: cached, or fetched within [timeoutMs]. */
    suspend fun ranked(context: Context, seed: Song, timeoutMs: Long = SOURCE_TIMEOUT_MS): Map<Source, List<String>> = coroutineScope {
        val db = RecoDb.get(context)
        sources(context).map { src ->
            async {
                val list = db.cachedList(seed.id, src.source, CACHE_TTL_MS)
                    ?: withTimeoutOrNull(timeoutMs) { fetchJob(context, seed, src).await() }
                src.source to list
            }
        }.awaitAll().mapNotNull { (s, l) -> l?.takeIf { it.isNotEmpty() }?.let { s to it } }.toMap()
    }

    /** Starts loading [seed]'s sources in the background (no waiting). */
    fun prefetch(context: Context, seed: Song) {
        val db = RecoDb.get(context)
        sources(context).forEach { src -> if (db.cachedList(seed.id, src.source, CACHE_TTL_MS) == null) fetchJob(context, seed, src) }
    }

    // ── 4 + 5: fuse and personalise ─────────────────────────────────────────

    suspend fun scoredAfter(
        context: Context,
        userId: String,
        seed: Song,
        timeoutMs: Long = SOURCE_TIMEOUT_MS,
        graceMs: Long = 0,
    ): List<Scored> {
        var ranked = ranked(context, seed, timeoutMs)
        if (ranked.isEmpty() && graceMs > 0) ranked = ranked(context, seed, graceMs)
        // Only the strongest global candidates are re-ranked, so taste can reorder them but
        // can't pull in a song the world rarely plays after this one.
        val fused = Fusion.fuse(ranked).take(CANDIDATE_POOL)
        val top = fused.firstOrNull()?.score?.takeIf { it > 0 } ?: 1.0
        val db = RecoDb.get(context)
        // The listener's own habits after this seed join the pool even if no source suggested them.
        val habitIds = db.transitionsFrom(userId, seed.id).map { it.toId }
        val songs = db.songs(fused.map { it.id } + habitIds)
        val fusedIds = fused.mapTo(HashSet()) { it.id }
        val pairs = fused.mapNotNull { f -> songs[f.id]?.let { it to f.score / top } } +
            habitIds.filter { it !in fusedIds }.mapNotNull { id -> songs[id]?.let { it to 0.0 } }
        return Personalizer.score(context, userId, seed, pairs, fused.associate { it.id to it.sources })
            .sortedByDescending { it.final }
    }

    // ── 6 + 7: diversify and chain ──────────────────────────────────────────

    /**
     * build_queue: the next [length] songs after [seed], chained. [excludeAppIds] are
     * songs already in the player's queue.
     */
    fun buildQueue(
        context: Context,
        seed: SongsModel,
        length: Int = DEFAULT_LENGTH,
        excludeAppIds: Set<Int> = emptySet(),
        userId: String = RecoDb.userId(context),
    ): Flow<QueueItem> = flow {
        ensureImported(context)
        val seedSong = RecoDb.get(context).resolve(SongBridge.toCandidate(seed))
        chain(context, userId, seedSong, length, excludeAppIds) { emit(it) }
    }.flowOn(Dispatchers.IO)

    /** The same queue as app songs, for the player. */
    fun queueSongs(context: Context, seed: SongsModel, length: Int = DEFAULT_LENGTH, excludeAppIds: Set<Int> = emptySet()): Flow<SongsModel> =
        buildQueue(context, seed, length, excludeAppIds).map { SongBridge.toSongsModel(it.scored.song) }

    suspend fun chain(
        context: Context,
        userId: String,
        seed: Song,
        length: Int,
        excludeAppIds: Set<Int>,
        onPick: suspend (QueueItem) -> Unit,
    ) {
        val profile = Personalizer.profile(context, userId)
        val excluded = HashSet<String>(profile.recentIds + profile.recentSkips)
        excluded += seed.id
        val rnd = Random()
        var cur = seed
        for (step in 0 until length) {
            val scored = if (step == 0) scoredAfter(context, userId, cur, SOURCE_TIMEOUT_MS, FIRST_PICK_GRACE_MS)
                else scoredAfter(context, userId, cur, BACKGROUND_TIMEOUT_MS)
                .filter { SongBridge.appId(it.song) !in excludeAppIds }
            val pick = Diversifier.pick(scored, cur.artists, excluded, rnd)
                ?.let { QueueItem(it, if (it.global == 0.0 && it.habit > 0) "habit" else "fusion") }
                ?: fallback(context, userId, cur, excluded, excludeAppIds, rnd)
            if (pick == null) {
                Log.w(TAG, "no next song after '${cur.title}'")
                return
            }
            Log.d(TAG, "#${step + 1} '${pick.scored.song.title}' via ${pick.via} " +
                "(global %.2f taste %.2f habit %.2f → %.3f, %s)".format(pick.scored.global, pick.scored.taste, pick.scored.habit, pick.scored.final, pick.scored.sources))
            onPick(pick)
            excluded += pick.scored.song.id
            cur = pick.scored.song
            prefetch(context, cur) // next seed's sources load while this song is queued
        }
    }

    /** No source answered: your own transitions after the seed, then the artist's top tracks. */
    private suspend fun fallback(
        context: Context,
        userId: String,
        seed: Song,
        excluded: Set<String>,
        excludeAppIds: Set<Int>,
        rnd: Random,
    ): QueueItem? {
        val db = RecoDb.get(context)
        val habits = db.transitionsFrom(userId, seed.id)
        val habitSongs = db.songs(habits.map { it.toId })
        val fromHabits = Personalizer.score(context, userId, seed, habits.mapNotNull { t -> habitSongs[t.toId]?.let { it to 0.0 } })
            .filter { SongBridge.appId(it.song) !in excludeAppIds }
        Diversifier.pick(fromHabits, seed.artists, excluded, rnd)?.let { return QueueItem(it, "habit") }

        val top = runCatching { com.music.spotui.data.api.Api(context).artistTopCandidates(seed.artists.substringBefore(",").trim()) }
            .getOrDefault(emptyList())
        if (top.isEmpty()) return null
        val songs = db.resolveAll(top)
        val n = songs.size.toDouble()
        val scored = Personalizer.score(context, userId, seed, songs.mapIndexed { i, s -> s to (1.0 - i / n) })
            .filter { SongBridge.appId(it.song) !in excludeAppIds }
        return Diversifier.pick(scored, "", excluded, rnd)?.let { QueueItem(it, "artist-top") }
    }

    // ── recording what the listener does ───────────────────────────────────

    /** A song started playing: log it (skips, habits), refresh the taste and warm its "next" lists. */
    fun onPlay(context: Context, song: SongsModel, at: Long = System.currentTimeMillis()) {
        if (song.title.isBlank()) return
        scope.launch {
            runCatching {
                ensureImported(context)
                val db = RecoDb.get(context)
                val s = db.resolve(SongBridge.toCandidate(song))
                db.recordPlay(RecoDb.userId(context), s.id, at)
                Personalizer.invalidate()
                prefetch(context, s)
            }.onFailure { Log.w(TAG, "onPlay failed: $it") }
        }
    }

    /** Liked songs from the library, so the taste knows them even before they're played. */
    fun rememberLiked(context: Context, songs: List<SongsModel>) {
        if (songs.isEmpty()) return
        scope.launch {
            runCatching {
                val db = RecoDb.get(context)
                db.addLiked(RecoDb.userId(context), db.resolveAll(songs.map(SongBridge::toCandidate)).map { it.id })
                Personalizer.invalidate()
            }
        }
    }

    private val imported = AtomicBoolean(false)

    /** One-time import of the plays logged before 2.5 into play_history and transitions. */
    @Synchronized
    fun ensureImported(context: Context) {
        if (imported.get()) return
        val prefs = context.getSharedPreferences("reco_engine", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("history_imported", false)) {
            runCatching {
                val history = com.music.spotui.data.preferences.getListeningHistory(context)
                RecoDb.get(context).importHistory(RecoDb.userId(context), history.map { e ->
                    Candidate(
                        title = e.title, artists = e.singer, durationSec = e.durationMs / 1000,
                        album = e.album, cover = e.image, spotifyId = e.spotifyTrackId,
                    ) to e.ts
                })
                Log.d(TAG, "imported ${history.size} plays")
                prefs.edit().putBoolean("history_imported", true).apply()
                imported.set(true)
            }.onFailure { Log.w(TAG, "history import failed (retries next time): $it") }
            runCatching { RecoDb.get(context).pruneCache(CACHE_TTL_MS) }
        } else {
            imported.set(true)
        }
    }

    // ── Home surfaces built on the same pipeline ───────────────────────────

    /**
     * Fresh picks: new-to-you songs. Seeds are 3 of your all-time favourites and 2 of
     * this week's finished plays (sampled, so they rotate); each seed's fused global
     * list is personalised, anything you've already played or liked is dropped, and
     * the seeds are interleaved with at most two songs per artist.
     */
    suspend fun freshPicks(context: Context, salt: Long, limit: Int = 25): List<SongsModel> = coroutineScope {
        ensureImported(context)
        val userId = RecoDb.userId(context)
        val db = RecoDb.get(context)
        val profile = Personalizer.profile(context, userId)
        val rnd = Random(salt)
        val plays = db.plays(userId, limit = 3000)
        val heard = plays.mapTo(HashSet()) { it.songId } + profile.liked
        val weekAgo = System.currentTimeMillis() - 7 * 86_400_000L
        val favourites = profile.songAffinity.entries.sortedByDescending { it.value }.take(30)
        val recent = plays.filter { !it.skipped && it.playedAt >= weekAgo }.map { it.songId }.distinct().take(25)

        val seedIds = LinkedHashSet<String>()
        repeat(3) { Diversifier.sampleWeighted(favourites.filter { it.key !in seedIds }, rnd) { it.value }?.let { seedIds += it.key } }
        recent.filter { it !in seedIds }.shuffled(rnd).take(2).forEach { seedIds += it }
        val seedSongs = db.songs(seedIds).values.distinctBy { SongMatcher.artistKeys(it.artists).firstOrNull() }
        if (seedSongs.isEmpty()) return@coroutineScope emptyList()

        val perSeed = seedSongs.map { seed ->
            async {
                scoredAfter(context, userId, seed, timeoutMs = 6_000)
                    .filter { it.song.id !in heard && it.song.id !in profile.recentSkips }
                    .take(30)
                    // Strong songs usually first, but a different order on every refresh.
                    .let { list -> val pool = list.toMutableList(); buildList { while (pool.isNotEmpty()) add(Diversifier.sampleWeighted(pool, rnd) { it.final }!!.also { pool.remove(it) }) } }
            }
        }.awaitAll()

        val perArtist = HashMap<String, Int>()
        val seen = HashSet<String>()
        val mixed = ArrayList<Scored>()
        var i = 0
        while (perSeed.any { i < it.size } && mixed.size < limit) {
            perSeed.forEach { list ->
                val s = list.getOrNull(i) ?: return@forEach
                val artist = SongMatcher.artistKeys(s.song.artists).firstOrNull().orEmpty()
                if (s.song.id in seen || (perArtist[artist] ?: 0) >= 2) return@forEach
                seen += s.song.id
                perArtist[artist] = (perArtist[artist] ?: 0) + 1
                mixed += s
            }
            i++
        }
        Diversifier.spreadArtists(mixed.take(limit)) { it.song.artists }.map { SongBridge.toSongsModel(it.song) }
    }

    /**
     * Orders a list (a mood mix, a chart…) for this listener: the list's own order
     * still counts, then taste; [familiar] (0..1) pulls up songs you already play.
     */
    fun rerank(context: Context, songs: List<SongsModel>, familiar: Float): List<SongsModel> {
        if (songs.isEmpty()) return songs
        val userId = RecoDb.userId(context)
        val profile = Personalizer.profile(context, userId)
        val db = RecoDb.get(context)
        val n = songs.size.toDouble()
        return songs.mapIndexed { i, s ->
            val id = db.songBySpotifyId(s.spotifyTrackId)?.id
            val taste = profile.taste(SongBridge.toCandidate(s), id)
            val known = if (id != null && profile.songAffinity.containsKey(id)) 1.0 else 0.0
            s to ((1.0 - i / n) + taste * 1.2 + known * familiar * 0.5)
        }.sortedByDescending { it.second }.map { it.first }
            .let { list -> Diversifier.spreadArtists(list) { it.singer } }
    }
}

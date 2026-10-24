package com.music.aalap.data.recommendation.engine

import android.content.Context
import android.util.Log
import com.music.aalap.data.entity.SongsModel
import com.music.aalap.data.recommendation.engine.sources.JioSaavnRadioSource
import com.music.aalap.data.recommendation.engine.sources.LastFmSource
import com.music.aalap.data.recommendation.engine.sources.NextSongSource
import com.music.aalap.data.recommendation.engine.sources.SpotifyRadioSource
import com.music.aalap.data.recommendation.engine.sources.YouTubeMusicSource
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
                RecoTrace.log("  ↳ asking ${src.source} for songs after '${seed.title}'…")
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
        if (RecoTrace.on) {
            val all = db.songs(ranked.values.flatMap { it.take(6) } + fused.take(8).map { it.id })
            RecoTrace.log("  SOURCES (what each service plays after '${seed.title}'):")
            for ((src, ids) in ranked) RecoTrace.log("    $src → ${ids.size} songs\n" + RecoTrace.songs(ids.mapNotNull { all[it] }))
            if (ranked.isEmpty()) RecoTrace.log("    (no source answered in time)")
            RecoTrace.log("  FUSION (rank fusion: a song high on several lists rises; top 8 of ${fused.size}):")
            fused.take(8).forEachIndexed { i, f ->
                RecoTrace.log("    ${i + 1}. ${all[f.id]?.title ?: f.id}  " + "rrf=%.4f  from %s".format(f.score, f.sources.joinToString("+")))
            }
        }
        // The listener's own habits after this seed join the pool even if no source suggested them.
        val habitIds = db.transitionsFrom(userId, seed.id).map { it.toId }
        val songs = db.songs(fused.map { it.id } + habitIds)
        val fusedIds = fused.mapTo(HashSet()) { it.id }
        val pairs = fused.mapNotNull { f -> songs[f.id]?.let { it to f.score / top } } +
            habitIds.filter { it !in fusedIds }.mapNotNull { id -> songs[id]?.let { it to 0.0 } }
        val scored = Personalizer.score(context, userId, seed, pairs, fused.associate { it.id to it.sources })
            .sortedByDescending { it.final }
        if (RecoTrace.on) {
            RecoTrace.log("  PERSONAL (final = 0.6·global + 0.2·your taste + 0.2·your habit; top 8):")
            scored.take(8).forEachIndexed { i, s ->
                RecoTrace.log("    ${i + 1}. ${s.song.title} — ${s.song.artists.substringBefore(",")}  " + "global %.2f  taste %.2f  habit %.2f  → %.3f".format(s.global, s.taste, s.habit, s.final))
            }
        }
        return scored
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
        buildQueue(context, seed, length, excludeAppIds + seed.id).map {
            SongBridge.toSongsModel(it.scored.song).also { m -> com.music.aalap.data.recommendation.PlayContext.markAuto(m.id) }
        }

    /**
     * The listening session right now: the song you chose ([anchor], counts most), then
     * what you played in the last stretch of listening (finished songs count, skipped
     * ones count against their feel), newest first.
     */
    private fun buildSession(context: Context, userId: String, seed: Song, anchor: Song?): MutableList<SessionSong> {
        val db = RecoDb.get(context)
        val now = System.currentTimeMillis()
        val plays = db.plays(userId, sinceMs = now - 3 * 3_600_000L, limit = 15)
        val stretch = ArrayList<RecoDb.Play>()
        var last = now
        for (p in plays) { if (last - p.playedAt > 30 * 60_000L) break; stretch += p; last = p.playedAt }
        val songs = db.songs(stretch.map { it.songId } + listOfNotNull(anchor?.id, seed.id))
        val vibes = VibeStore.vibes(context, songs.values)
        val out = ArrayList<SessionSong>()
        fun add(song: Song, w: Double) = out.add(SessionSong(song.id, song.artists, vibes[song.id] ?: Vibe.UNKNOWN, VibeStore.language(context, song), w))
        anchor?.let { add(it, 1.2) }
        stretch.forEachIndexed { i, p ->
            if (p.songId == anchor?.id) return@forEachIndexed
            val s = songs[p.songId] ?: return@forEachIndexed
            val base = Math.pow(0.85, i.toDouble())
            // The newest play is still going (not finished or skipped yet).
            add(s, if (i == 0 && !p.skipped) 0.8 else if (p.skipped) -0.9 * base else 0.8 * base)
        }
        if (out.none { it.id == seed.id }) songs[seed.id]?.let { add(it, if (anchor == null) 1.0 else 0.6) }
        // "More / less like this" from the last two hours turn the session toward or away.
        com.music.aalap.data.recommendation.PlayContext.nudges().forEach { (model, w) ->
            val s = db.resolve(SongBridge.toCandidate(model))
            out.add(SessionSong(s.id, s.artists, VibeStore.vibe(context, s), VibeStore.language(context, s), w))
        }
        return out
    }

    /** 0..1 per song: how high it comes after the session's songs together (cached lists only, no waiting). */
    private fun sessionRanks(context: Context, seeds: List<Pair<String, Double>>): Map<String, Double> {
        val db = RecoDb.get(context)
        val total = HashMap<String, Double>()
        var weights = 0.0
        for ((id, w) in seeds) {
            val ranked = Source.entries.mapNotNull { src -> db.cachedList(id, src, CACHE_TTL_MS)?.takeIf { it.isNotEmpty() }?.let { src to it } }.toMap()
            if (ranked.isEmpty()) continue
            val fused = Fusion.fuse(ranked)
            val top = fused.firstOrNull()?.score?.takeIf { it > 0 } ?: continue
            fused.forEach { total[it.id] = (total[it.id] ?: 0.0) + w * it.score / top }
            weights += w
        }
        return if (weights == 0.0) emptyMap() else total.mapValues { it.value / weights }
    }

    suspend fun chain(
        context: Context,
        userId: String,
        seed: Song,
        length: Int,
        excludeAppIds: Set<Int>,
        onPick: suspend (QueueItem) -> Unit,
    ) {
        val profile = Personalizer.profile(context, userId)
        VibeStore.learnInBackground(context)
        val db = RecoDb.get(context)
        val now = System.currentTimeMillis()
        val excluded = HashSet<String>(profile.recentIds + profile.recentSkips)
        excluded += seed.id

        // The song you chose, while it's still this queue's (you haven't started something else).
        val play = com.music.aalap.data.recommendation.PlayContext.current
        val anchorModel = play.anchor?.takeIf { it.id in excludeAppIds && now - play.at < 4 * 3_600_000L }
        val anchor = anchorModel?.let { db.resolve(SongBridge.toCandidate(it)) }
        val pool = if (anchorModel != null) play.pool.filter { it.id !in excludeAppIds } else emptyList()
        val poolSongs = if (pool.isEmpty()) emptyList() else db.resolveAll(pool.map(SongBridge::toCandidate))
        val poolIds = poolSongs.mapTo(HashSet()) { it.id }

        val sessionSongs = buildSession(context, userId, seed, anchor)
        val trending = VibeStore.trendingIds(context)
        val favouriteIds = profile.songAffinity.entries.sortedByDescending { it.value }.take(40).map { it.key }
        var state = PickState(
            recentArtists = sessionSongs.mapNotNull { SongMatcher.artistKeys(it.artists).firstOrNull() },
            lastVibe = sessionSongs.firstOrNull { it.id == seed.id }?.vibe ?: VibeStore.vibe(context, seed),
        )
        // Your settings: the discovery dial and the language mix; and your feedback.
        val dial = com.music.aalap.data.preferences.RecoPrefs.discovery(context)
        val feedback = Feedback.snapshot(context, userId)
        val targets = Targets(
            familiar = if (profile.isNew) 0.0 else dial.familiar ?: profile.familiarTarget,
            maxLiked = 0.35,
            fromList = if (poolSongs.isNotEmpty()) 0.35 else 0.0,
            rediscover = if (dial == com.music.aalap.data.preferences.RecoPrefs.Discovery.SURPRISE) 0.05 else 0.1,
            surprise = if (profile.isNew) 0.0 else dial.surprise,
            languageMix = com.music.aalap.data.preferences.RecoPrefs.languageMix(context),
        )
        val rediscoverIds = profile.rediscover.toList()
        // Your language mix: what plays after the seed is mostly in its own language, so
        // the other languages you asked for come from a radio of each in this feel (fetched
        // alongside; joins from the pick it's ready for).
        val mix = targets.languageMix
        val langRadio = if (mix.isEmpty()) null else scope.async { languageRadio(context, mix, state.lastVibe ?: Vibe.UNKNOWN) }
        RecoTrace.log("━━━━ UP NEXT after '${seed.title}' — ${seed.artists} ━━━━")
        RecoTrace.log("  session: ${sessionSongs.size} songs, feel ${Session(sessionSongs, profile.dayPartFeel).vibe}; " +
            "aim ${"%.0f".format(targets.familiar * 100)}% known songs" + (if (poolSongs.isNotEmpty()) ", ${"%.0f".format(targets.fromList * 100)}% from '${play.poolName}'" else ""))
        val rnd = Random()
        var cur = seed
        for (step in 0 until length) {
            RecoTrace.log("\n── Pick #${step + 1}: what comes after '${cur.title}'? ──")
            var ranked = ranked(context, cur, if (step == 0) SOURCE_TIMEOUT_MS else BACKGROUND_TIMEOUT_MS)
            if (ranked.isEmpty() && step == 0) ranked = ranked(context, cur, FIRST_PICK_GRACE_MS)
            val fused = Fusion.fuse(ranked).take(CANDIDATE_POOL)
            val top = fused.firstOrNull()?.score?.takeIf { it > 0 } ?: 1.0
            val habits = db.transitionsFrom(userId, cur.id).associateBy { it.toId }

            // What plays after the session as a whole: the song you chose and the last two picks.
            val sessionSeeds = buildList {
                anchor?.let { add(it.id to 1.0) }
                sessionSongs.filter { it.weight > 0 && it.id != anchor?.id }.takeLast(2).forEach { add(it.id to 0.5) }
                if (isEmpty()) add(cur.id to 1.0)
            }
            val sessionRank = sessionRanks(context, sessionSeeds)

            // Candidates: what the world plays next, your habits, and (only if they fit)
            // the playlist you started from, your all-time favourites and today's trending songs.
            val langPool = langRadio?.takeIf { it.isCompleted && !it.isCancelled }?.let { runCatching { it.await() }.getOrNull() }.orEmpty()
            val globalOf = langPool + fused.associate { it.id to it.score / top }
            val sourcesOf = fused.associate { it.id to it.sources }
            val extraPool = poolSongs.filter { it.id !in excluded }.shuffled(rnd).take(30)
            val extraIds = favouriteIds + trending.shuffled(rnd).take(25) + rediscoverIds.shuffled(rnd).take(20)
            // What plays after the session as a whole stays in the running at every step, so one
            // off pick can't drag the queue somewhere else (all candidates would follow it).
            val sessionIds = sessionRank.entries.sortedByDescending { it.value }.take(30).map { it.key }
            val songs = HashMap(db.songs(globalOf.keys + habits.keys + extraIds + sessionIds))
            extraPool.forEach { songs[it.id] = it }
            val vibes = VibeStore.vibes(context, songs.values)
            val session = Session(sessionSongs, profile.dayPartFeel)
            val features = songs.values.mapNotNull { s ->
                if (s.id in excluded || SongBridge.appId(s) in excludeAppIds || feedback.blocked(s)) return@mapNotNull null
                val v = vibes[s.id] ?: Vibe.UNKNOWN
                val global = globalOf[s.id] ?: 0.0
                val habit = habits[s.id]?.let { TasteProfile.habit(it.count, it.lastAt, now) } ?: 0.0
                val fromList = s.id in poolIds
                val lang = VibeStore.language(context, s)
                // Songs nobody suggested after this one only join if they fit the session (feel and language).
                if (global == 0.0 && habit == 0.0 && (sessionRank[s.id] ?: 0.0) < 0.3 &&
                    (session.vibeScore(v) < (if (fromList) 0.5 else 0.62) || session.languageScore(lang) < 0.5)) return@mapNotNull null
                // A language you turned off in your mix doesn't play (unless it's all there is).
                if (mix.isNotEmpty() && (mix[lang] ?: 0.0) <= 0.0 && lang.isNotBlank() && global < 0.5) return@mapNotNull null
                Features(
                    song = s, global = global, session = sessionRank[s.id] ?: 0.0, vibe = v,
                    language = lang, taste = profile.taste(s.info, s.id, v), habit = habit,
                    trend = s.id in trending, familiar = s.id in profile.heard, liked = s.id in profile.liked,
                    fromList = fromList, sources = sourcesOf[s.id].orEmpty(),
                    rediscover = s.id in profile.rediscover,
                    // A surprise: new to you, by an artist you don't usually play, and it fits.
                    surprise = !profile.isNew && s.id !in profile.heard && s.id !in profile.liked &&
                        SongMatcher.artistKeys(s.artists).firstOrNull() !in profile.usualArtists && session.vibeScore(v) >= 0.65,
                    feedback = feedback.factor(s, v),
                )
            }
            if (RecoTrace.on) {
                RecoTrace.log("  ${fused.size} songs the world plays next, ${habits.size} habits, ${features.size} candidates in all")
                features.map { QueuePicker.score(it, session, profile.isNew, state, targets) }.sortedByDescending { it.final }.take(8).forEachIndexed { i, sc ->
                    RecoTrace.log("    ${i + 1}. ${sc.song.title} — ${sc.song.artists.substringBefore(",")}  " +
                        "world %.2f  session %.2f  fit %.2f  taste %.2f  habit %.2f%s → %.3f".format(
                            sc.global, sc.session, sc.fit, sc.taste, sc.habit, if (sc.trend > 0) "  trending" else "", sc.final) +
                        "  ${sc.vibe}  ${sc.tags.joinToString(" ")}")
                }
            }
            val picked = QueuePicker.pick(features, session, profile.isNew, state, targets, cur.artists, excluded, rnd)
            val pick = picked?.let {
                val f = features.first { f -> f.song.id == it.song.id }
                state = state.after(f)
                QueueItem(it, when {
                    f.fromList && f.global == 0.0 -> "your list"
                    it.global == 0.0 && it.session > 0 -> "session"
                    it.global == 0.0 && it.habit > 0 -> "habit"
                    it.global == 0.0 && f.trend -> "trending"
                    it.global == 0.0 -> "your favourites"
                    else -> "fusion"
                })
            } ?: fallback(context, userId, cur, excluded, excludeAppIds, rnd)
            if (pick == null) {
                Log.w(TAG, "no next song after '${cur.title}'")
                return
            }
            RecoTrace.log("  ✔ PICKED #${step + 1}: '${pick.scored.song.title}' — ${pick.scored.song.artists.substringBefore(",")}  " +
                "(via ${pick.via}, score %.3f, ".format(pick.scored.final) + "${pick.scored.vibe})")
            Log.d(TAG, "#${step + 1} '${pick.scored.song.title}' via ${pick.via} " +
                "(world %.2f session %.2f fit %.2f taste %.2f habit %.2f → %.3f, ".format(
                    pick.scored.global, pick.scored.session, pick.scored.fit, pick.scored.taste, pick.scored.habit, pick.scored.final) + "${pick.scored.vibe})")
            onPick(pick)
            excluded += pick.scored.song.id
            val pv = pick.scored.vibe.takeIf { it.known } ?: VibeStore.vibe(context, pick.scored.song)
            sessionSongs += SessionSong(pick.scored.song.id, pick.scored.song.artists, pv, VibeStore.language(context, pick.scored.song), 0.25)
            cur = pick.scored.song
            prefetch(context, cur) // next seed's sources load while this song is queued
        }
    }

    /** No source answered: your own transitions after the seed, then the artist's top tracks. */
    /**
     * Songs in each language of your mix (up to three) that fit [feel]: what plays after
     * a top search hit for "<feel> <language> songs". Canonical id → score (0..0.6), a
     * bit under what the seed's own sources score so the session still leads.
     */
    private suspend fun languageRadio(context: Context, mix: Map<String, Double>, feel: Vibe): Map<String, Double> = coroutineScope {
        val db = RecoDb.get(context)
        val mood = feel.takeIf { it.known }?.dominant?.label.orEmpty()
        mix.filterValues { it > 0 }.keys.take(3).map { lang ->
            async {
                runCatching {
                    withTimeoutOrNull(8_000) {
                        val hit = com.music.aalap.data.api.Api(context).searchCandidate("$mood $lang songs".trim()) ?: return@withTimeoutOrNull emptyMap()
                        val start = db.resolve(hit)
                        val fused = Fusion.fuse(ranked(context, start, BACKGROUND_TIMEOUT_MS)).take(30)
                        val top = fused.firstOrNull()?.score?.takeIf { it > 0 } ?: 1.0
                        fused.associate { it.id to 0.6 * it.score / top } + (start.id to 0.5)
                    }
                }.getOrNull().orEmpty()
            }
        }.awaitAll().fold(HashMap<String, Double>()) { acc, m -> m.forEach { (k, v) -> acc[k] = maxOf(acc[k] ?: 0.0, v) }; acc }
    }

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

        val top = runCatching { com.music.aalap.data.api.Api(context).artistTopCandidates(seed.artists.substringBefore(",").trim()) }
            .getOrDefault(emptyList())
        if (top.isEmpty()) return null
        val songs = db.resolveAll(top)
        val n = songs.size.toDouble()
        val scored = Personalizer.score(context, userId, seed, songs.mapIndexed { i, s -> s to (1.0 - i / n) })
            .filter { SongBridge.appId(it.song) !in excludeAppIds }
        return Diversifier.pick(scored, "", excluded, rnd)?.let { QueueItem(it, "artist-top") }
    }

    // ── recording what the listener does ───────────────────────────────────

    /**
     * A song started playing: log it (skips, habits), refresh the taste and warm its
     * "next" lists. If the song before it was one the engine queued and you skipped it,
     * [onSkippedAuto] gets the upcoming queued songs that are too much like it (same
     * artist, or the same feel and tempo), so the queue can drop them and rethink.
     */
    fun onPlay(
        context: Context,
        song: SongsModel,
        at: Long = System.currentTimeMillis(),
        upcoming: List<SongsModel> = emptyList(),
        onSkippedAuto: ((Set<Int>) -> Unit)? = null,
    ) {
        if (song.title.isBlank()) return
        scope.launch {
            runCatching {
                ensureImported(context)
                val db = RecoDb.get(context)
                val userId = RecoDb.userId(context)
                val s = db.resolve(SongBridge.toCandidate(song))
                db.recordPlay(userId, s.id, at)
                Personalizer.invalidate()
                prefetch(context, s)
                val prev = db.plays(userId, limit = 2).getOrNull(1)
                if (prev != null && prev.skipped && onSkippedAuto != null) {
                    val skipped = db.song(prev.songId) ?: return@runCatching
                    if (!com.music.aalap.data.recommendation.PlayContext.isAuto(SongBridge.appId(skipped))) return@runCatching
                    val drop = tooLike(context, skipped, upcoming.filter { com.music.aalap.data.recommendation.PlayContext.isAuto(it.id) })
                    if (drop.isNotEmpty()) {
                        Log.d(TAG, "skipped '${skipped.title}': dropping ${drop.size} similar queued songs")
                        onSkippedAuto(drop)
                    }
                }
            }.onFailure { Log.w(TAG, "onPlay failed: $it") }
        }
    }

    /** Of [upcoming], the songs too much like [skipped] (same main artist, or same feel and tempo); at most 3. */
    fun tooLike(context: Context, skipped: Song, upcoming: List<SongsModel>): Set<Int> {
        if (upcoming.isEmpty()) return emptySet()
        val db = RecoDb.get(context)
        val sv = VibeStore.vibe(context, skipped)
        val artist = SongMatcher.artistKeys(skipped.artists).firstOrNull()
        val songs = upcoming.map { it to db.resolve(SongBridge.toCandidate(it)) }
        val vibes = VibeStore.vibes(context, songs.map { it.second })
        return songs.filter { (_, s) ->
            val v = vibes[s.id] ?: Vibe.UNKNOWN
            SongMatcher.artistKeys(s.artists).firstOrNull() == artist ||
                (sv.known && v.known && v.similarity(sv) >= 0.85 && kotlin.math.abs(v.energy - sv.energy) <= 0.12)
        }.take(3).mapTo(HashSet()) { it.first.id }
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
        // Plays logged before an email was set belong to this phone's one listener.
        RecoDb.userId(context).takeIf { it != "local" }?.let { me ->
            runCatching { RecoDb.get(context).adoptUser("local", me) }
            Personalizer.invalidate()
        }
        val prefs = context.getSharedPreferences("reco_engine", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("history_imported", false)) {
            runCatching {
                val history = com.music.aalap.data.preferences.getListeningHistory(context)
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
            // Lists stay two weeks (they also say what each song sounds like); fetching still refreshes after 24 h.
            runCatching { RecoDb.get(context).pruneCache(14 * CACHE_TTL_MS) }
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

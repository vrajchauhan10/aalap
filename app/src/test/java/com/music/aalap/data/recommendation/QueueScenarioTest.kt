package com.music.aalap.data.recommendation

import com.music.aalap.data.recommendation.engine.Candidate
import com.music.aalap.data.recommendation.engine.Diversifier
import com.music.aalap.data.recommendation.engine.Features
import com.music.aalap.data.recommendation.engine.Feel
import com.music.aalap.data.recommendation.engine.Fusion
import com.music.aalap.data.recommendation.engine.PickState
import com.music.aalap.data.recommendation.engine.QueuePicker
import com.music.aalap.data.recommendation.engine.Scored
import com.music.aalap.data.recommendation.engine.Session
import com.music.aalap.data.recommendation.engine.SessionSong
import com.music.aalap.data.recommendation.engine.Song
import com.music.aalap.data.recommendation.engine.Source
import com.music.aalap.data.recommendation.engine.Targets
import com.music.aalap.data.recommendation.engine.TasteProfile
import com.music.aalap.data.recommendation.engine.Vibe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Random
import kotlin.math.abs

/**
 * Scenario tests for the queue: a simulated world of 15 genres (Punjabi party,
 * Bollywood romantic, sufi, devotional, lofi, English pop, Telugu, garba…) and a
 * listener whose likes and plays lean heavily to romantic Bollywood (like a real
 * library). Each scenario runs the OLD engine (0.6·world + 0.2·taste + 0.2·habit,
 * chained from the last song) and the NEW one ([QueuePicker] with the session, feel,
 * trends and mix rules) many times, and checks what the queue is made of.
 *
 * The world is realistic about drift: after any song, the "what plays next" lists
 * contain the same genre, the same language in other feels, and a pull of popular
 * romantic Bollywood songs, which is what turned a party start into ballads.
 *
 * Results are printed and written to build/reports/reco-scenarios.md.
 */
class QueueScenarioTest {

    // ── the simulated world ─────────────────────────────────────────────────

    data class Genre(val name: String, val label: String, val main: Feel, val second: Feel, val language: String, val style: String = "")

    private val genres = listOf(
        Genre("punjabi_party", "Punjabi party", Feel.PARTY, Feel.ENERGETIC, "punjabi"),
        Genre("punjabi_romantic", "Punjabi romantic", Feel.ROMANTIC, Feel.SAD, "punjabi"),
        Genre("punjabi_rap", "Punjabi rap", Feel.ENERGETIC, Feel.PARTY, "punjabi", "rap"),
        Genre("bollywood_romantic", "Bollywood romantic", Feel.ROMANTIC, Feel.SAD, "hindi"),
        Genre("bollywood_sad", "Bollywood sad", Feel.SAD, Feel.ROMANTIC, "hindi"),
        Genre("bollywood_party", "Bollywood party", Feel.PARTY, Feel.HAPPY, "hindi"),
        Genre("sufi", "Sufi", Feel.DEVOTIONAL, Feel.ROMANTIC, "hindi", "sufi"),
        Genre("devotional", "Devotional / bhajan", Feel.DEVOTIONAL, Feel.CHILL, "hindi", "devotional"),
        Genre("lofi", "Lofi / chill indie", Feel.CHILL, Feel.ROMANTIC, "hindi", "lofi"),
        Genre("hindi_hiphop", "Hindi hip-hop", Feel.ENERGETIC, Feel.PARTY, "hindi", "rap"),
        Genre("english_pop", "English pop", Feel.HAPPY, Feel.ROMANTIC, "english"),
        Genre("english_workout", "English rock / workout", Feel.ENERGETIC, Feel.HAPPY, "english"),
        Genre("garba", "Gujarati garba", Feel.PARTY, Feel.DEVOTIONAL, "gujarati", "folk"),
        Genre("telugu_mass", "Telugu mass", Feel.ENERGETIC, Feel.PARTY, "telugu"),
        Genre("focus", "Instrumental / focus", Feel.FOCUS, Feel.CHILL, "", "instrumental"),
    )
    private val pullGenre = "bollywood_romantic"

    data class WSong(val song: Song, val genre: Genre, val vibe: Vibe, val artist: String, val trending: Boolean)

    private val rnd0 = Random(42)
    private val world: List<WSong> = genres.flatMap { g ->
        (0 until 60).map { i ->
            val artist = "${g.name}_artist_${i % 7}"
            // Most songs' feel is known; some only roughly, a few not at all (like real data).
            val known = rnd0.nextDouble()
            val vibe = when {
                known < 0.12 -> Vibe.UNKNOWN
                else -> {
                    val w = mutableMapOf(g.main to 0.6 + rnd0.nextDouble() * 0.25)
                    w[g.second] = (w[g.second] ?: 0.0) + 0.3
                    if (rnd0.nextDouble() < 0.3) Feel.entries.random(rnd0).let { w[it] = (w[it] ?: 0.0) + 0.15 }
                    Vibe.of(w.mapValues { it.value * (1.0 + known * 2.5) }, if (g.style.isBlank()) emptySet() else setOf(g.style))
                }
            }
            val id = "${g.name}:$i"
            // Tempo and measured energy around the genre's own, with spread (some songs calmer, some faster).
            val baseBpm = mapOf(Feel.PARTY to 124.0, Feel.ENERGETIC to 138.0, Feel.HAPPY to 112.0, Feel.ROMANTIC to 86.0,
                Feel.SAD to 74.0, Feel.CHILL to 82.0, Feel.DEVOTIONAL to 92.0, Feel.FOCUS to 70.0).getValue(g.main)
            val baseE = mapOf(Feel.PARTY to 0.85, Feel.ENERGETIC to 0.9, Feel.HAPPY to 0.7, Feel.ROMANTIC to 0.45,
                Feel.SAD to 0.3, Feel.CHILL to 0.35, Feel.DEVOTIONAL to 0.4, Feel.FOCUS to 0.25).getValue(g.main)
            val v2 = vibe.withAudio((baseE + (rnd0.nextDouble() - 0.5) * 0.5).coerceIn(0.05, 1.0), baseBpm * (0.85 + rnd0.nextDouble() * 0.3))
            WSong(Song(id, Candidate(title = "${g.label} song $i", artists = artist, language = g.language)), g, v2, artist, rnd0.nextDouble() < 0.08)
        }
    }
    private val byId = world.associateBy { it.song.id }
    private val byGenre = world.groupBy { it.genre.name }

    private fun <T> List<T>.random(r: Random) = this[r.nextInt(size)]

    /** What each service plays after [seed]: same genre, same language, the popular pull, a few random. */
    private val nextCache = HashMap<String, Map<Source, List<String>>>()
    private fun nextLists(seed: WSong): Map<Source, List<String>> = nextCache.getOrPut(seed.song.id) {
        val r = Random(seed.song.id.hashCode().toLong())
        fun list(): List<String> {
            val same = byGenre.getValue(seed.genre.name).shuffled(r).take(18)
            val lang = world.filter { it.genre.language == seed.genre.language && it.genre != seed.genre && seed.genre.language.isNotBlank() }.shuffled(r).take(8)
            val pull = byGenre.getValue(pullGenre).shuffled(r).take(9)
            val any = world.shuffled(r).take(5)
            // Same genre ranks higher on average, the popular pull close behind.
            return (same + lang + pull + any).distinctBy { it.song.id }.map { s ->
                s to when { s.genre == seed.genre -> r.nextDouble() * 1.0; s.genre.name == pullGenre -> r.nextDouble() * 1.2; else -> 0.4 + r.nextDouble() * 1.2 }
            }.sortedBy { it.second }.map { it.first.song.id }.take(40)
        }
        mapOf(Source.YOUTUBE to list(), Source.SPOTIFY to list(), Source.JIOSAAVN to list())
    }

    private fun fused(seed: WSong): Map<String, Double> {
        val f = Fusion.fuse(nextLists(seed)).take(40)
        val top = f.firstOrNull()?.score ?: 1.0
        return f.associate { it.id to it.score / top }
    }

    // ── the listener: likes and plays lean to romantic Bollywood ────────────

    private val likedIds: Set<String> = (byGenre.getValue("bollywood_romantic").take(50) + byGenre.getValue("bollywood_sad").take(30) +
        byGenre.getValue("punjabi_romantic").take(15) + byGenre.getValue("sufi").take(10)).map { it.song.id }.toSet()
    private val playedIds: Map<String, Int> = buildMap {
        byGenre.getValue("bollywood_romantic").take(40).forEach { put(it.song.id, 6) }
        byGenre.getValue("bollywood_sad").take(20).forEach { put(it.song.id, 4) }
        byGenre.getValue("punjabi_party").take(15).forEach { put(it.song.id, 3) }
        byGenre.getValue("english_pop").take(10).forEach { put(it.song.id, 2) }
        byGenre.getValue("hindi_hiphop").take(8).forEach { put(it.song.id, 2) }
    }

    /** Artist weights the way each engine builds them (old: +1.5 per like; new: +0.5, at most 3 per artist). */
    private fun artistWeights(oldRule: Boolean): Map<String, Double> {
        val a = HashMap<String, Double>()
        playedIds.forEach { (id, n) -> val ar = byId.getValue(id).artist; a[ar] = (a[ar] ?: 0.0) + n }
        val fromLikes = HashMap<String, Double>()
        likedIds.forEach { id -> val ar = byId.getValue(id).artist; fromLikes[ar] = (fromLikes[ar] ?: 0.0) + (if (oldRule) 1.5 else 0.5) }
        fromLikes.forEach { (ar, v) -> a[ar] = (a[ar] ?: 0.0) + if (oldRule) v else minOf(3.0, v) }
        return TasteProfile.normalise(a.mapKeys { com.music.aalap.data.recommendation.engine.SongMatcher.artistKeys(it.key).first() })
    }

    private val oldArtist = artistWeights(oldRule = true)
    private val newProfile = TasteProfile(
        artistWeight = artistWeights(oldRule = false), skipRate = emptyMap(),
        songAffinity = TasteProfile.normalise(playedIds.mapValues { it.value.toDouble() }),
        liked = likedIds, followed = emptySet(), language = mapOf("hindi" to 1.0, "punjabi" to 0.5, "english" to 0.3),
        dayPart = emptyMap(), playCount = 400, recentIds = emptySet(), recentSkips = emptySet(),
        feelPref = mapOf(Feel.ROMANTIC to 1.0, Feel.SAD to 0.9, Feel.PARTY to 0.8, Feel.ENERGETIC to 0.75, Feel.HAPPY to 0.8,
            Feel.DEVOTIONAL to 0.6, Feel.CHILL to 0.7, Feel.FOCUS to 0.5),
        familiarTarget = 0.35, heard = playedIds.keys,
    )

    /** The old taste score (artist + likes + replays; likes counted a lot). */
    private fun oldTaste(s: WSong): Double {
        val key = com.music.aalap.data.recommendation.engine.SongMatcher.artistKeys(s.artist).first()
        val parts = listOf(0.35 to (oldArtist[key] ?: 0.0), 0.15 to (if (s.song.id in likedIds) 1.0 else 0.0),
            0.10 to (newProfile.songAffinity[s.song.id] ?: 0.0), 0.15 to (if (s.genre.language == "hindi") 1.0 else if (s.genre.language == "punjabi") 0.5 else 0.2))
        return parts.sumOf { it.first * it.second } / parts.sumOf { it.first }
    }

    // ── the two engines ─────────────────────────────────────────────────────

    private fun oldQueue(seed: WSong, length: Int, r: Random): List<WSong> {
        val out = ArrayList<WSong>()
        val excluded = hashSetOf(seed.song.id)
        var cur = seed
        repeat(length) {
            val scored = fused(cur).map { (id, g) ->
                val s = byId.getValue(id)
                val t = oldTaste(s)
                Scored(s.song, g, t, 0.0, TasteProfile.final(g, t, 0.0, isNew = false))
            }
            val pick = Diversifier.pick(scored, cur.artist, excluded, r) ?: return out
            val ws = byId.getValue(pick.song.id)
            out += ws; excluded += ws.song.id; cur = ws
        }
        return out
    }

    private fun newQueue(
        seed: WSong,
        length: Int,
        r: Random,
        isNew: Boolean = false,
        pool: List<WSong> = emptyList(),
        skipped: List<WSong> = emptyList(),
        onPick: (WSong) -> Unit = {},
        tune: (Targets) -> Targets = { it },
        feedback: com.music.aalap.data.recommendation.engine.Feedback.Snapshot = com.music.aalap.data.recommendation.engine.Feedback.Snapshot(),
        rediscoverIds: Set<String> = emptySet(),
        flow: Boolean = true,
    ): List<WSong> {
        val out = ArrayList<WSong>()
        val excluded = hashSetOf(seed.song.id)
        val session = ArrayList<SessionSong>()
        session += SessionSong(seed.song.id, seed.artist, seed.vibe, seed.genre.language, 1.2)
        skipped.forEach { session += SessionSong(it.song.id, it.artist, it.vibe, it.genre.language, -0.9) }
        val profile = if (isNew) TasteProfile.EMPTY else newProfile
        var state = PickState(recentArtists = listOf(com.music.aalap.data.recommendation.engine.SongMatcher.artistKeys(seed.artist).first()), lastVibe = seed.vibe)
        val targets = tune(Targets(familiar = if (isNew) 0.0 else profile.familiarTarget, maxLiked = 0.35, fromList = if (pool.isEmpty()) 0.0 else 0.35, surprise = if (isNew) 0.0 else 0.1))
        val poolIds = pool.map { it.song.id }.toSet()
        val favourites = profile.songAffinity.entries.sortedByDescending { it.value }.take(40).map { it.key }
        val trending = world.filter { it.trending }
        var cur = seed
        repeat(length) {
            val global = fused(cur)
            val sessionSeeds = listOf(seed to 1.0) + out.takeLast(2).map { it to 0.5 }
            val sessionRank = HashMap<String, Double>()
            sessionSeeds.forEach { (s, w) -> fused(s).forEach { (id, v) -> sessionRank[id] = (sessionRank[id] ?: 0.0) + w * v } }
            val wsum = sessionSeeds.sumOf { it.second }
            val sess = Session(session)
            val sessionIds = sessionRank.entries.sortedByDescending { it.value }.take(30).map { it.key }
            val ids = global.keys + pool.shuffled(r).take(30).map { it.song.id } + favourites + trending.shuffled(r).take(25).map { it.song.id } + sessionIds + rediscoverIds.shuffled(r).take(20)
            val features = ids.distinct().mapNotNull { id ->
                val s = byId.getValue(id)
                val g = global[id] ?: 0.0
                val fromList = id in poolIds
                val sr = (sessionRank[id] ?: 0.0) / wsum
                if (feedback.blocked(s.song)) return@mapNotNull null
                if (g == 0.0 && sr < 0.3 && (sess.vibeScore(s.vibe) < (if (fromList) 0.5 else 0.62) || sess.languageScore(s.genre.language) < 0.5)) return@mapNotNull null
                Features(
                    song = s.song, global = g, session = (sessionRank[id] ?: 0.0) / wsum, vibe = s.vibe, language = s.genre.language,
                    taste = profile.taste(s.song.info, id, s.vibe), habit = 0.0, trend = s.trending,
                    familiar = id in profile.heard, liked = id in profile.liked, fromList = fromList,
                    rediscover = id in rediscoverIds,
                    surprise = !isNew && id !in profile.heard && id !in profile.liked &&
                        com.music.aalap.data.recommendation.engine.SongMatcher.artistKeys(s.artist).first() !in profile.usualArtists && sess.vibeScore(s.vibe) >= 0.65,
                    feedback = feedback.factor(s.song, s.vibe),
                )
            }
            val pick = QueuePicker.pick(features, sess, isNew, if (flow) state else state.copy(lastVibe = null), targets, cur.artist, excluded, r) ?: return out
            val ws = byId.getValue(pick.song.id)
            state = state.after(features.first { it.song.id == ws.song.id })
            out += ws; excluded += ws.song.id; cur = ws
            session += SessionSong(ws.song.id, ws.artist, ws.vibe, ws.genre.language, 0.25)
            onPick(ws)
        }
        return out
    }

    // ── measuring ───────────────────────────────────────────────────────────

    data class Stats(val feel: Double, val energyGap: Double, val language: Double, val liked: Double, val known: Double, val sameArtistInRow: Int, val trending: Double)

    private fun stats(seed: WSong, queues: List<List<WSong>>): Stats {
        val picks = queues.flatten()
        val n = picks.size.toDouble().coerceAtLeast(1.0)
        val seedFeel = seed.genre.main
        val seedEnergy = Vibe.of(mapOf(seed.genre.main to 0.7, seed.genre.second to 0.3)).energy
        return Stats(
            feel = picks.count { it.genre.main == seedFeel || it.genre.second == seedFeel && it.genre.main == seed.genre.second } / n,
            energyGap = picks.sumOf { abs(Vibe.of(mapOf(it.genre.main to 0.7, it.genre.second to 0.3)).energy - seedEnergy) } / n,
            language = picks.count { it.genre.language == seed.genre.language } / n,
            liked = picks.count { it.song.id in likedIds } / n,
            known = picks.count { it.song.id in playedIds } / n,
            sameArtistInRow = queues.sumOf { q -> (listOf(seed) + q).zipWithNext().count { (a, b) -> a.artist == b.artist } },
            trending = picks.count { it.trending } / n,
        )
    }

    private val report = StringBuilder()
    private val softFeel = LinkedHashMap<String, Double>()
    private fun line(s: String) { println(s); report.appendLine(s) }
    private fun pct(x: Double) = "%3.0f%%".format(x * 100)

    private fun seedOf(genre: String, r: Random) = byGenre.getValue(genre).filter { it.vibe.known }.random(r)

    // ── the scenarios ───────────────────────────────────────────────────────

    @Test
    fun everyGenreKeepsItsFeel() {
        val runs = 60
        line("## Every genre: 10 songs after a seed ($runs runs each)\n")
        line("Same feel = the pick has the seed's main feel. Energy gap = how far its tempo is from the seed's (0 = same).\n")
        line("| Seed genre | Same feel (old → new) | Energy gap (old → new) | Same language (old → new) | Liked songs (old → new) | Same artist twice in a row (old → new) |")
        line("|---|---|---|---|---|---|")
        var oldFeel = 0.0; var newFeel = 0.0
        val problems = ArrayList<String>()
        for (g in genres) {
            val r = Random(g.name.hashCode().toLong())
            val oldQ = ArrayList<List<WSong>>(); val newQ = ArrayList<List<WSong>>()
            val seeds = (0 until runs).map { seedOf(g.name, r) }
            seeds.forEach { s -> oldQ += oldQueue(s, 10, Random(r.nextLong())) }
            seeds.forEach { s -> newQ += newQueue(s, 10, Random(r.nextLong())) }
            val o = seeds.indices.map { stats(seeds[it], listOf(oldQ[it])) }.average()
            val n = seeds.indices.map { stats(seeds[it], listOf(newQ[it])) }.average()
            oldFeel += o.feel; newFeel += n.feel
            val gap = "%.2f → **%.2f**".format(o.energyGap, n.energyGap)
            line("| ${g.label} | ${pct(o.feel)} → **${pct(n.feel)}** | $gap | ${pct(o.language)} → **${pct(n.language)}** | ${pct(o.liked)} → **${pct(n.liked)}** | ${o.sameArtistInRow} → **${n.sameArtistInRow}** |")
            if (n.feel < 0.62) problems += "${g.label}: keeps its feel only ${pct(n.feel)}"
            if (n.feel < o.feel - 0.05) problems += "${g.label}: worse than old on feel"
            // (A genre that's mostly in your likes can go a little over the 35% target.)
            if (n.liked > 0.42) problems += "${g.label}: liked songs ${pct(n.liked)}"
            if (n.sameArtistInRow > 0) problems += "${g.label}: same artist twice in a row"
        }
        line("\n**Average same feel: old ${pct(oldFeel / genres.size)} → new ${pct(newFeel / genres.size)}**\n")
        save()
        assertTrue(problems.joinToString("; "), problems.isEmpty())
        assertTrue(newFeel / genres.size >= oldFeel / genres.size + 0.15)
    }

    private fun List<Stats>.average() = Stats(
        sumOf { it.feel } / size, sumOf { it.energyGap } / size, sumOf { it.language } / size, sumOf { it.liked } / size,
        sumOf { it.known } / size, sumOf { it.sameArtistInRow }, sumOf { it.trending } / size,
    )

    @Test
    fun tappingASongInLikedSongsMixesInsteadOfPlayingTheList() {
        line("## Tapping a song in Liked Songs\n")
        val r = Random(7)
        val liked = world.filter { it.song.id in likedIds }
        var fromList = 0.0; var fits = 0.0; var total = 0
        repeat(60) {
            val tapped = liked.filter { it.vibe.known }.random(r)
            val q = newQueue(tapped, 10, Random(r.nextLong()), pool = liked)
            fromList += q.count { it.song.id in likedIds }
            fits += q.count { it.genre.main == tapped.genre.main || it.genre.second == tapped.genre.main }
            total += q.size
        }
        val share = fromList / total
        line("Old: the next 10 songs were the rest of Liked Songs, in order (100% from the list).")
        line("New: ${pct(share)} from Liked Songs (picked at random among the ones that fit), the rest recommendations; " +
            "${pct(fits / total)} share the tapped song's feel.\n")
        assertTrue("some liked songs mixed in (${pct(share)})", share >= 0.15)
        assertTrue("but not the whole list (${pct(share)})", share <= 0.55)
        save()
    }

    @Test
    fun skippingSadSongsSteersAway() {
        line("## Skipping steers the queue\n")
        val r = Random(11)
        var with = 0.0; var without = 0.0; var n = 0
        repeat(60) {
            // A sad start where the listener then skipped two sad songs: they want out of sad.
            val seed = seedOf("bollywood_sad", r)
            val sad = byGenre.getValue("bollywood_sad").filter { it.vibe.known }.shuffled(r).take(2)
            val a = newQueue(seed, 10, Random(r.nextLong()), skipped = sad)
            val b = newQueue(seed, 10, Random(r.nextLong()))
            with += a.count { it.genre.main == Feel.SAD }; without += b.count { it.genre.main == Feel.SAD }; n += 10
        }
        line("Sad songs after a sad start: ${pct(without / n)} normally, ${pct(with / n)} after you skipped two sad songs.\n")
        assertTrue("skips should lower sad songs", with < without)
        save()
    }

    @Test
    fun newListenerStillGetsTheFeel() {
        line("## A brand-new listener (no history)\n")
        val r = Random(3)
        line("| Seed genre | Same feel | Same language |")
        line("|---|---|---|")
        for (g in genres.filter { it.name in setOf("punjabi_party", "devotional", "lofi", "english_workout", "garba", "sufi") }) {
            val st = (0 until 40).map { val s = seedOf(g.name, r); stats(s, listOf(newQueue(s, 10, Random(r.nextLong()), isNew = true))) }.average()
            line("| ${g.label} | ${pct(st.feel)} | ${pct(st.language)} |")
            if (st.feel < 0.6) softFeel["new listener, ${g.label}"] = st.feel
        }
        line("")
        save()
        assertTrue("weak: $softFeel", softFeel.isEmpty())
    }

    @Test
    fun knownNewAndTrendingMix() {
        line("## The mix: songs you know, new songs, trending\n")
        val r = Random(5)
        val st = genres.map { g -> (0 until 30).map { val s = seedOf(g.name, r); stats(s, listOf(newQueue(s, 10, Random(r.nextLong())))) }.average() }.average()
        val old = genres.map { g -> (0 until 30).map { val s = seedOf(g.name, r); stats(s, listOf(oldQueue(s, 10, Random(r.nextLong())))) }.average() }.average()
        line("Across all genres: songs you already play ${pct(old.known)} → ${pct(st.known)}, liked songs ${pct(old.liked)} → ${pct(st.liked)}, trending ${pct(old.trending)} → ${pct(st.trending)}.\n")
        assertTrue("liked songs capped", st.liked <= 0.35)
        save()
    }

    // ── Aalap 5.0 ───────────────────────────────────────────────────────────

    @Test
    fun smoothFlowAvoidsJumps() {
        line("## Smooth flow (energy and tempo from song to song)\n")
        fun jumps(flow: Boolean): Pair<Double, Double> {
            val r = Random(21); var gap = 0.0; var big = 0; var n = 0
            genres.forEach { g -> repeat(15) {
                val seed = seedOf(g.name, r)
                val q = listOf(seed) + newQueue(seed, 10, Random(r.nextLong()), flow = flow)
                q.zipWithNext().forEach { (a, b) -> val d = kotlin.math.abs(a.vibe.energy - b.vibe.energy); gap += d; if (d > 0.3) big++; n++ }
            } }
            return gap / n to big.toDouble() / n
        }
        val (offGap, offBig) = jumps(false); val (onGap, onBig) = jumps(true)
        line("Average energy change between songs: ${"%.2f".format(offGap)} → **${"%.2f".format(onGap)}**; big jumps (over 0.3): ${pct(offBig)} → **${pct(onBig)}**.\n")
        save()
        assertTrue("smoother", onGap < offGap && onBig < offBig)
    }

    @Test
    fun discoveryDial() {
        line("## The discovery dial\n")
        line("| Setting | Songs you know | Surprises (artists you don't play) |")
        line("|---|---|---|")
        val dials = listOf(
            "Familiar" to { t: Targets -> t.copy(familiar = 0.75, surprise = 0.0) },
            "Balanced" to { t: Targets -> t },
            "Surprise me" to { t: Targets -> t.copy(familiar = 0.08, surprise = 0.3) },
        )
        val known = ArrayList<Double>(); val surprises = ArrayList<Double>()
        for ((label, tune) in dials) {
            val r = Random(9); var k = 0; var sp = 0; var n = 0
            listOf("bollywood_romantic", "bollywood_sad", "punjabi_party", "english_pop", "hindi_hiphop").forEach { gname -> repeat(20) {
                val q = newQueue(seedOf(gname, r), 10, Random(r.nextLong()), tune = tune)
                k += q.count { it.song.id in playedIds }
                sp += q.count { it.song.id !in playedIds && it.song.id !in likedIds && com.music.aalap.data.recommendation.engine.SongMatcher.artistKeys(it.artist).first() !in newProfile.usualArtists }
                n += q.size
            } }
            known += k.toDouble() / n; surprises += sp.toDouble() / n
            line("| $label | ${pct(k.toDouble() / n)} | ${pct(sp.toDouble() / n)} |")
        }
        line("")
        save()
        assertTrue("familiar has the most known songs", known[0] > known[1] && known[1] > known[2])
        assertTrue("surprise me has the most surprises", surprises[2] > surprises[0])
    }

    @Test
    fun languageMixIsFollowed() {
        line("## Language mix\n")
        val r = Random(13); var hindi = 0; var punjabi = 0; var english = 0; var n = 0
        val mix = mapOf("punjabi" to 0.6, "english" to 0.4)
        listOf("punjabi_party", "english_pop", "bollywood_party", "english_workout").forEach { gname -> repeat(20) {
            val q = newQueue(seedOf(gname, r), 10, Random(r.nextLong()), tune = { it.copy(languageMix = mix) })
            hindi += q.count { it.genre.language == "hindi" }; punjabi += q.count { it.genre.language == "punjabi" }; english += q.count { it.genre.language == "english" }; n += q.size
        } }
        line("Mix set to Punjabi 60% + English 40% (Hindi off): Punjabi ${pct(punjabi.toDouble() / n)}, English ${pct(english.toDouble() / n)}, Hindi ${pct(hindi.toDouble() / n)}.\n")
        save()
        assertTrue("hindi mostly kept out (${pct(hindi.toDouble() / n)})", hindi.toDouble() / n < 0.15)
        assertTrue("punjabi and english make the queue", (punjabi + english).toDouble() / n > 0.8)
    }

    @Test
    fun feedbackIsObeyed() {
        line("## \"Not this\" feedback\n")
        val r = Random(17)
        val blockedArtist = com.music.aalap.data.recommendation.engine.SongMatcher.artistKeys("bollywood_romantic_artist_0").first()
        val sadIds = byGenre.getValue("bollywood_sad").take(6).map { it.song.id }
        val fb = com.music.aalap.data.recommendation.engine.Feedback.build(
            rows = listOf("BLOCK_ARTIST" to blockedArtist) + sadIds.map { "LESS" to it },
            songs = sadIds.associateWith { byId.getValue(it).song },
            vibes = sadIds.associateWith { byId.getValue(it).vibe },
        )
        var blockedSeen = 0; var sadWith = 0; var sadWithout = 0; var n = 0
        repeat(60) {
            val seed = seedOf("bollywood_sad", r)
            val a = newQueue(seed, 10, Random(r.nextLong()), feedback = fb)
            val b = newQueue(seed, 10, Random(r.nextLong()))
            blockedSeen += a.count { it.artist == "bollywood_romantic_artist_0" } + newQueue(seedOf("bollywood_romantic", r), 10, Random(r.nextLong()), feedback = fb).count { it.artist == "bollywood_romantic_artist_0" }
            sadWith += a.count { it.genre.main == Feel.SAD }; sadWithout += b.count { it.genre.main == Feel.SAD }; n += 10
        }
        line("Blocked artist: played $blockedSeen times in $n songs. \"Less like this\" on 6 sad songs: sad songs ${pct(sadWithout.toDouble() / n)} → ${pct(sadWith.toDouble() / n)}.\n")
        save()
        assertEquals("blocked artist never plays", 0, blockedSeen)
        assertTrue("less like this lowers sad ($sadWith vs $sadWithout)", sadWith < sadWithout)
    }

    @Test
    fun rediscoverBringsBackOldFavourites() {
        line("## Re-discover\n")
        val r = Random(23)
        val old = byGenre.getValue("bollywood_romantic").drop(40).take(15).map { it.song.id }.toSet()
        var back = 0; var n = 0
        repeat(60) { val q = newQueue(seedOf("bollywood_romantic", r), 10, Random(r.nextLong()), rediscoverIds = old); back += q.count { it.song.id in old }; n += q.size }
        line("Old favourites (played a lot, not for 45+ days) coming back: ${pct(back.toDouble() / n)} of songs (aim ~10%).\n")
        save()
        assertTrue("some come back (${pct(back.toDouble() / n)})", back.toDouble() / n in 0.04..0.25)
    }

    private fun save() {
        runCatching {
            val f = File("build/reports/reco-scenarios.md")
            f.parentFile.mkdirs()
            f.appendText(report.toString()); report.clear()
        }
    }
}

package com.music.spotui.data.recommendation.engine

import android.content.Context
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId

/**
 * Builds the [TasteProfile] from the database and scores candidates:
 *
 *     finalScore = 0.6·globalScore + 0.2·tasteScore + 0.2·habitScore
 *
 * The profile is rebuilt on its own after new plays (at most once every 30 s), so
 * the taste follows how the app is actually used.
 */
object Personalizer {
    private const val HALF_LIFE_DAYS = 60.0
    private const val REBUILD_MS = 30_000L

    @Volatile private var cached: Triple<String, Long, TasteProfile>? = null

    /** Drops the cached profile (a play was just logged, a song liked…). */
    fun invalidate() { cached = null }

    /** 0 night (0–5h), 1 morning (6–11h), 2 afternoon (12–17h), 3 evening (18–23h). */
    fun dayPart(ts: Long): Int = Instant.ofEpochMilli(ts).atZone(ZoneId.systemDefault()).hour / 6

    fun profile(context: Context, userId: String = RecoDb.userId(context)): TasteProfile {
        cached?.let { (u, at, p) -> if (u == userId && System.currentTimeMillis() - at < REBUILD_MS) return p }
        val p = runCatching { build(context, userId) }.getOrElse {
            android.util.Log.w("Personalizer", "taste build failed: $it"); TasteProfile.EMPTY
        }
        cached = Triple(userId, System.currentTimeMillis(), p)
        return p
    }

    private fun build(context: Context, userId: String): TasteProfile {
        val db = RecoDb.get(context)
        val now = System.currentTimeMillis()
        val plays = db.plays(userId, limit = 4000)
        val likedApp = runCatching { com.music.spotui.data.preferences.getLikedSongIds(context) }.getOrDefault(emptySet())
        val likedDb = db.likedIds(userId)
        val songs = db.songs(plays.map { it.songId }.toSet() + likedDb)
        // The app's like list is the truth (unlikes included); the database just knows the liked songs' details.
        val liked = songs.values.filter { SongBridge.appId(it) in likedApp }.mapTo(HashSet()) { it.id }

        val artist = HashMap<String, Double>()
        val artistPlays = HashMap<String, Int>()
        val artistSkips = HashMap<String, Int>()
        val songAff = HashMap<String, Double>()
        val lang = HashMap<String, Double>()
        val partNow = dayPart(now)
        val partArtist = HashMap<String, Int>()
        var partTotal = 0

        for (p in plays) {
            val s = songs[p.songId] ?: continue
            val keys = SongMatcher.artistKeys(s.artists)
            val main = keys.firstOrNull() ?: continue
            val decay = Math.pow(0.5, (now - p.playedAt) / 86_400_000.0 / HALF_LIFE_DAYS)
            val w = if (p.skipped) -0.6 * decay else decay
            keys.forEachIndexed { i, k -> artist[k] = (artist[k] ?: 0.0) + w * (if (i == 0) 1.0 else 0.6) }
            artistPlays[main] = (artistPlays[main] ?: 0) + 1
            if (p.skipped) {
                artistSkips[main] = (artistSkips[main] ?: 0) + 1
            } else {
                songAff[p.songId] = (songAff[p.songId] ?: 0.0) + decay
                s.info.language.takeIf { it.isNotBlank() }?.let { lang[it] = (lang[it] ?: 0.0) + decay }
                if (dayPart(p.playedAt) == partNow) {
                    partArtist[main] = (partArtist[main] ?: 0) + 1
                    partTotal++
                }
            }
        }
        // Likes, follows and imported favourites lift artists even before they're played much.
        liked.mapNotNull { songs[it] }.forEach { s ->
            SongMatcher.artistKeys(s.artists).firstOrNull()?.let { artist[it] = (artist[it] ?: 0.0) + 1.5 }
        }
        val followed = followedArtistKeys(context)
        followed.forEach { artist[it] = (artist[it] ?: 0.0) + 2.0 }
        // Spotify's own view of your listening: your top artists there.
        com.music.spotui.data.api.SpotifyTaste.topArtists(context).forEachIndexed { i, name ->
            SongMatcher.artistKeys(name).firstOrNull()?.let { artist[it] = (artist[it] ?: 0.0) + 2.5 * (1.0 - i / 80.0) }
        }
        // YouTube Music recent plays count like imported favourites (its subscriptions are follows, below).
        com.music.spotui.data.api.YouTubeLibrary.historyArtistCounts(context).forEach { (name, n) ->
            SongMatcher.artistKeys(name).firstOrNull()?.let { artist[it] = (artist[it] ?: 0.0) + minOf(3.0, 0.4 * n) }
        }
        com.music.spotui.data.local.AppleMusicImport.artistCounts(context).forEach { (name, n) ->
            SongMatcher.artistKeys(name).firstOrNull()?.let { artist[it] = (artist[it] ?: 0.0) + minOf(3.0, 0.5 * n) }
        }

        val completed = plays.count { !it.skipped }.coerceAtLeast(1)
        val partShare = partTotal.toDouble() / completed
        val dayPartPref = if (partShare < 0.05) emptyMap() else artistPlays.filter { it.value >= 3 }.mapValues { (a, total) ->
            ((partArtist[a] ?: 0).toDouble() / total / partShare / 2.0).coerceIn(0.0, 1.0)
        }
        val langTop = lang.values.maxOrNull() ?: 0.0
        val recentCut = now - 3 * 3_600_000L
        val profile = TasteProfile(
            artistWeight = TasteProfile.normalise(artist),
            skipRate = artistPlays.filter { it.value >= 3 }.mapValues { (a, n) -> (artistSkips[a] ?: 0).toDouble() / n },
            songAffinity = TasteProfile.normalise(songAff),
            liked = liked,
            followed = followed,
            language = if (langTop > 0) lang.mapValues { it.value / langTop } else emptyMap(),
            dayPart = dayPartPref,
            playCount = plays.size,
            recentIds = (plays.take(30) + plays.filter { it.playedAt >= recentCut }).mapTo(HashSet()) { it.songId },
            recentSkips = plays.filter { it.skipped && now - it.playedAt < 14 * 86_400_000L }.mapTo(HashSet()) { it.songId },
        )
        // Snapshot for the user_taste table (and the Profile "Your taste" card).
        runCatching {
            val top = profile.artistWeight.entries.sortedByDescending { it.value }.take(60)
            db.saveArtistWeights(userId, JSONObject().apply { top.forEach { put(it.key, it.value) } }.toString())
        }
        return profile
    }

    private fun followedArtistKeys(context: Context): Set<String> {
        val names = context.getSharedPreferences("FollowedArtists", Context.MODE_PRIVATE).all.values.mapNotNull { it as? String } +
            com.music.spotui.data.preferences.getCachedFollowedArtists(context).map { it.name } +
            com.music.spotui.data.api.YouTubeLibrary.artists(context).map { it.name }
        val hidden = com.music.spotui.data.api.YouTubeLibrary.hiddenArtists(context)
        return names.mapNotNull { SongMatcher.artistKeys(it).firstOrNull() }.filter { it !in hidden }.toSet()
    }

    /** Scores [candidates] (with their normalised global scores) after [seed]. */
    fun score(
        context: Context,
        userId: String,
        seed: Song?,
        candidates: List<Pair<Song, Double>>,
        sourcesById: Map<String, Set<Source>> = emptyMap(),
        profile: TasteProfile = profile(context, userId),
    ): List<Scored> {
        val now = System.currentTimeMillis()
        val habits = seed?.let { s -> RecoDb.get(context).transitionsFrom(userId, s.id).associateBy { it.toId } }.orEmpty()
        return candidates.map { (song, global) ->
            val taste = profile.taste(song.info, song.id)
            val habit = habits[song.id]?.let { TasteProfile.habit(it.count, it.lastAt, now) } ?: 0.0
            Scored(song, global, taste, habit, TasteProfile.final(global, taste, habit, profile.isNew), sourcesById[song.id].orEmpty())
        }
    }

    /** An artist's name as Spotify / YouTube Music / your follows spell it. */
    private fun displayFromServices(context: Context, key: String): String? =
        (com.music.spotui.data.api.SpotifyTaste.topArtists(context) +
            com.music.spotui.data.api.YouTubeLibrary.allFollowed(context, null).map { it.name })
            .firstOrNull { SongMatcher.artistKeys(it).firstOrNull() == key }

    /** Finished plays per artist (main credit), most played first, with the name as you see it. */
    fun artistPlayCounts(context: Context): List<Pair<String, Int>> {
        val userId = RecoDb.userId(context)
        val db = RecoDb.get(context)
        val plays = db.plays(userId, limit = 5000).filter { !it.skipped }
        val songs = db.songs(plays.map { it.songId }.toSet())
        val counts = HashMap<String, Int>()
        val display = HashMap<String, String>()
        plays.forEach { p ->
            val s = songs[p.songId] ?: return@forEach
            val name = s.artists.split(",").firstOrNull()?.trim().orEmpty()
            val key = SongMatcher.artistKeys(name).firstOrNull() ?: return@forEach
            counts[key] = (counts[key] ?: 0) + 1
            display.putIfAbsent(key, name)
        }
        return counts.entries.sortedByDescending { it.value }.map { (display[it.key] ?: it.key) to it.value }
    }

    /** Human-readable summary for Profile: top artists, languages, and when you listen. */
    data class Summary(val artists: List<String>, val languages: List<String>, val plays: Int, val skipRate: Double)

    fun summary(context: Context, topN: Int = 6): Summary {
        val userId = RecoDb.userId(context)
        val p = profile(context, userId)
        val db = RecoDb.get(context)
        val plays = db.plays(userId, limit = 4000)
        val songs = db.songs(plays.map { it.songId }.toSet())
        // Show names as people write them: the most common spelling for each artist key.
        val display = HashMap<String, String>()
        songs.values.forEach { s ->
            s.artists.split(",").map { it.trim() }.forEach { n -> SongMatcher.artistKeys(n).firstOrNull()?.let { display.putIfAbsent(it, n) } }
        }
        return Summary(
            artists = p.artistWeight.entries.sortedByDescending { it.value }.take(topN).map { display[it.key] ?: displayFromServices(context, it.key) ?: it.key.replaceFirstChar(Char::titlecase) },
            languages = p.language.entries.sortedByDescending { it.value }.take(3).map { it.key.replaceFirstChar(Char::titlecase) },
            plays = plays.size,
            skipRate = if (plays.isEmpty()) 0.0 else plays.count { it.skipped }.toDouble() / plays.size,
        )
    }
}

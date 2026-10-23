package com.music.aalap.data.recommendation

import android.content.Context
import com.music.aalap.data.api.Discover
import com.music.aalap.data.api.Mood
import com.music.aalap.data.entity.SongsModel
import com.music.aalap.data.recommendation.engine.Diversifier
import com.music.aalap.data.recommendation.engine.Personalizer
import com.music.aalap.data.recommendation.engine.QueueEngine
import com.music.aalap.data.recommendation.engine.RecoDb
import com.music.aalap.data.recommendation.engine.SongBridge
import com.music.aalap.data.recommendation.engine.SongMatcher
import java.util.Random

/**
 * Aalap Radio: stations made from the listener's taste.
 *
 *  - Your Mix blends your favourites (what you finish and replay, not just likes),
 *    liked songs you haven't played much, and new songs the engine found for you.
 *  - Mood stations (Happy, Sad, Romantic, Party…) take the mood's songs and order
 *    them for your taste. The stations you use most, and the ones that fit the time
 *    of day, come first.
 *
 * When a station's list ends, the queue engine keeps it going.
 */
object AalapRadio {
    private const val PREF = "aalap_radio"

    /** Your top artists, for the Your Mix card ("Arijit Singh, Kamal Kahlon and more"). */
    fun topArtists(context: Context, n: Int = 3): List<String> =
        runCatching { Personalizer.summary(context).artists.take(n) }.getOrDefault(emptyList())

    /** Up to 4 album covers of your current favourites, for the Your Mix card. */
    fun mixCovers(context: Context): List<String> = runCatching {
        QueueEngine.ensureImported(context)
        val profile = Personalizer.profile(context)
        val top = profile.songAffinity.entries.sortedByDescending { it.value }.take(40).map { it.key }
        val songs = RecoDb.get(context).songs(top).let { m -> top.mapNotNull { m[it] } }
            .filter { it.info.cover.isNotBlank() }
        // Prefer different artists, then fill up with whatever covers there are.
        val varied = songs.distinctBy { SongMatcher.artistKeys(it.artists).firstOrNull() }
        (varied + songs).map { it.info.cover }.distinct().take(4)
    }.getOrDefault(emptyList())

    suspend fun yourMix(context: Context, salt: Long = System.currentTimeMillis(), size: Int = 30): List<SongsModel> {
        QueueEngine.ensureImported(context)
        val userId = RecoDb.userId(context)
        val profile = Personalizer.profile(context, userId)
        val db = RecoDb.get(context)
        val rnd = Random(salt)

        // Favourites: how much you replay a song, plus how much you like its artist.
        val favIds = profile.songAffinity.keys.filter { it !in profile.recentSkips }
        val favSongs = db.songs(favIds)
        val favScored = favSongs.values.map { s ->
            s to (0.6 * (profile.songAffinity[s.id] ?: 0.0) + 0.4 * profile.taste(s.info, s.id) -
                (if (s.id in profile.recentIds) 0.3 else 0.0))
        }.sortedByDescending { it.second }.take(60).toMutableList()
        val favourites = buildList {
            while (size > 0 && favScored.isNotEmpty() && this.size < size / 2) {
                val pick = Diversifier.sampleWeighted(favScored, rnd) { it.second.coerceAtLeast(0.01) }!!
                favScored.remove(pick); add(pick.first)
            }
        }
        // Liked but rarely played: worth rediscovering.
        val rediscover = db.songs(profile.liked.filter { (profile.songAffinity[it] ?: 0.0) < 0.3 && it !in profile.recentIds })
            .values.shuffled(rnd).take(size / 6)
        // New to you, from the engine.
        val fresh = runCatching { Discover.dailyPicks(context) }.getOrDefault(emptyList()).shuffled(rnd).take(size / 3)

        val favModels = favourites.map(SongBridge::toSongsModel)
        val redModels = rediscover.map(SongBridge::toSongsModel)
        val out = ArrayList<SongsModel>()
        var f = 0; var n = 0; var r = 0
        // Pattern: two favourites, one new, and now and then a rediscovered like.
        while (out.size < size && (f < favModels.size || n < fresh.size || r < redModels.size)) {
            repeat(2) { if (f < favModels.size) out += favModels[f++] }
            if (n < fresh.size) out += fresh[n++]
            if (out.size % 5 == 0 && r < redModels.size) out += redModels[r++]
        }
        return Diversifier.spreadArtists(out.distinctBy { it.id }.take(size)) { it.singer }
    }

    /** A mood station: the mood's songs ranked for you, in a new order every time. */
    suspend fun moodRadio(context: Context, mood: Mood, salt: Long = System.currentTimeMillis()): List<SongsModel> {
        recordMood(context, mood)
        val mix = Discover.moodMix(context, mood)
        if (mix.isEmpty()) return mix
        // Keep the taste order roughly (top songs early) but shuffle within windows of 6.
        val rnd = Random(salt)
        return mix.take(50).chunked(6).flatMap { it.shuffled(rnd) }.let { list -> Diversifier.spreadArtists(list) { it.singer } }
    }

    private fun recordMood(context: Context, mood: Mood) {
        val p = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        p.edit().putInt("mood_${mood.name}", p.getInt("mood_${mood.name}", 0) + 1).apply()
    }

    /** Moods ordered for now: the ones you play most, then the ones that suit the time of day. */
    fun moodOrder(context: Context): List<Mood> {
        val p = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val timeFit: List<Mood> = when (Personalizer.dayPart(System.currentTimeMillis())) {
            0 -> listOf(Mood.CHILL, Mood.SAD, Mood.HEARTBROKEN, Mood.ROMANTIC, Mood.FOCUS)
            1 -> listOf(Mood.DEVOTIONAL, Mood.HAPPY, Mood.ENERGETIC, Mood.FOCUS, Mood.ROAD_TRIP)
            2 -> listOf(Mood.FOCUS, Mood.HAPPY, Mood.ROAD_TRIP, Mood.ENERGETIC, Mood.CHILL)
            else -> listOf(Mood.ROMANTIC, Mood.PARTY, Mood.CHILL, Mood.HAPPY, Mood.SAD)
        }
        return Mood.entries.sortedWith(
            compareByDescending<Mood> { p.getInt("mood_${it.name}", 0) }
                .thenBy { timeFit.indexOf(it).let { i -> if (i < 0) 99 else i } },
        )
    }

    /** The artists in your taste that also sing in a mood (shown under a station), if its mix is cached. */
    fun artistsLike(context: Context, songs: List<SongsModel>, n: Int = 2): List<String> {
        val profile = Personalizer.profile(context)
        return songs.flatMap { it.singer.split(",").map(String::trim) }
            .distinct()
            .sortedByDescending { profile.artistWeight[SongMatcher.artistKeys(it).firstOrNull()] ?: 0.0 }
            .take(n)
    }
}

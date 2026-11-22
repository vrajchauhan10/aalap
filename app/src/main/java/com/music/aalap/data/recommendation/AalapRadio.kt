package com.music.aalap.data.recommendation

import android.content.Context
import com.music.aalap.data.api.Discover
import com.music.aalap.data.api.Mood
import com.music.aalap.data.entity.SongsModel
import com.music.aalap.data.recommendation.engine.Diversifier
import com.music.aalap.data.recommendation.engine.Pace
import com.music.aalap.data.recommendation.engine.Personalizer
import com.music.aalap.data.recommendation.engine.QueueEngine
import com.music.aalap.data.recommendation.engine.RecentPace
import com.music.aalap.data.recommendation.engine.RecoDb
import com.music.aalap.data.recommendation.engine.SongBridge
import com.music.aalap.data.recommendation.engine.SongMatcher
import com.music.aalap.data.recommendation.engine.Vibe
import com.music.aalap.data.recommendation.engine.VibeStore
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
        // Your pace lately (tempo and energy, any language) nudges which favourites come up.
        val pace = RecentPace.target(context)
        val favVibes = if (pace != null) VibeStore.vibes(context, favSongs.values) else emptyMap()
        val favScored = favSongs.values.map { s ->
            s to (0.6 * (profile.songAffinity[s.id] ?: 0.0) + 0.4 * profile.taste(s.info, s.id) -
                (if (s.id in profile.recentIds) 0.3 else 0.0) +
                (if (pace != null) 0.2 * (Pace.closeness(pace, favVibes[s.id] ?: Vibe.UNKNOWN) - 0.5) else 0.0))
        }.sortedByDescending { it.second }.take(60).toMutableList()
        // Half the mix from your favourites. (Inside buildList, a bare `size` is the list being
        // built, always 0 here: the mix's own size is named so it can't be mistaken for it.)
        val wanted = size
        val favourites = buildList {
            while (wanted > 0 && favScored.isNotEmpty() && this.size < wanted / 2) {
                val pick = Diversifier.sampleWeighted(favScored, rnd) { it.second.coerceAtLeast(0.01) } ?: break
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
        // Every round adds at least one song or stops: a round that added nothing used to spin
        // forever (a core at 100% in the background) once only rediscovered likes were left.
        while (out.size < size && (f < favModels.size || n < fresh.size || r < redModels.size)) {
            val before = out.size
            repeat(2) { if (f < favModels.size) out += favModels[f++] }
            if (n < fresh.size) out += fresh[n++]
            if ((out.size % 5 == 0 || out.size == before) && r < redModels.size) out += redModels[r++]
        }
        return Diversifier.spreadArtists(com.music.aalap.data.recommendation.engine.MixClean.songs(out.distinctBy { it.id }).take(size)) { it.singer }
    }

    /** A mood station: the mood's songs ranked for you, in a new order every time. */
    suspend fun moodRadio(context: Context, mood: Mood, salt: Long = System.currentTimeMillis()): List<SongsModel> {
        recordMood(context, mood)
        val mix = Discover.moodMix(context, mood)
        if (mix.isEmpty()) return mix
        // Keep the taste order roughly (top songs early) but shuffle within windows of 6.
        val rnd = Random(salt)
        val ordered = forMood(context, mood, mix)
        val list = com.music.aalap.data.recommendation.engine.MixClean.songs(ordered.take(60)).chunked(6).flatMap { it.shuffled(rnd) }
            .let { l -> Diversifier.spreadArtists(l) { it.singer } }
        return freshFirst(context, mood, list)
    }

    /**
     * The station picks up where it left off: songs it hasn't played you lately come first,
     * the ones it did (or that you played anywhere in the last 3 days) go to the end. Every
     * open used to start with the same top songs.
     */
    fun freshFirst(context: Context, mood: Mood, songs: List<SongsModel>): List<SongsModel> = freshFirst(context, mood.name, songs)

    /** A station on Home beyond the moods: new songs or a decade in your language, or an artist's radio. */
    data class Station(
        val id: String,
        val title: String,
        val subtitle: String,
        val badge: String,
        val query: String = "",
        val lang: String = "",
        /** For an artist's station: your favourite song of theirs, which the radio grows from. */
        val seed: SongsModel? = null,
    )

    /**
     * Stations made for you: new releases and the 90s in each of your top two languages, and
     * a radio for each of your two favourite artists (that artist, then songs like theirs).
     */
    fun stationsForYou(context: Context): List<Station> = runCatching {
        val langs = Discover.radioLanguages(context, Mood.HAPPY).map { it.first }.filter { it.isNotBlank() }.take(2)
        fun name(l: String) = l.replaceFirstChar { it.uppercase() }
        val out = ArrayList<Station>()
        langs.forEach { l ->
            out += Station("new_$l", "New ${name(l)}", "Fresh releases, picked for you", "NEW",
                query = if (l == "hindi") "new hindi songs" else "new $l songs", lang = l)
        }
        langs.forEach { l ->
            out += Station("90s_$l", "90s ${name(l)}", "The decade's best-loved songs", "90s",
                query = if (l == "hindi") "90s bollywood hits" else "90s $l songs", lang = l)
        }
        // Your two favourite artists: the song of theirs you play most starts the radio.
        val profile = Personalizer.profile(context)
        val db = RecoDb.get(context)
        val songs = db.songs(profile.songAffinity.entries.sortedByDescending { it.value }.take(150).map { it.key })
        val byArtist = LinkedHashMap<String, com.music.aalap.data.recommendation.engine.Song>()
        profile.songAffinity.entries.sortedByDescending { it.value }.forEach { (id, _) ->
            val s = songs[id] ?: return@forEach
            val a = s.artists.split(",").first().trim()
            if (a.isNotBlank() && a.lowercase() !in byArtist.keys.map { it.lowercase() }) byArtist[a] = s
        }
        byArtist.entries.take(2).forEach { (artist, song) ->
            out += Station("artist_${artist.lowercase()}", "$artist & friends", "$artist, then songs like theirs", "",
                seed = SongBridge.toSongsModel(song))
        }
        // Artists first (they're yours), then new, then the 90s.
        out.sortedBy { if (it.seed != null) 0 else if (it.id.startsWith("new_")) 1 else 2 }
    }.getOrDefault(emptyList())

    /** A search station's songs, fresh-first like the moods. */
    suspend fun stationSongs(context: Context, st: Station, salt: Long = System.currentTimeMillis()): List<SongsModel> {
        val mix = Discover.stationMix(context, st.id, st.query, st.lang)
        val rnd = Random(salt)
        val list = com.music.aalap.data.recommendation.engine.MixClean.songs(mix.take(60)).chunked(6).flatMap { it.shuffled(rnd) }
            .let { l -> Diversifier.spreadArtists(l) { it.singer } }
        return freshFirst(context, st.id, list)
    }

    fun freshFirst(context: Context, key: String, songs: List<SongsModel>): List<SongsModel> {
        val mood = key
        if (songs.isEmpty()) return songs
        val p = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val dayMs = 24 * 60 * 60 * 1000L
        fun key(s: SongsModel) = s.spotifyTrackId.ifBlank { (s.title + "|" + s.singer).lowercase() }
        // What this station served before (key → when), kept for a week, at most 300.
        val served = runCatching { org.json.JSONObject(p.getString("served_$mood", "{}") ?: "{}") }.getOrDefault(org.json.JSONObject())
        val recent = HashSet<String>()
        served.keys().forEach { k -> if (now - served.optLong(k) < 7 * dayMs) recent += k }
        runCatching {
            com.music.aalap.data.preferences.getListeningHistory(context).filter { now - it.ts < 3 * dayMs }
                .forEach { h -> recent += h.spotifyTrackId.ifBlank { (h.title + "|" + h.singer).lowercase() } }
        }
        val (heard, fresh) = songs.partition { key(it) in recent }
        val out = fresh + heard
        // The first ten are what you'll hear this time: next time they move to the back.
        val keep = org.json.JSONObject()
        served.keys().forEach { k -> if (now - served.optLong(k) < 7 * dayMs) keep.put(k, served.optLong(k)) }
        out.take(10).forEach { keep.put(key(it), now) }
        val trimmed = if (keep.length() > 300) org.json.JSONObject().also { o ->
            keep.keys().asSequence().map { it to keep.optLong(it) }.sortedByDescending { it.second }.take(300).forEach { (k, v) -> o.put(k, v) }
        } else keep
        p.edit().putString("served_$mood", trimmed.toString()).apply()
        return out
    }

    /** A mood's songs in the order its station plays them (the same on Home, Search and voice). */
    fun forMood(context: Context, mood: Mood, mix: List<SongsModel>): List<SongsModel> =
        if (mood == Mood.GYM) workoutOrder(context, mix) else mix

    /** Gym Freaks: songs known to be high energy or fast first, slow or calm ones out ([GymFit]). */
    private fun workoutOrder(context: Context, mix: List<SongsModel>): List<SongsModel> = runCatching {
        val canon = RecoDb.get(context).resolveAll(mix.map(com.music.aalap.data.recommendation.engine.SongBridge::toCandidate))
        val vibes = com.music.aalap.data.recommendation.engine.VibeStore.vibes(context, canon)
        GymFit.order(mix.zip(canon).map { (song, c) ->
            val v = vibes[c.id]
            GymFit.Track(song, energy = v?.takeIf { it.energyKnown }?.energy, bpm = v?.bpm)
        })
    }.getOrDefault(mix)

    private fun recordMood(context: Context, mood: Mood) {
        val p = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        p.edit().putInt("mood_${mood.name}", p.getInt("mood_${mood.name}", 0) + 1).apply()
    }

    /** Moods ordered for now: the ones you play most, then the ones that suit the time of day. */
    fun moodOrder(context: Context): List<Mood> {
        val p = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val timeFit: List<Mood> = when (Personalizer.dayPart(System.currentTimeMillis())) {
            0 -> listOf(Mood.CHILL, Mood.SAD, Mood.HEARTBROKEN, Mood.ROMANTIC, Mood.FOCUS)
            1 -> listOf(Mood.DEVOTIONAL, Mood.GYM, Mood.HAPPY, Mood.ENERGETIC, Mood.FOCUS, Mood.ROAD_TRIP)
            2 -> listOf(Mood.FOCUS, Mood.HAPPY, Mood.ROAD_TRIP, Mood.ENERGETIC, Mood.GYM, Mood.CHILL)
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

package com.music.aalap.data.api

import android.content.Context
import android.util.Log
import com.music.aalap.data.entity.SongsModel
import com.music.aalap.data.preferences.getListeningHistory
import kotlinx.coroutines.flow.toList
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/**
 * Moods shown at the top of Home. Each is backed by Spotify's own mood playlists
 * (found by [query]), so a song lands in a mood the way listeners categorise it.
 */
enum class Mood(val label: String, val query: String, val blurb: String, val colors: Pair<Long, Long>) {
    ENERGETIC("Energetic", "energetic hindi workout", "High-tempo tracks to get you moving", 0xFFFF8A3D to 0xFFE0461B),
    HAPPY("Happy", "happy hindi songs", "Feel-good songs that lift the mood", 0xFFFFC56B to 0xFFE08A00),
    SAD("Sad", "sad hindi songs", "Slow, emotional songs for quiet moments", 0xFF5B7CFA to 0xFF2A3A8C),
    HEARTBROKEN("Heartbroken", "heartbreak hindi songs", "For when it hurts a little", 0xFF9B5DE5 to 0xFF4B2A8C),
    ROMANTIC("Romantic", "romantic hindi songs", "Love songs, old and new", 0xFFE5397A to 0xFF8C1F4A),
    CHILL("Chill", "chill hindi lofi", "Lo-fi and easy listening", 0xFF2BB3C0 to 0xFF15606B),
    PARTY("Party", "bollywood party hits", "Dance-floor fillers", 0xFFFF5E7E to 0xFFB8235A),
    DEVOTIONAL("Devotional", "bhakti devotional songs", "Bhajans, aartis and spiritual songs", 0xFFFFB347 to 0xFFB86E00),
    FOCUS("Focus", "focus instrumental indian", "Instrumentals to help you concentrate", 0xFF6E8B74 to 0xFF2F4A36),
    ROAD_TRIP("Road trip", "road trip hindi songs", "Windows down, volume up", 0xFF3BA55C to 0xFF1E5E33),
    // Spotify's gym playlists for the songs, then only high energy first (AalapRadio.moodRadio).
    GYM("Gym Freaks", "gym workout", "Heavy beats and high tempo for lifting and cardio", 0xFFE53935 to 0xFF5A0F0F),
}

/** Spotify's daily-updated chart playlists. */
data class Chart(val id: String, val title: String, val subtitle: String, val colors: Pair<Long, Long>)

val CHARTS = listOf(
    Chart("37i9dQZEVXbLZ52XmnySJg", "Top 50 India", "Most played in India today", 0xFFFF8A3D to 0xFF7A2E0E),
    Chart("37i9dQZEVXbMDoHDwVN2tF", "Top 50 Global", "Most played worldwide today", 0xFF3D6BE0 to 0xFF1A2A6C),
    // Spotify reused the old "Viral 50 India" id for "Top Songs - India" (weekly most played),
    // so India's trending chart is Spotify's own "Trending Now India".
    Chart("37i9dQZF1DXbVhgADFy3im", "Viral 50 India", "Rising fastest in India this week", 0xFFE5397A to 0xFF6C1238),
    // Spotify retired "Viral 50 - Global"; its replacement "Global Viral 50" holds only a few
    // playable songs, so worldwide trending uses Spotify's own "Viral Hits" (50 songs).
    Chart("37i9dQZF1DX2L0iB23Enbq", "Viral 50 Global", "Rising fastest worldwide this week", 0xFF2BB3C0 to 0xFF0F4C55),
)

/**
 * Discovery for Home: mood mixes, fresh picks and all-time favourites. Results are
 * cached per 6-hour window (so they change four times a day and stay instant within
 * a window); pull-to-refresh moves to a new mix straight away.
 */
object Discover {
    private const val TAG = "Discover"
    private const val PREF = "discover_cache"

    private const val SLOT_HOURS = 6
    private const val KEY_GEN = "refresh_gen"

    private fun prefs(c: Context) = c.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    /** Current 6-hour window: mixes rebuild four times a day. */
    private fun slot() = "${LocalDate.now()}-${java.time.LocalTime.now().hour / SLOT_HOURS}"

    /** Bumped by pull-to-refresh, so a refresh always gives a different mix. */
    private fun gen(c: Context) = prefs(c).getInt(KEY_GEN, 0)

    /** The current 6-hour window, e.g. "2026-10-20-3" (Home rebuilds when it changes). */
    fun currentWindow(): String = slot()

    /** Cache prefix for the current window + refresh generation. */
    private fun prefix(c: Context) = "${slot()}#${gen(c)}"

    /** Seed for shuffles/rotations: changes every window and on every refresh. */
    private fun salt(c: Context, extra: Int = 0): Long =
        (slot().hashCode().toLong() shl 16) + gen(c) * 7919L + extra

    /** Pull-to-refresh: drop cached mixes and move to a new generation. */
    fun clearToday(context: Context) {
        val p = prefs(context)
        p.edit().apply {
            p.all.keys.filter { it != KEY_GEN }.forEach { remove(it) }
            putInt(KEY_GEN, gen(context) + 1)
        }.apply()
    }

    /** Artists you play most (main credit), most-played first. */
    fun topArtists(context: Context, limit: Int = 15): List<String> =
        getListeningHistory(context).map { it.singer.substringBefore(",").trim() }
            .filter { it.isNotBlank() }
            .groupingBy { it }.eachCount().entries
            .sortedByDescending { it.value }.take(limit).map { it.key }

    /** Your most-played songs of all time that can be replayed. */
    fun allTimeFavourites(context: Context, limit: Int = 12): List<SongsModel> =
        getListeningHistory(context).filter { it.url.isNotBlank() }
            .groupBy { it.songId }.values
            .sortedByDescending { it.size }.take(limit)
            .map { plays -> plays.first().let {
                SongsModel(it.songId, it.title, it.album, it.singer, it.image, it.url, it.spotifyTrackId, durationMs = it.durationMs)
            } }

    /**
     * A mood mix: tracks from the top Spotify playlists for the mood, with songs by
     * your most-played artists first, the rest in a different order each day.
     */
    suspend fun moodMix(context: Context, mood: Mood): List<SongsModel> {
        val langs = radioLanguages(context, mood)
        val key = "${prefix(context)}:mood:${mood.name}:${langs.joinToString(",") { it.first }}"
        read(context, key)?.let { return it }
        val api = Api(context)
        // Each of your languages gets its own playlists, in proportion to its share: the
        // stations used to search Hindi playlists only and ignored the language mix.
        val tracks = langs.flatMap { (lang, share) ->
            val want = kotlin.math.max(1, kotlin.math.round(3 * share).toInt())
            val playlists = runCatching {
                api.getCategoryPlaylists(queryFor(mood, lang)).toList()
                    .filterIsInstance<Response.Success<List<com.music.aalap.data.entity.LibraryEntry>>>()
                    .lastOrNull()?.data
            }.getOrNull().orEmpty().take(6)
                // Rotate which of the mood's top playlists feed the mix each window/refresh.
                .shuffled(java.util.Random(salt(context, mood.ordinal + lang.hashCode()))).take(want)
            playlists.flatMap { pl ->
                runCatching {
                    api.getPlaylistSongs(pl.spotifyId).toList()
                        .filterIsInstance<Response.Success<List<SongsModel>>>()
                        .firstOrNull()?.data
                }.getOrNull().orEmpty().take(40)
            }
        }.distinctBy { it.spotifyTrackId.ifBlank { it.title + it.singer } }
            .let { keepLanguages(context, it, langs.map { l -> l.first }, mood) }
        // The station's songs teach the engine what this mood sounds like.
        com.music.aalap.data.recommendation.engine.VibeStore.tagMood(context, tracks, mood.name)
        // Same engine as everywhere else: a fresh order each window, then ranked for your taste.
        val shuffled = tracks.shuffled(java.util.Random(salt(context, 100 + mood.ordinal)))
        val result = com.music.aalap.data.recommendation.Recommender.rerank(context, shuffled, familiar = 0.3f).take(80)
        Log.d(TAG, "mood ${mood.name} in ${langs.joinToString { "${it.first} ${"%.0f".format(it.second * 100)}%" }}: ${tracks.size} tracks")
        if (result.isNotEmpty()) write(context, key, result)
        return result
    }

    /**
     * The languages a station plays, with their shares: your language mix from Settings when
     * you chose one, otherwise the languages you actually finish songs in (up to three).
     * Instrumental moods have no language.
     */
    fun radioLanguages(context: Context, mood: Mood): List<Pair<String, Double>> {
        if (mood == Mood.FOCUS) return listOf("" to 1.0)
        val mix = com.music.aalap.data.preferences.RecoPrefs.languageMix(context).filter { it.value > 0 }
        if (mix.isNotEmpty()) return mix.entries.sortedByDescending { it.value }.map { it.key.lowercase() to it.value }
        val taste = runCatching { com.music.aalap.data.recommendation.engine.Personalizer.profile(context).language }.getOrDefault(emptyMap())
            .filterKeys { it.isNotBlank() }.mapKeys { if (it.key.lowercase() == "urdu") "hindi" else it.key.lowercase() }
        val total = taste.values.filter { it > 0 }.sum()
        if (total <= 0) return listOf("hindi" to 1.0)
        val top = taste.filter { it.value > 0 }.mapValues { it.value / total }.filter { it.value >= 0.12 }
            .entries.sortedByDescending { it.value }.take(3)
        if (top.isEmpty()) return listOf("hindi" to 1.0)
        val sum = top.sumOf { it.value }
        return top.map { it.key to it.value / sum }
    }

    /** The mood's search in a language: "romantic hindi songs" → "romantic punjabi songs". */
    internal fun queryFor(mood: Mood, lang: String): String {
        if (lang.isBlank() || lang == "hindi") return mood.query
        val swapped = mood.query.replace(Regex("\\b(hindi|bollywood|indian)\\b"), lang)
        return if (swapped != mood.query) swapped else "$lang ${mood.query}"
    }

    /** Drops songs known to be in a language you didn't choose (unknown ones stay). */
    private fun keepLanguages(context: Context, songs: List<SongsModel>, langs: List<String>, mood: Mood): List<SongsModel> {
        if (langs.any { it.isBlank() } || songs.isEmpty()) return songs
        return runCatching {
            val db = com.music.aalap.data.recommendation.engine.RecoDb.get(context)
            val canon = db.resolveAll(songs.map(com.music.aalap.data.recommendation.engine.SongBridge::toCandidate))
            val kept = songs.zip(canon).filter { (_, c) ->
                val l = com.music.aalap.data.recommendation.engine.VibeStore.language(context, c)
                l.isBlank() || langs.any { want -> com.music.aalap.data.recommendation.engine.LanguageGuess.same(l, want) }
            }.map { it.first }
            Log.d(TAG, "mood ${mood.name}: kept ${kept.size} of ${songs.size} in ${langs.joinToString()}")
            // Too few left (a rare language for this mood): keep what there is rather than nothing.
            if (kept.size >= 12) kept else songs
        }.getOrDefault(songs)
    }

    /** New-to-you songs mixed from several favourites' radios; changes every window and refresh. */
    suspend fun dailyPicks(context: Context): List<SongsModel> {
        val key = "${prefix(context)}:picks"
        read(context, key)?.let { return it }
        val picks = com.music.aalap.data.recommendation.Recommender.freshPicks(context, salt(context, 999))
        if (picks.isNotEmpty()) write(context, key, picks)
        return picks
    }

    // ── tiny per-day cache (only today's keys are kept) ──

    private fun read(context: Context, key: String): List<SongsModel>? {
        val raw = prefs(context).getString(key, null) ?: return null
        return runCatching {
            val a = JSONArray(raw)
            (0 until a.length()).map { i ->
                val o = a.getJSONObject(i)
                SongsModel(
                    o.getInt("id"), o.optString("title"), o.optString("album"), o.optString("singer"),
                    o.optString("cover"), o.optString("url"), o.optString("sid"), o.optBoolean("explicit"), o.optInt("dur"),
                )
            }
        }.getOrNull()?.takeIf { it.isNotEmpty() }
    }

    private fun write(context: Context, key: String, songs: List<SongsModel>) {
        val a = JSONArray()
        songs.forEach { s ->
            a.put(JSONObject().apply {
                put("id", s.id); put("title", s.title); put("album", s.album); put("singer", s.singer)
                put("cover", s.coverUri); put("url", s.url); put("sid", s.spotifyTrackId)
                put("explicit", s.explicit); put("dur", s.durationMs)
            })
        }
        val p = prefs(context)
        p.edit().apply {
            p.all.keys.filter { it != KEY_GEN && !it.startsWith(prefix(context)) }.forEach { remove(it) } // drop older windows
            putString(key, a.toString())
        }.apply()
    }
}

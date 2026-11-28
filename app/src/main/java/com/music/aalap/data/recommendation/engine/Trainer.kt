package com.music.aalap.data.recommendation.engine

import android.content.Context
import android.util.Log
import com.music.aalap.data.api.Response
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch

/**
 * Teaches the engine what each song is, from what people put together: for each of your
 * languages it reads playlists named for a mood ("romantic hindi songs"), an era ("90s
 * bollywood") or a genre ("desi hip hop"), and labels every song on them. A song on several
 * playlists gets each label, so "romantic" and "90s" and "sufi" add up into one picture.
 * Labels also say the song's language, for songs whose language nothing else told.
 *
 * Runs at most once a week, only on Wi-Fi, one request at a time with pauses: a few minutes of
 * light network work in the background, no work per song.
 */
object Trainer {
    private const val TAG = "Trainer"
    private const val WEEK = 7 * 86_400_000L
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var running = false

    /** What a feed teaches: a feel (Feel name), an era or a genre. */
    data class Feed(val query: String, val tag: String, val kind: String)

    /** Era labels, oldest to newest (the engine moves at most one step at a time). */
    val ERAS = listOf("ERA_5060", "ERA_7080", "ERA_90", "ERA_00", "ERA_10", "ERA_NEW")

    /** Genres strong enough that what follows must share them (rap after rap, ghazal after ghazal). */
    val STRONG_GENRES = setOf("rap", "ghazal", "sufi", "devotional")

    /** Genres that never come into a queue that didn't start with them. */
    val OWN_WORLD = setOf("ghazal", "sufi", "devotional")

    /** A song's genres, with devotional added when that's its main feel (bhajans, stotrams). */
    fun genresOf(labels: Set<String>, vibe: Vibe): Set<String> =
        if (vibe.known && vibe.dominant == Feel.DEVOTIONAL) labels + "devotional" else labels

    /** The feeds for one language. Pure, unit-tested. */
    fun feeds(lang: String): List<Feed> {
        val l = lang.lowercase()
        val film = if (l == "hindi") "bollywood" else l
        return listOf(
            Feed("romantic $l songs", "ROMANTIC", "mood"),
            Feed("sad $l songs", "SAD", "mood"),
            Feed("party $l songs", "PARTY", "mood"),
            Feed("$l gym workout", "ENERGETIC", "mood"),
            Feed("happy $l songs", "HAPPY", "mood"),
            Feed("$l lofi chill", "CHILL", "mood"),
            Feed("$l devotional", "DEVOTIONAL", "mood"),
            Feed("50s 60s old $film classics", "ERA_5060", "era"),
            Feed("70s 80s $film songs", "ERA_7080", "era"),
            Feed("90s $film songs", "ERA_90", "era"),
            Feed("2000s $film songs", "ERA_00", "era"),
            Feed("2010s $film songs", "ERA_10", "era"),
            Feed("new $l songs this week", "ERA_NEW", "era"),
            Feed("$l rap hip hop", "G_RAP", "genre"),
            Feed("$l sufi qawwali", "G_SUFI", "genre"),
            Feed("$l ghazal", "G_GHAZAL", "genre"),
            Feed("$l indie", "G_INDIE", "genre"),
            Feed("$l folk", "G_FOLK", "genre"),
            Feed("$l dance edm remix", "G_DANCE", "genre"),
            Feed("$l pop hits", "G_POP", "genre"),
            Feed("$l unplugged acoustic", "G_ACOUSTIC", "genre"),
        )
    }

    private fun prefs(c: Context) = c.getSharedPreferences("reco_trainer", Context.MODE_PRIVATE)

    /** Starts a round when one is due (a week since the last, or your languages changed) and on Wi-Fi. */
    fun maybeRun(context: Context, force: Boolean = false) {
        val c = context.applicationContext
        val langs = com.music.aalap.data.preferences.RecoPrefs.languageMix(c).filterValues { it > 0 }.keys.sorted()
        if (langs.isEmpty() || running) return
        val p = prefs(c)
        // v2: four playlists per feed (was two), so each round confirms twice the songs.
        val key = langs.joinToString(",") + "#v2"
        val due = force || p.getString("langs", null) != key || System.currentTimeMillis() - p.getLong("at", 0) > WEEK
        if (!due) return
        // The first round runs on any network (a few MB, once): without it nothing knows its era or
        // genre. Weekly refreshes wait for Wi-Fi.
        val firstRound = p.getLong("at", 0) == 0L
        if (!force && !firstRound && !com.music.aalap.data.preferences.OfflineState.unmetered) { Log.d(TAG, "waiting for Wi-Fi"); return }
        running = true
        Log.d(TAG, "round starting: ${langs.joinToString()}")
        scope.launch {
            try {
                // Resumes where it stopped if the app closed midway.
                var done = if (p.getString("langs", null) == key) p.getInt("done", 0) else 0
                val all = langs.flatMap { l -> feeds(l).map { l to it } }
                val api = com.music.aalap.data.api.Api(c)
                val db = RecoDb.get(c)
                for ((i, pair) in all.withIndex()) {
                    if (i < done) continue
                    if (!force && !firstRound && !com.music.aalap.data.preferences.OfflineState.unmetered) break
                    val (lang, feed) = pair
                    runCatching { learn(api, db, lang, feed) }.onFailure { Log.w(TAG, "${feed.query}: $it") }
                    done = i + 1
                    p.edit().putString("langs", key).putInt("done", done).apply()
                    delay(1_500)
                }
                if (done >= all.size) {
                    p.edit().putLong("at", System.currentTimeMillis()).putInt("done", 0).putString("langs", key).apply()
                    VibeStore.invalidate()
                    Log.d(TAG, "round done: ${all.size} feeds in ${langs.joinToString()}")
                }
            } finally { running = false }
        }
    }

    private suspend fun learn(api: com.music.aalap.data.api.Api, db: RecoDb, lang: String, feed: Feed) {
        val playlists = api.getCategoryPlaylists(feed.query).toList()
            .filterIsInstance<Response.Success<List<com.music.aalap.data.entity.LibraryEntry>>>().lastOrNull()?.data.orEmpty().take(4)
        var n = 0
        for (pl in playlists) {
            val songs = api.getPlaylistSongs(pl.spotifyId).toList()
                .filterIsInstance<Response.Success<List<com.music.aalap.data.entity.SongsModel>>>().firstOrNull()?.data.orEmpty().take(80)
            if (songs.isEmpty()) continue
            val cands = songs.map(SongBridge::toCandidate)
            // Covers and karaoke versions match the original by title: only the playlist's own artists' songs count.
            val ids = db.resolveAll(cands).zip(cands).filter { (s, c) -> SongMatcher.artistsOverlap(s.artists, c.artists) }.map { it.first.id }
            // The first playlist is the clearest example; the second still counts, a bit less.
            val w = if (n == 0) 1.0 else 0.8
            db.putTags(ids, feed.tag, feed.kind, w)
            // A Hindi mood playlist mostly holds Hindi songs: a language hint for songs nothing else places.
            db.putTags(ids, "LANG_$lang", "lang", 0.6)
            n++
            delay(600)
        }
    }
}

package com.music.aalap.di

import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import com.music.aalap.data.entity.SongsModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Voice assistants (Google Assistant, Gemini, Bixby, Android Auto) playing music in
 * Aalap: "Hey Google, play Kesariya on Aalap". Android hands the app a "play from
 * search" request, either as an intent (the app opens) or through the media session
 * (screen off, car). Both end up here.
 *
 * What a request can be:
 *  - a song ("Kesariya", "Kesariya by Arijit Singh"): that song, then recommendations;
 *  - an artist ("Arijit Singh", "songs by Diljit"): their top song, then a mix with more of theirs;
 *  - "my liked songs" / "my favourites": Liked Songs, shuffled;
 *  - "Aaj ka Aalap" / "today's playlist" / "my mix";
 *  - a mood ("party songs", "something sad", "bhajans", "workout music"): that mood station;
 *  - nothing ("play music on Aalap"): carry on with what was playing, or Your Mix.
 */
object VoicePlay {
    private const val TAG = "VoicePlay"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Asks the open app to show the player (an intent request opened the app). */
    private val _showPlayer = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val showPlayer: SharedFlow<Unit> = _showPlayer

    /** The last request and what came of it (debug and tests). */
    @Volatile var last: String = ""
        private set

    data class Request(
        val query: String,
        val focus: String = "",
        val title: String = "",
        val artist: String = "",
        val album: String = "",
        val genre: String = "",
        val playlist: String = "",
    )

    /** A "play from search" intent's request (null if it isn't one). */
    fun fromIntent(intent: Intent?): Request? {
        if (intent?.action != MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH) return null
        return fromExtras(intent.getStringExtra(SearchManager.QUERY).orEmpty(), intent.extras)
    }

    fun fromExtras(query: String, extras: Bundle?): Request = Request(
        query = query,
        focus = extras?.getString(MediaStore.EXTRA_MEDIA_FOCUS).orEmpty(),
        title = extras?.getString(MediaStore.EXTRA_MEDIA_TITLE).orEmpty(),
        artist = extras?.getString(MediaStore.EXTRA_MEDIA_ARTIST).orEmpty(),
        album = extras?.getString(MediaStore.EXTRA_MEDIA_ALBUM).orEmpty(),
        genre = extras?.getString("android.intent.extra.genre").orEmpty(),
        playlist = extras?.getString("android.intent.extra.playlist").orEmpty(),
    )

    // ── understanding the request (pure, tested) ────────────────────────────

    sealed class Plan {
        data class Song(val query: String) : Plan()
        data class Artist(val name: String) : Plan()
        data class Mood(val mood: com.music.aalap.data.api.Mood) : Plan()
        data object Liked : Plan()
        data object Today : Plan()
        data object YourMix : Plan()
        data object Resume : Plan()
    }

    private val FILLER = Regex("""^(please\s+)?(play|put on|start|listen to|i want to (hear|listen to)|can you play)\s+""")
    private val ON_APP = Regex("""\s+(on|in|using|with|from)\s+(the\s+)?(aalap|alaap|aalaap|aalap app)(\s+app)?$""")
    private val GENERIC = setOf("", "music", "some music", "songs", "some songs", "something", "anything", "my music", "aalap", "a song", "any song")

    private val MOODS = listOf(
        com.music.aalap.data.api.Mood.ENERGETIC to listOf("energetic", "workout", "gym", "high energy", "pump up", "running", "motivational"),
        com.music.aalap.data.api.Mood.PARTY to listOf("party", "dance", "club", "dj", "bollywood party", "wedding"),
        com.music.aalap.data.api.Mood.HEARTBROKEN to listOf("heartbreak", "heartbroken", "breakup"),
        com.music.aalap.data.api.Mood.SAD to listOf("sad", "emotional", "dard bhare", "crying"),
        com.music.aalap.data.api.Mood.ROMANTIC to listOf("romantic", "love", "pyaar", "ishq"),
        com.music.aalap.data.api.Mood.CHILL to listOf("chill", "lofi", "lo-fi", "lo fi", "relaxing", "relax", "calm", "sleep"),
        com.music.aalap.data.api.Mood.DEVOTIONAL to listOf("devotional", "bhajan", "bhajans", "bhakti", "aarti", "spiritual", "religious"),
        com.music.aalap.data.api.Mood.FOCUS to listOf("focus", "study", "studying", "instrumental", "concentration", "work"),
        com.music.aalap.data.api.Mood.HAPPY to listOf("happy", "feel good", "feel-good", "upbeat", "cheerful"),
        com.music.aalap.data.api.Mood.ROAD_TRIP to listOf("road trip", "driving", "drive", "travel"),
    )
    private val MOOD_TAIL = Regex("""\s+(songs?|music|mix|playlist|vibes?|tracks?|hits)$""")
    private val MOOD_HEAD = Regex("""^(some|a few|my|hindi|punjabi|bollywood|english|something|a)\s+""")

    /** The spoken words, without "play" and "on Aalap". */
    fun clean(query: String): String =
        query.lowercase().trim().replace(Regex("\\s+"), " ").replace(FILLER, "").replace(ON_APP, "").trim()

    fun plan(req: Request): Plan {
        val q = clean(req.query)
        val focus = req.focus.lowercase()
        if (req.playlist.isNotBlank() || focus.contains("playlist")) {
            val p = clean(req.playlist.ifBlank { req.query })
            if (Regex("""like|favou?rite""").containsMatchIn(p)) return Plan.Liked
        }
        if (focus.contains("artist") && (req.artist.isNotBlank() || q.isNotBlank())) return Plan.Artist(req.artist.ifBlank { q })
        if (focus.contains("audio/") || req.title.isNotBlank()) {
            val t = req.title.ifBlank { q }
            if (t.isNotBlank()) return Plan.Song(listOf(t, req.artist).filter { it.isNotBlank() }.joinToString(" "))
        }
        if (q in GENERIC) return Plan.Resume
        if (Regex("""^(my )?(liked|favou?rite|loved)( songs?| music| tracks?)?$|^my (likes|favou?rites)$""").matches(q)) return Plan.Liked
        if (Regex("""^(aaj ka aalap|aaj ka alaap|today'?s (playlist|mix|songs)|my daily (mix|playlist)|daily mix)$""").matches(q)) return Plan.Today
        if (Regex("""^(my mix|your mix|a mix for me|something i like|my taste|songs i like)$""").matches(q)) return Plan.YourMix
        // A mood: only when the words are just the mood ("party songs", "some sad music"),
        // so a song called "Love Me Like You Do" still plays that song.
        val m = q.replace(MOOD_HEAD, "").replace(MOOD_TAIL, "").trim()
        for ((mood, words) in MOODS) if (m in words) return Plan.Mood(mood)
        // "songs by X", "X songs", "music by X": an artist.
        Regex("""^(songs?|music|tracks?|hits) (by|of) (.+)$""").find(q)?.let { return Plan.Artist(it.groupValues[3]) }
        Regex("""^(.+?)( ke| ki)? (songs|gaane|hits)$""").find(q)?.let { return Plan.Artist(it.groupValues[1]) }
        return Plan.Song(q)
    }

    // ── doing it ────────────────────────────────────────────────────────────

    /**
     * Carries out [req]. [fromApp]: the request opened the app, so it shows the player.
     */
    fun handle(context: Context, state: CurrentSongState, req: Request, fromApp: Boolean) {
        val app = context.applicationContext
        val plan = plan(req)
        Log.d(TAG, "'${req.query}' (focus ${req.focus}) → $plan")
        last = "'${req.query}' → $plan"
        scope.launch {
            val ok = runCatching { run(app, state, plan) }.onFailure { Log.w(TAG, "failed: $it") }.getOrDefault(null)
            withContext(Dispatchers.Main) {
                if (ok != null) {
                    last = "'${req.query}' → $plan → $ok"
                    if (fromApp) _showPlayer.tryEmit(Unit)
                } else {
                    last = "'${req.query}' → $plan → nothing found"
                    android.widget.Toast.makeText(app, "Couldn't find \"${clean(req.query).ifBlank { req.query }}\" on Aalap", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    /** Plays [plan]; returns what's playing (null if nothing was found). */
    private suspend fun run(context: Context, state: CurrentSongState, plan: Plan): String? {
        val api = com.music.aalap.data.api.Api(context)
        return when (plan) {
            is Plan.Song -> {
                val song = findSong(context, plan.query) ?: return null
                withContext(Dispatchers.Main) { SmartPlay.single(context, state, song) }
                "${song.title} — ${song.singer}"
            }
            is Plan.Artist -> {
                val top = api.artistTopCandidates(plan.name)
                val db = com.music.aalap.data.recommendation.engine.RecoDb.get(context)
                val songs = db.resolveAll(top).map(com.music.aalap.data.recommendation.engine.SongBridge::toSongsModel)
                if (songs.isEmpty()) return findSong(context, plan.query())?.let { s ->
                    withContext(Dispatchers.Main) { SmartPlay.single(context, state, s) }; "${s.title} — ${s.singer}"
                }
                val first = songs.take(5).random()
                withContext(Dispatchers.Main) { SmartPlay.fromList(context, state, first, songs, plan.name) }
                "${plan.name}: ${first.title}"
            }
            Plan.Liked -> {
                val liked = com.music.aalap.data.preferences.LikedLibrary.all(context).shuffled()
                if (liked.isEmpty()) return null
                withContext(Dispatchers.Main) { SmartPlay.playAll(context, state, liked, "Liked Songs") }
                "Liked Songs (${liked.size})"
            }
            Plan.Today -> {
                val list = com.music.aalap.data.recommendation.HomeMixes.todayPlaylist(context).map { it.song }
                if (list.isEmpty()) return null
                withContext(Dispatchers.Main) { SmartPlay.playAll(context, state, list, "Aaj ka Aalap") }
                "Aaj ka Aalap (${list.size})"
            }
            Plan.YourMix -> playYourMix(context, state)
            is Plan.Mood -> {
                val list = com.music.aalap.data.recommendation.AalapRadio.moodRadio(context, plan.mood)
                if (list.isEmpty()) return null
                withContext(Dispatchers.Main) { SmartPlay.playAll(context, state, list, plan.mood.label) }
                "${plan.mood.label} station"
            }
            Plan.Resume -> withContext(Dispatchers.Main) {
                when {
                    state.title.value.isNotBlank() && state.queue.value.isNotEmpty() -> {
                        if (!SongPlayer.isPlaying()) {
                            val cur = state.queue.value.firstOrNull { it.id == state.songId.value }
                            if (cur != null && SongPlayer.exoPlayer?.mediaItemCount == 0) SongPlayer.playSong(cur.url, context) else SongPlayer.play()
                            state.updatePlayingState(true)
                        }
                        state.title.value
                    }
                    else -> com.music.aalap.data.preferences.loadLastPlayback(context)?.first?.let { s ->
                        SmartPlay.single(context, state, s); s.title
                    }
                } ?: withContext(Dispatchers.IO) { playYourMix(context, state) }
            }
        }
    }

    private fun Plan.Artist.query() = name

    private suspend fun playYourMix(context: Context, state: CurrentSongState): String? {
        val mix = com.music.aalap.data.recommendation.HomeMixes.yourMix(context)
        if (mix.isEmpty()) return null
        withContext(Dispatchers.Main) { SmartPlay.playAll(context, state, mix, "Your Mix") }
        return "Your Mix (${mix.size})"
    }

    /** Titles + artists you play or like (lower case), to prefer your version of a song. */
    private fun known(context: Context): Set<String> = runCatching {
        (com.music.aalap.data.preferences.getListeningHistory(context).map { it.title to it.singer } +
            com.music.aalap.data.preferences.LikedLibrary.all(context).map { it.title to it.singer })
            .map { (t, a) -> norm(t) + "|" + norm(a.substringBefore(",")) }.toSet()
    }.getOrDefault(emptySet())

    private fun norm(s: String) = s.lowercase().replace(Regex("""\(.*?\)|\[.*?\]|-.*$"""), " ").replace(Regex("[^\\p{L}\\p{N} ]"), " ").replace(Regex("\\s+"), " ").trim()

    /**
     * Of Spotify's results for the spoken words, the one meant: an exact title beats a
     * similar one ("Kesariya" over "Kesariyo Rang"), a named artist must match, and your
     * own songs (played or liked) win ties. Spotify's order breaks the rest.
     */
    fun bestMatch(query: String, results: List<com.music.aalap.data.recommendation.engine.Candidate>, known: Set<String> = emptySet()): com.music.aalap.data.recommendation.engine.Candidate? {
        val q = norm(query)
        // Spotify always returns something; a result must share a word with what was said
        // (spelling may differ a little: "kesriya" ~ "kesariya"), or nothing was found.
        val words = q.split(" ").filter { it.length >= 3 && it !in setOf("the", "and", "song", "songs", "from", "by") }
        fun related(c: com.music.aalap.data.recommendation.engine.Candidate): Boolean {
            if (words.isEmpty()) return true
            val theirs = norm(c.title + " " + c.artists.replace(",", " ")).split(" ").filter { it.length >= 3 }
            return words.any { w -> theirs.any { t -> close(w, t) } }
        }
        val relevant = results.filter(::related)
        if (relevant.isEmpty()) return null
        return relevant.withIndex().maxByOrNull { (i, c) ->
            val title = norm(c.title)
            val artists = c.artists.split(",").map { norm(it) }.filter { it.isNotBlank() }
            val namedArtist = artists.any { it.isNotBlank() && q.contains(it) }
            val rest = artists.fold(q) { acc, a -> acc.replace(a, " ") }.replace(Regex("""\b(by|from|of|song)\b"""), " ").replace(Regex("\\s+"), " ").trim()
            var score = 0.0
            score += when {
                title == q || title == rest -> 4.0
                rest.isNotBlank() && (title.startsWith(rest) || rest.startsWith(title)) -> 2.5
                rest.isNotBlank() && title.contains(rest) -> 1.5
                else -> 0.0
            }
            if (namedArtist) score += 2.0
            // Each word you said that's in the title or the artists (small spelling differences allowed).
            val titleWords = title.split(" ").filter { it.length >= 3 }
            val artistWords = artists.flatMap { it.split(" ") }.filter { it.length >= 3 }
            words.forEach { w ->
                when {
                    titleWords.any { close(w, it) } -> score += 1.2
                    artistWords.any { close(w, it) } -> score += 1.0
                }
            }
            if (known.contains(title + "|" + artists.firstOrNull().orEmpty())) score += 1.0
            score - i * 0.15
        }?.value
    }

    /** Same word, allowing a small spelling difference (transliterations vary). */
    private fun close(a: String, b: String): Boolean {
        if (a == b) return true
        if (a.length >= 4 && b.length >= 4 && (a.startsWith(b) || b.startsWith(a))) return true
        val allowed = if (minOf(a.length, b.length) <= 5) 1 else 2
        if (kotlin.math.abs(a.length - b.length) > allowed) return false
        var prev = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            val cur = IntArray(b.length + 1); cur[0] = i
            for (j in 1..b.length) cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1)
            prev = cur
        }
        return prev[b.length] <= allowed
    }

    /** The best song for spoken words: Spotify search; offline, your downloads. */
    private suspend fun findSong(context: Context, query: String): SongsModel? {
        if (query.isBlank()) return null
        if (com.music.aalap.data.preferences.OfflineState.isOffline) {
            val q = query.lowercase()
            return com.music.aalap.data.preferences.getDownloadedSongs(context).firstOrNull { q.contains(it.title.lowercase()) || it.title.lowercase().contains(q) }
        }
        val results = com.music.aalap.data.api.Api(context).searchCandidates(query, 10)
        val c = bestMatch(query, results, known(context)) ?: return null
        val song = com.music.aalap.data.recommendation.engine.RecoDb.get(context).resolve(c)
        return com.music.aalap.data.recommendation.engine.SongBridge.toSongsModel(song)
    }
}

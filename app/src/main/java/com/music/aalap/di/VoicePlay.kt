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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
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
        /** Other ways the recogniser heard it, best first (the in-app mic). */
        val alternatives: List<String> = emptyList(),
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
        /** "Hindi songs", "some Punjabi music": a mix in that language. */
        data class Language(val language: String) : Plan()
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
    private val LANGUAGE = Regex("""^(some |a few |the |top |latest |new |best )?(hindi|punjabi|english|gujarati|tamil|telugu|marathi|bengali|bhojpuri|haryanvi|malayalam|kannada)( film| bollywood| movie)?( song| songs| music| gaane| gane| hits| tracks)?$""")
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
        LANGUAGE.matchEntire(q)?.let { return Plan.Language(it.groupValues[2]) }
        if (Regex("""^(my )?(liked|favou?rite|loved)( songs?| music| tracks?)?$|^my (likes|favou?rites)$""").matches(q)) return Plan.Liked
        if (isToday(q)) return Plan.Today
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

    /**
     * "Aaj ka Aalap", however it was heard: "aaj ka alap", "aj ka aalaap", "aaj ke aalap",
     * "today's aalap", "today's playlist"… but not "Aaj Ki Raat". Compares the sound
     * (doubled letters squashed) allowing two small differences.
     */
    fun isToday(q: String): Boolean {
        if (Regex("""^(today'?s (playlist|mix|songs|aalap|alap|alaap)|my daily (mix|playlist)|daily mix|aalap (of|for) (the day|today)|aaj ka playlist|aaj ki playlist)$""").matches(q)) return true
        val joined = squash(q.replace(Regex("[^a-z]"), ""))
        if (joined.length !in 5..10) return false
        return dist(joined, "ajkalap") <= 2 && Regex("l[ae]*p|l[ae]*b").containsMatchIn(joined.takeLast(4))
    }

    /** Doubled letters as one ("aalaap" → "alap"; "aa" → "a"). */
    private fun squash(s: String): String = s.replace(Regex("(.)\\1+"), "$1").replace("aa", "a")

    private fun dist(a: String, b: String): Int {
        var prev = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            val cur = IntArray(b.length + 1); cur[0] = i
            for (j in 1..b.length) cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1)
            prev = cur
        }
        return prev[b.length]
    }

    /**
     * The plan for what was said, looking at the recogniser's other hearings too: if
     * one of them is clearly a named list ("Aaj ka Aalap", Liked Songs), that wins over
     * a song search for the first hearing.
     */
    fun planAll(req: Request): Plan {
        val first = plan(req)
        if (first !is Plan.Song) return first
        for (alt in req.alternatives.take(4)) {
            val p = plan(req.copy(query = alt, alternatives = emptyList()))
            if (p == Plan.Today || p == Plan.Liked) return p
        }
        return first
    }

    // ── doing it ────────────────────────────────────────────────────────────

    /** What was played, for the voice overlay. */
    data class Played(val title: String, val subtitle: String, val cover: String = "")

    /**
     * Carries out [req]. [fromApp]: the request came from inside the app (the mic, a
     * link): the voice overlay shows Aalap thinking for a moment, then what it found,
     * and the player opens.
     */
    fun handle(context: Context, state: CurrentSongState, req: Request, fromApp: Boolean) {
        val app = context.applicationContext
        val plan = planAll(req)
        Log.d(TAG, "'${req.query}' (focus ${req.focus}, alts ${req.alternatives}) → $plan")
        last = "'${req.query}' → $plan"
        val heard = clean(req.query).ifBlank { req.query }
        if (fromApp) VoiceUi.state.value = VoiceUi.State.Thinking(heard)
        val started = System.currentTimeMillis()
        // Take a moment, so it's clear Aalap looked (and the "thinking" step is seen).
        val ready: suspend () -> Unit = {
            if (fromApp) kotlinx.coroutines.delay((1_100 - (System.currentTimeMillis() - started)).coerceAtLeast(0))
        }
        scope.launch {
            val ok = runCatching { run(app, state, plan, req, ready) }.onFailure { Log.w(TAG, "failed: $it") }.getOrDefault(null)
            withContext(Dispatchers.Main) {
                if (ok != null) {
                    last = "'${req.query}' → $plan → ${ok.title} — ${ok.subtitle}"
                    if (fromApp) {
                        VoiceUi.state.value = VoiceUi.State.Found(ok.title, ok.subtitle, ok.cover)
                        _showPlayer.tryEmit(Unit)
                    }
                } else {
                    last = "'${req.query}' → $plan → nothing found"
                    if (fromApp) VoiceUi.state.value = VoiceUi.State.Failed("Couldn't find \"$heard\". Try saying the song and the singer.")
                    else android.widget.Toast.makeText(app, "Couldn't find \"$heard\" on Aalap", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    /** Works out [plan] without playing it (debug: what a request would play). */
    suspend fun dryRun(context: Context, req: Request): String {
        val plan = planAll(req)
        if (plan !is Plan.Song) return "$plan"
        val results = search(context, plan.query, req.alternatives)
        val ranked = rank(plan.query, results, known(context))
        return "$plan\n" + ranked.take(8).joinToString("\n") { (c, sc) -> "%.2f  %s — %s  [%s]".format(sc, c.title, c.artists, c.album) }
    }

    /** Plays [plan]; returns what's playing (null if nothing was found). */
    private suspend fun run(context: Context, state: CurrentSongState, plan: Plan, req: Request, ready: suspend () -> Unit): Played? {
        val api = com.music.aalap.data.api.Api(context)
        return when (plan) {
            is Plan.Song -> {
                val song = findSong(context, plan.query, req.alternatives) ?: return null
                ready()
                withContext(Dispatchers.Main) { SmartPlay.single(context, state, song) }
                // Only say it's playing once it really is (a stream that fails makes the player
                // move on to the next song, and the card would name one song while another plays).
                if (!playingNow(state, song)) {
                    Log.w(TAG, "'${song.title}' didn't start, trying again")
                    withContext(Dispatchers.Main) { SmartPlay.single(context, state, song) }
                    if (!playingNow(state, song)) { last = "'${req.query}' → $plan → couldn't play"; return null }
                }
                Played(song.title, listOf(song.singer.substringBefore(",").trim(), song.album).filter { it.isNotBlank() }.distinct().joinToString(" · "), song.coverUri)
            }
            is Plan.Artist -> {
                var top = api.artistTopCandidates(plan.name)
                if (top.isEmpty()) {
                    kotlinx.coroutines.delay(1_200)
                    runCatching { com.music.aalap.data.api.SpotifyTokenProvider.ensureToken(context, force = true) }
                    top = api.artistTopCandidates(plan.name)
                }
                val db = com.music.aalap.data.recommendation.engine.RecoDb.get(context)
                val songs = db.resolveAll(top).map(com.music.aalap.data.recommendation.engine.SongBridge::toSongsModel)
                if (songs.isEmpty()) return findSong(context, plan.query(), req.alternatives)?.let { s ->
                    ready(); withContext(Dispatchers.Main) { SmartPlay.single(context, state, s) }; Played(s.title, s.singer.substringBefore(","), s.coverUri)
                }
                val first = songs.take(5).random()
                ready()
                withContext(Dispatchers.Main) { SmartPlay.fromList(context, state, first, songs, plan.name) }
                Played(first.title, "${plan.name.split(" ").joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }} mix", first.coverUri)
            }
            Plan.Liked -> {
                val liked = com.music.aalap.data.preferences.LikedLibrary.all(context).shuffled()
                if (liked.isEmpty()) return null
                ready()
                withContext(Dispatchers.Main) { SmartPlay.playAll(context, state, liked, "Liked Songs") }
                Played("Liked Songs", "${liked.size} songs, shuffled", liked.first().coverUri)
            }
            Plan.Today -> {
                val list = com.music.aalap.data.recommendation.HomeMixes.todayPlaylist(context).map { it.song }
                if (list.isEmpty()) return null
                ready()
                withContext(Dispatchers.Main) { SmartPlay.playAll(context, state, list, "Aaj ka Aalap") }
                Played("Aaj ka Aalap", "Today's ${list.size} songs", list.first().coverUri)
            }
            Plan.YourMix -> { ready(); playYourMix(context, state)?.let { Played(it, "Made from your taste") } }
            is Plan.Language -> {
                val list = languageMix(context, plan.language)
                if (list.isEmpty()) return null
                ready()
                val name = plan.language.replaceFirstChar { it.uppercase() } + " songs"
                withContext(Dispatchers.Main) { SmartPlay.playAll(context, state, list, name) }
                if (!playingNow(state, list.first())) return null
                Played(name, "${list.size} songs, starting with ${list.first().title}", list.first().coverUri)
            }
            is Plan.Mood -> {
                val list = com.music.aalap.data.recommendation.AalapRadio.moodRadio(context, plan.mood)
                if (list.isEmpty()) return null
                ready()
                withContext(Dispatchers.Main) { SmartPlay.playAll(context, state, list, plan.mood.label) }
                Played("${plan.mood.label} station", "${list.size} songs", list.first().coverUri)
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
            }?.let { Played(it, "Carrying on") }
        }
    }

    private fun Plan.Artist.query() = name

    /** Waits (up to ~12 s) for [song] to be the one playing; false if the player moved on or never started. */
    private suspend fun playingNow(state: CurrentSongState, song: SongsModel): Boolean {
        repeat(24) {
            kotlinx.coroutines.delay(500)
            val (current, playing) = withContext(Dispatchers.Main) { state.songId.value to SongPlayer.isPlaying() }
            if (current != song.id) return false
            if (playing) return true
        }
        return false
    }

    /** Songs in [language] from what you have (Aaj ka Aalap, Your Mix, Liked Songs), topped up from search. */
    private suspend fun languageMix(context: Context, language: String): List<SongsModel> {
        val db = com.music.aalap.data.recommendation.engine.RecoDb.get(context)
        // Only what's already made (building today's mixes can take long): Aaj ka Aalap, Your Mix, your likes.
        val pool = (runCatching { com.music.aalap.data.recommendation.HomeMixes.todayCached().map { it.song } }.getOrDefault(emptyList()) +
            com.music.aalap.data.recommendation.HomeMixes.yourMixCached(context) +
            com.music.aalap.data.preferences.LikedLibrary.all(context)).distinctBy { it.id }
        val songs = kotlinx.coroutines.withTimeoutOrNull(6_000) { db.resolveAll(pool.map(com.music.aalap.data.recommendation.engine.SongBridge::toCandidate)) }.orEmpty()
        val mine = songs.filter { com.music.aalap.data.recommendation.engine.VibeStore.language(context, it) == language }
        val picked = mine.shuffled().take(40).toMutableList()
        if (picked.size < 12) {
            // Not enough of your own: the most played songs in that language.
            val found = kotlinx.coroutines.withTimeoutOrNull(10_000) { com.music.aalap.data.api.Api(context).searchCandidates("top $language songs", 30) }.orEmpty()
            picked += db.resolveAll(found).filter { s -> picked.none { it.id == s.id } }
        }
        android.util.Log.d("VoicePlay", "$language mix: ${mine.size} of yours in ${pool.size}, ${picked.size} in all")
        return picked.map(com.music.aalap.data.recommendation.engine.SongBridge::toSongsModel)
    }

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

    private val VERSION = Regex("""\b(remix|lofi|lo fi|slowed|reverb|sped up|speed up|cover|karaoke|instrumental|unplugged|reprise|mashup|8d|live|female version|male version|lyrical|version)\b""")
    private val SMALL = setOf("the", "and", "song", "songs", "from", "by", "tha", "thi", "the", "hai", "movie", "film")

    /**
     * Of the search results for the spoken words, the one meant, best first with scores:
     *  - an exact title beats a similar one ("Kesariya" over "Kesariyo Rang");
     *  - a named artist must match, and a film or album you name counts
     *    ("Saiyaara Ek Tha Tiger" is the 2012 song, not the 2025 film's);
     *  - the original beats a remix, lofi, cover… unless you asked for one;
     *  - your own songs (played or liked) win ties; then the search's own order,
     *    which puts the most played version first.
     */
    fun rank(query: String, results: List<com.music.aalap.data.recommendation.engine.Candidate>, known: Set<String> = emptySet()): List<Pair<com.music.aalap.data.recommendation.engine.Candidate, Double>> {
        val q = norm(query)
        // A result must share a word with what was said (spelling may differ a little:
        // "kesriya" ~ "kesariya"), or nothing was found.
        val words = q.split(" ").filter { it.length >= 3 && it !in SMALL }
        val askedVersion = VERSION.containsMatchIn(q)
        fun related(c: com.music.aalap.data.recommendation.engine.Candidate): Boolean {
            if (words.isEmpty()) return true
            val theirs = norm(c.title + " " + c.artists.replace(",", " ")).split(" ").filter { it.length >= 3 }
            return words.any { w -> theirs.any { t -> close(w, t) } }
        }
        return results.withIndex().filter { related(it.value) }.map { (i, c) ->
            val title = norm(c.title)
            val fullTitle = c.title.lowercase()
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
            // Each word you said that's in the title, the artists or the film / album.
            val titleWords = title.split(" ").filter { it.length >= 3 }
            val artistWords = artists.flatMap { it.split(" ") }.filter { it.length >= 3 }
            val albumWords = norm(c.album + " " + fullTitle.substringAfter("(", "").substringAfter("from", "")).split(" ").filter { it.length >= 2 && it !in SMALL }
            var albumHits = 0
            words.forEach { w ->
                when {
                    titleWords.any { close(w, it) } -> score += 1.2
                    artistWords.any { close(w, it) } -> score += 1.0
                    albumWords.any { close(w, it) } -> { score += 1.4; albumHits++ }
                }
            }
            // Short words of a film name ("ek", "tha") count once the film is clearly named.
            if (albumHits > 0) q.split(" ").filter { it.length == 2 }.forEach { w -> if (albumWords.any { it == w }) score += 0.4 }
            if (!askedVersion && VERSION.containsMatchIn(fullTitle)) score -= 1.8
            if (known.contains(title + "|" + artists.firstOrNull().orEmpty())) score += 1.0
            c to score - i * 0.15
        }.sortedByDescending { it.second }
    }

    fun bestMatch(query: String, results: List<com.music.aalap.data.recommendation.engine.Candidate>, known: Set<String> = emptySet()) =
        rank(query, results, known).firstOrNull()?.first

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

    /** Search results for the words and the recogniser's next hearing, merged (first search's order first). */
    private suspend fun search(context: Context, query: String, alternatives: List<String>): List<com.music.aalap.data.recommendation.engine.Candidate> = kotlinx.coroutines.coroutineScope {
        val api = com.music.aalap.data.api.Api(context)
        val alt = alternatives.map { plan(Request(it)) }.filterIsInstance<Plan.Song>().map { it.query }.firstOrNull { it != query }
        suspend fun once() = listOfNotNull(query, alt).map { q -> async { api.searchCandidates(q, 10) } }.awaitAll()
        var lists = once()
        // Right after Aalap starts the search sign-in may not be ready yet: refresh it once and retry.
        if (lists.all { it.isEmpty() }) {
            kotlinx.coroutines.delay(1_200)
            runCatching { com.music.aalap.data.api.SpotifyTokenProvider.ensureToken(context, force = true) }
            lists = once()
        }
        lists.flatten().distinctBy { it.spotifyId.ifBlank { it.title + it.artists } }
    }

    /** The best song for spoken words: search; offline, your downloads. */
    private suspend fun findSong(context: Context, query: String, alternatives: List<String> = emptyList()): SongsModel? {
        if (query.isBlank()) return null
        if (com.music.aalap.data.preferences.OfflineState.isOffline) {
            val q = query.lowercase()
            return com.music.aalap.data.preferences.getDownloadedSongs(context).firstOrNull { q.contains(it.title.lowercase()) || it.title.lowercase().contains(q) }
        }
        val c = bestMatch(query, search(context, query, alternatives), known(context)) ?: return null
        val song = com.music.aalap.data.recommendation.engine.RecoDb.get(context).resolve(c)
        return com.music.aalap.data.recommendation.engine.SongBridge.toSongsModel(song)
    }
}

package com.music.aalap.data.recommendation

import android.content.Context
import com.music.aalap.data.api.Api
import com.music.aalap.data.api.ArtistPicker
import com.music.aalap.data.api.YouTubeLibrary
import com.music.aalap.data.entity.ArtistsModel
import com.music.aalap.data.entity.HomeItem
import com.music.aalap.data.entity.LibraryEntry
import com.music.aalap.data.entity.SongsModel
import com.music.aalap.data.recommendation.engine.Candidate
import com.music.aalap.data.recommendation.engine.Diversifier
import com.music.aalap.data.recommendation.engine.Personalizer
import com.music.aalap.data.recommendation.engine.QueueEngine
import com.music.aalap.data.recommendation.engine.RecoDb
import com.music.aalap.data.recommendation.engine.SongBridge
import com.music.aalap.data.recommendation.engine.SongMatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.toList
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalTime
import java.util.Random

/**
 * Everything on Home that's made for you, built from all your music: plays in Aalap,
 * likes and follows on Spotify and YouTube Music, your YouTube Music history and
 * suggestions, Spotify's top artists and an Apple Music import. No section comes
 * from a single service.
 */
object HomeMixes {
    private const val PREF = "home_mixes"
    private fun prefs(c: Context) = c.getSharedPreferences(PREF, Context.MODE_PRIVATE)
    private fun key(name: String) = SongMatcher.artistKeys(name).firstOrNull().orEmpty()

    /** "2026-10-22-5": changes every 3 hours. */
    private fun window3h() = "${LocalDate.now()}-${LocalTime.now().hour / 3}"
    private fun today() = LocalDate.now().toString()

    // ── small JSON cache ────────────────────────────────────────────────────

    private fun songsJson(list: List<SongsModel>) = JSONArray().also { a ->
        list.forEach { m ->
            a.put(JSONObject().apply {
                put("id", m.id); put("title", m.title); put("album", m.album); put("singer", m.singer)
                put("cover", m.coverUri); put("url", m.url); put("sid", m.spotifyTrackId); put("explicit", m.explicit); put("dur", m.durationMs)
            })
        }
    }

    private fun songsFrom(a: JSONArray): List<SongsModel> = (0 until a.length()).map { i ->
        a.getJSONObject(i).let { o ->
            SongsModel(o.getInt("id"), o.optString("title"), o.optString("album"), o.optString("singer"),
                o.optString("cover"), o.optString("url"), o.optString("sid"), o.optBoolean("explicit"), o.optInt("dur"))
        }
    }

    private fun readSongs(name: String, stamp: String): List<SongsModel>? = runCatching {
        if (prefs(appCtx).getString("$name.stamp", null) != stamp) return null
        songsFrom(JSONArray(prefs(appCtx).getString(name, "[]"))).takeIf { it.isNotEmpty() }
    }.getOrNull()

    private fun writeSongs(name: String, stamp: String, songs: List<SongsModel>) {
        if (songs.isEmpty()) return
        prefs(appCtx).edit().putString(name, songsJson(songs).toString()).putString("$name.stamp", stamp)
            .putLong("$name.at", System.currentTimeMillis()).apply()
    }

    private val appCtx get() = com.music.aalap.MyApplication.instance

    // ── Your Mix: rebuilt every 3 hours ─────────────────────────────────────

    data class Mix(val title: String, val subtitle: String, val covers: List<String>, val songs: List<SongsModel>)

    /** When Your Mix was last rebuilt (for "Updated 3 PM"). */
    fun yourMixUpdatedAt(c: Context): Long = prefs(c).getLong("yourmix.at", 0)

    suspend fun yourMix(c: Context, force: Boolean = false): List<SongsModel> {
        val stamp = window3h()
        if (!force) readSongs("yourmix", stamp)?.let { return it }
        val songs = AalapRadio.yourMix(c, salt = stamp.hashCode().toLong())
        writeSongs("yourmix", stamp, songs)
        return songs
    }

    // ── Quick picks: YouTube Music's own suggestions + the engine, ranked for you ──

    /**
     * Quick picks: new every day and on every pull-to-refresh. A third each: what's
     * trending (India's Top 50 and Trending charts), YouTube Music's suggestions for
     * you, and new songs the engine found from your taste, all ordered for you.
     */
    suspend fun quickPicks(c: Context, force: Boolean = false): List<SongsModel> {
        val stamp = today() + "#" + prefs(c).getInt("quick.gen", 0)
        if (force) {
            prefs(c).edit().putInt("quick.gen", prefs(c).getInt("quick.gen", 0) + 1).apply()
            return quickPicks(c, force = false)
        }
        readSongs("quick", stamp)?.let { return it }
        val db = RecoDb.get(c)
        val fromYouTube = if (com.music.aalap.data.preferences.isYoutubeLoggedIn(c))
            com.metrolist.innertube.YouTube.homeSongs().getOrNull().orEmpty()
                .map { Candidate(title = it.title, artists = it.artists, durationSec = it.durationSec, album = it.album, cover = it.thumbnail, ytId = it.videoId) }
                .let { db.resolveAll(it) }.map(SongBridge::toSongsModel)
        else emptyList()
        val rnd = Random(stamp.hashCode().toLong())
        // Fresh engine picks (Spotify + YouTube + JioSaavn) from your taste.
        val engine = runCatching { QueueEngine.freshPicks(c, salt = stamp.hashCode().toLong(), limit = 15) }.getOrDefault(emptyList())
        // What's trending now, the songs closest to your taste first.
        val trending = QueueEngine.rerank(c, trendingSongs(c), familiar = 0.3f).take(20).shuffled(rnd).take(10)
        val yt = QueueEngine.rerank(c, fromYouTube, familiar = 0.5f).take(10)
        val mixed = ArrayList<SongsModel>()
        for (i in 0 until 10) { trending.getOrNull(i)?.let(mixed::add); yt.getOrNull(i)?.let(mixed::add); engine.getOrNull(i)?.let(mixed::add) }
        val result = Diversifier.spreadArtists(mixed.distinctBy { it.id }.take(24)) { it.singer }
        writeSongs("quick", stamp, result)
        return result
    }

    private const val TOP50_INDIA = "37i9dQZEVXbLZ52XmnySJg"
    private const val TOP50_GLOBAL = "37i9dQZEVXbMDoHDwVN2tF"
    private const val VIRAL50_INDIA = "37i9dQZF1DXbVhgADFy3im" // Spotify's "Trending Now India"

    /** A chart's songs, cached for the day. */
    private suspend fun chart(c: Context, id: String): List<SongsModel> {
        readSongs("chart.$id", today())?.let { return it } // (empty results aren't cached)
        val songs = runCatching {
            Api(c).getPlaylistSongs(id).toList()
                .filterIsInstance<com.music.aalap.data.api.Response.Success<List<SongsModel>>>().lastOrNull()?.data.orEmpty()
        }.getOrDefault(emptyList()).take(50)
        writeSongs("chart.$id", today(), songs)
        return songs
    }

    /** Today's Top 50 India + Trending India, interleaved. */
    suspend fun trendingSongs(c: Context): List<SongsModel> {
        val lists = listOf(chart(c, TOP50_INDIA), chart(c, VIRAL50_INDIA))
        val out = ArrayList<SongsModel>()
        for (i in 0 until 50) lists.forEach { l -> l.getOrNull(i)?.let(out::add) }
        return out.distinctBy { it.id }
    }

    // ── Aaj ka Aalap: today's 30, rebuilt every morning ────────────────────

    /** A song in today's playlist and why it's there ("Trending", "Top hit", "Your taste", "Liked"); [matches] = close to your taste. */
    data class TodaySong(val song: SongsModel, val reason: String, val matches: Boolean)

    fun todayUpdatedAt(c: Context): Long = prefs(c).getLong("today.at", 0)

    /** The last Your Mix made (no network, any age); empty if none yet. */
    fun yourMixCached(c: Context): List<SongsModel> = runCatching { songsFrom(JSONArray(prefs(c).getString("yourmix", "[]"))) }.getOrDefault(emptyList())

    /** The cached Aaj ka Aalap (no network); empty until it's been built today. */
    fun todayCached(): List<TodaySong> =
        if (prefs(appCtx).getString("today.stamp", null) == today()) readToday().orEmpty() else emptyList()

    /**
     * Why a song is worth a tag anywhere in the app: "Trending" (in today's Trending
     * chart), "Top hit" (today's Top 50), "Liked" (liked on any service) or
     * "Your taste" (fits your taste closely). Null when none apply. Works offline from
     * today's cached charts.
     */
    // In-memory lookups for tags (rebuilt once a day), so tagging long lists stays smooth.
    @Volatile private var memoDay = ""
    private var chartMemo: Map<String, Set<String>> = emptyMap()
    private var todayMemo: Map<String, TodaySong> = emptyMap()
    private fun songKeys(s: SongsModel) = listOf("id:${s.id}", "t:${s.title.lowercase()}|${key(s.singer)}")

    @Synchronized
    private fun ensureMemo() {
        val day = today() + "#" + prefs(appCtx).getLong("today.at", 0)
        if (memoDay == day) return
        chartMemo = listOf(VIRAL50_INDIA, TOP50_INDIA, TOP50_GLOBAL).associateWith { id ->
            readSongs("chart.$id", today()).orEmpty().flatMap(::songKeys).toSet()
        }
        todayMemo = todayCached().flatMap { t -> songKeys(t.song).map { it to t } }.toMap()
        memoDay = day
    }

    /**
     * [personalOnly]: only "Liked" / "Your taste" (for a chart page, where every song is
     * already a top hit).
     */
    fun reasonFor(c: Context, song: SongsModel, personalOnly: Boolean = false): TodaySong? {
        ensureMemo()
        val keys = songKeys(song)
        // A song in today's 30 keeps the tag it has there, so it reads the same everywhere.
        if (!personalOnly) keys.firstNotNullOfOrNull { todayMemo[it] }?.let { return it.copy(song = song) }
        fun inChart(id: String) = !personalOnly && keys.any { it in chartMemo[id].orEmpty() }
        val taste = runCatching {
            Personalizer.profile(c).taste(SongBridge.toCandidate(song), RecoDb.get(c).songBySpotifyId(song.spotifyTrackId)?.id)
        }.getOrDefault(0.0)
        val matches = taste >= 0.3
        val liked = com.music.aalap.data.preferences.isSongLiked(c, song.id.toString())
        val why = when {
            inChart(VIRAL50_INDIA) -> "Trending"
            inChart(TOP50_INDIA) || inChart(TOP50_GLOBAL) -> "Top hit"
            // Your taste first: liking a song adds a "Liked" tag beside it (see SongTags).
            matches -> "Your taste"
            liked -> "Liked"
            else -> return null
        }
        return TodaySong(song, why, matches)
    }

    /** Tags a list (Quick picks) with reasons; songs with none show as "For you". */
    fun tagged(c: Context, songs: List<SongsModel>): List<TodaySong> =
        songs.map { reasonFor(c, it) ?: TodaySong(it, "For you", false) }

    /**
     * Aaj ka Aalap: 30 songs, new every day. About 8 trending now, 7 of today's top
     * hits, 9 new songs for your taste and 6 of your likes from every service, each
     * group picked closest to your taste and interleaved so no artist repeats.
     */
    suspend fun todayPlaylist(c: Context, force: Boolean = false): List<TodaySong> = coroutineScope {
        val stamp = today()
        val p = prefs(c)
        if (!force && p.getString("today.stamp", null) == stamp) readToday()?.takeIf { it.size >= 25 }?.let { return@coroutineScope it }
        QueueEngine.ensureImported(c)
        val rnd = Random(stamp.hashCode().toLong())
        val trendingJob = async { chart(c, VIRAL50_INDIA) }
        val topJob = async { (chart(c, TOP50_INDIA) + chart(c, TOP50_GLOBAL).take(20)).distinctBy { it.id } }
        val tasteJob = async { runCatching { QueueEngine.freshPicks(c, salt = stamp.hashCode().toLong() + 7, limit = 20) }.getOrDefault(emptyList()) }
        val liked = com.music.aalap.data.preferences.LikedLibrary.all(c)

        fun pick(list: List<SongsModel>, n: Int, pool: Int) = QueueEngine.rerank(c, list, familiar = 0.3f).take(pool).shuffled(rnd).take(n)
        val trending = pick(trendingJob.await(), 8, 18)
        val top = pick(topJob.await().filter { t -> trending.none { it.id == t.id } }, 7, 16)
        val taste = tasteJob.await().take(9)
        // Likes: weighted to recent ones, a few older gems.
        val likes = (liked.take(30).shuffled(rnd).take(4) + liked.drop(30).shuffled(rnd).take(2))

        val groups = listOf(taste.map { it to "Your taste" }, trending.map { it to "Trending" }, likes.map { it to "Liked" }, top.map { it to "Top hit" })
        val mixed = ArrayList<Pair<SongsModel, String>>()
        for (i in 0 until 10) groups.forEach { g -> g.getOrNull(i)?.let(mixed::add) }
        val sameSong: (Pair<SongsModel, String>) -> String = { "${it.first.title.lowercase()}|${key(it.first.singer)}" }
        val unique = mixed.distinctBy(sameSong).toMutableList()
        // Short of 30 (little taste data yet, few likes, or overlaps between groups): top up
        // from the rest of each pool, so the list is always a full 30 when songs exist.
        if (unique.size < 30) {
            val seen = unique.mapTo(HashSet(), sameSong)
            val spare = tasteJob.await().drop(9).map { it to "Your taste" } +
                trendingJob.await().map { it to "Trending" } +
                topJob.await().map { it to "Top hit" } +
                liked.shuffled(rnd).map { it to "Liked" }
            for (s in spare) {
                if (unique.size >= 30) break
                if (seen.add(sameSong(s))) unique += s
            }
        }
        val ordered = Diversifier.spreadArtists(unique) { it.first.singer }.take(30)
        // Mark songs that fit your taste closely, whatever brought them in.
        val profile = Personalizer.profile(c)
        val result = ordered.map { (s, why) ->
            val score = profile.taste(SongBridge.toCandidate(s), RecoDb.get(c).songBySpotifyId(s.spotifyTrackId)?.id)
            TodaySong(s, why, why == "Your taste" || why == "Liked" || score >= 0.3)
        }
        // Only a full list is kept for the day; a short one (charts didn't load) is rebuilt next time.
        if (result.size >= 25 && trending.isNotEmpty() && top.isNotEmpty()) {
            val a = JSONArray()
            result.forEach { t -> a.put(JSONObject().put("song", songsJson(listOf(t.song)).getJSONObject(0)).put("why", t.reason).put("match", t.matches)) }
            p.edit().putString("today", a.toString()).putString("today.stamp", stamp).putLong("today.at", System.currentTimeMillis()).apply()
        }
        result
    }

    private fun readToday(): List<TodaySong>? = runCatching {
        val a = JSONArray(prefs(appCtx).getString("today", "[]"))
        (0 until a.length()).map { i ->
            a.getJSONObject(i).let { o ->
                TodaySong(songsFrom(JSONArray().put(o.getJSONObject("song"))).first(), o.optString("why"), o.optBoolean("match"))
            }
        }.takeIf { it.isNotEmpty() }
    }.getOrNull()

    // ── Trending searches (Search page): 5 from YouTube Music + 5 from Spotify, daily ──

    /** A trending search: what the chip says and what it searches for. */
    data class TrendingSearch(val label: String, val query: String)

    /** "KAALI HOODIE (MUSIC VIDEO) … | THE KIDD" → "Kaali Hoodie"; "#Video | हमरी अंगनवा | …" → "हमरी अंगनवा". */
    private fun cleanTitle(raw: String, artists: String): String {
        var t = raw.replace(Regex("""#\S+"""), " ").replace(Regex("""\(.*?\)|\[.*?\]"""), " ")
        val parts = t.split(" | ", " - ", " – ", "|").map { it.trim() }.filter { it.length > 1 }
        val artistKeys = SongMatcher.artistKeys(artists).toSet()
        t = parts.firstOrNull { p -> SongMatcher.artistKeys(p).firstOrNull() !in artistKeys && !p.contains("video", true) && !p.contains("song", true) } ?: parts.firstOrNull() ?: raw
        // Drop "ft. …", upload words and the artists' own names from the label.
        t = t.replace(Regex("""\s(ft\.?|feat\.?|featuring|x)\s.*$""", RegexOption.IGNORE_CASE), " ")
        t = t.replace(Regex("""\b(official|music video|video song|video|lyrical|lyrics?|full song|audio|song)\b""", RegexOption.IGNORE_CASE), " ")
        artists.split(",").map { it.trim() }.filter { it.length > 2 }.forEach { a ->
            t = t.replace(Regex(Regex.escape(a), RegexOption.IGNORE_CASE), " ")
        }
        t = t.replace(Regex("""\s+"""), " ").trim().trimEnd('-', '|', ':', ',').trim()
        // Shouting titles read better in title case.
        if (t.length > 3 && t == t.uppercase() && t.any { it.isLetter() && it.code < 128 }) {
            t = t.lowercase().split(" ").joinToString(" ") { w -> w.replaceFirstChar(Char::titlecase) }
        }
        return t.take(32)
    }

    suspend fun trendingSearches(c: Context): List<TrendingSearch> = coroutineScope {
        val p = prefs(c)
        if (p.getString("searches.stamp", null) == today() + "v2") runCatching {
            val a = JSONArray(p.getString("searches", "[]"))
            val list = (0 until a.length()).map { i -> a.getJSONObject(i).let { TrendingSearch(it.getString("l"), it.getString("q")) } }
            if (list.size >= 8) return@coroutineScope list
        }
        val ytJob = async {
            com.metrolist.innertube.YouTube.trendingSongs().getOrNull().orEmpty().map { s ->
                val label = cleanTitle(s.title, s.artists)
                val artist = s.artists.split(",").firstOrNull()?.trim().orEmpty()
                TrendingSearch(label, "$label $artist".trim())
            }
        }
        val spJob = async {
            chart(c, VIRAL50_INDIA).map { s ->
                val label = cleanTitle(s.title, s.singer)
                TrendingSearch(label, "$label ${s.singer.substringBefore(",").trim()}".trim())
            }
        }
        fun top5(list: List<TrendingSearch>, skip: Set<String>) =
            list.filter { it.label.isNotBlank() && it.label.lowercase() !in skip }.distinctBy { it.label.lowercase() }.take(5)
        val yt = top5(ytJob.await(), emptySet())
        val sp = top5(spJob.await(), yt.mapTo(HashSet()) { it.label.lowercase() })
        val mixed = ArrayList<TrendingSearch>()
        for (i in 0 until 5) { yt.getOrNull(i)?.let(mixed::add); sp.getOrNull(i)?.let(mixed::add) }
        if (mixed.size >= 8) {
            val a = JSONArray(); mixed.forEach { a.put(JSONObject().put("l", it.label).put("q", it.query)) }
            p.edit().putString("searches", a.toString()).putString("searches.stamp", today() + "v2").apply()
        }
        mixed
    }

    // ── Recently played: your plays in Aalap, then on YouTube Music ─────────

    fun recentlyPlayed(c: Context, limit: Int = 20): List<SongsModel> {
        val aalap = com.music.aalap.data.preferences.getListeningHistory(c)
            .filter { it.url.isNotBlank() }
            .distinctBy { "${it.title.lowercase()}|${key(it.singer)}" }
            .map { SongsModel(it.songId, it.title, it.album, it.singer, it.image, it.url, it.spotifyTrackId, durationMs = it.durationMs) }
        val seen = aalap.mapTo(HashSet()) { "${it.title.lowercase()}|${key(it.singer)}" }
        val yt = YouTubeLibrary.historySongs(c).filter { "${it.title.lowercase()}|${key(it.singer)}" !in seen }
        return (aalap + yt).take(limit)
    }

    // ── Jump back in: what you opened and the artists you're into lately ───

    suspend fun jumpBackIn(c: Context): List<HomeItem> {
        val opened = com.music.aalap.data.preferences.getRecentItems(c).mapNotNull { r ->
            when (r.type) {
                "album" -> HomeItem.Album(r.name, r.image, r.singer.ifBlank { "Album" }, r.singer)
                "artist" -> HomeItem.Artist(r.name, r.image, r.key.takeIf { it != r.name }.orEmpty())
                else -> null
            }
        }
        // Artists you played most in the last two weeks (Aalap + YouTube Music history).
        val recentArtists = recentTopArtists(c, 8)
        val photos = artistPhotos(c, recentArtists)
        val artists = recentArtists.map { n -> HomeItem.Artist(n, photos[key(n)]?.coverUri.orEmpty(), photos[key(n)]?.id.orEmpty()) }
        val out = ArrayList<HomeItem>()
        for (i in 0 until maxOf(opened.size, artists.size)) { opened.getOrNull(i)?.let(out::add); artists.getOrNull(i)?.let(out::add) }
        return out.distinctBy { it::class.simpleName + key(it.name) + it.name.lowercase() }.take(12)
    }

    private fun recentTopArtists(c: Context, n: Int): List<String> {
        val cut = System.currentTimeMillis() - 14 * 86_400_000L
        val counts = LinkedHashMap<String, Pair<String, Int>>()
        com.music.aalap.data.preferences.getListeningHistory(c).filter { it.ts >= cut }.forEach { h ->
            val name = h.singer.substringBefore(",").trim(); val k = key(name)
            if (k.isNotBlank()) counts[k] = (counts[k]?.first ?: name) to ((counts[k]?.second ?: 0) + 1)
        }
        YouTubeLibrary.historyArtistCounts(c).forEach { (name, plays) ->
            val k = key(name)
            if (k.isNotBlank()) counts[k] = (counts[k]?.first ?: name) to ((counts[k]?.second ?: 0) + plays)
        }
        return counts.values.sortedByDescending { it.second }.take(n).map { it.first }
    }

    /** Photo + id for each artist: from your follows first, else a lookup. */
    private suspend fun artistPhotos(c: Context, names: List<String>): Map<String, ArtistsModel> = coroutineScope {
        val known = YouTubeLibrary.allFollowed(c, null).filter { it.coverUri.isNotBlank() }.associateBy { key(it.name) }
        val api = Api(c)
        names.map { n ->
            async {
                val k = key(n)
                k to (known[k] ?: runCatching { api.searchArtists(n, 5).firstOrNull { key(it.name) == k } }.getOrNull())
            }
        }.awaitAll().mapNotNull { (k, a) -> a?.let { k to it } }.toMap()
    }

    // ── Your top artists (all services) ─────────────────────────────────────

    private fun topArtists(c: Context, n: Int): List<String> =
        runCatching { Personalizer.summary(c, topN = n).artists }.getOrDefault(emptyList())

    // ── Made for you: Daily Mixes, refreshed every day ──────────────────────

    /**
     * Four Daily Mixes, each around two of your top artists: songs of theirs you know
     * (played or liked anywhere) mixed with new ones the engine finds after them.
     */
    suspend fun dailyMixes(c: Context, force: Boolean = false): List<Mix> = coroutineScope {
        val stamp = today()
        if (!force) readMixes("daily", stamp)?.let { return@coroutineScope it }
        QueueEngine.ensureImported(c)
        val artists = topArtists(c, 8)
        if (artists.isEmpty()) return@coroutineScope emptyList()
        val known = knownSongs(c)
        val userId = RecoDb.userId(c)
        val db = RecoDb.get(c)
        val rnd = Random(stamp.hashCode().toLong())
        val mixes = artists.chunked(2).take(4).mapIndexed { i, group ->
            async {
                val keys = group.map(::key).toSet()
                val familiar = known.filter { key(it.singer) in keys }.shuffled(rnd).take(10)
                val seed = familiar.firstOrNull()
                    ?: runCatching { Api(c).artistTopCandidates(group.first()).firstOrNull() }.getOrNull()?.let { SongBridge.toSongsModel(db.resolve(it)) }
                val fresh = if (seed == null) emptyList() else runCatching {
                    QueueEngine.scoredAfter(c, userId, db.resolve(SongBridge.toCandidate(seed)), timeoutMs = 6_000)
                        .take(25).map { SongBridge.toSongsModel(it.song) }
                }.getOrDefault(emptyList())
                val songs = ArrayList<SongsModel>()
                for (j in 0 until maxOf(familiar.size, fresh.size)) { familiar.getOrNull(j)?.let(songs::add); fresh.getOrNull(j)?.let(songs::add) }
                val list = Diversifier.spreadArtists(songs.distinctBy { it.id }.take(30)) { it.singer }
                Mix(
                    title = "Daily Mix ${i + 1}",
                    subtitle = group.joinToString(", ") + " and more",
                    covers = list.map { it.coverUri }.filter { it.isNotBlank() }.distinct().take(4),
                    songs = list,
                )
            }
        }.awaitAll().filter { it.songs.size >= 5 }
        writeMixes("daily", stamp, mixes)
        mixes
    }

    /** Songs you know from every service: Aalap plays, liked songs (Spotify + YouTube), YouTube history. */
    private fun knownSongs(c: Context): List<SongsModel> {
        val history = com.music.aalap.data.preferences.getListeningHistory(c).filter { it.url.isNotBlank() }
            .map { SongsModel(it.songId, it.title, it.album, it.singer, it.image, it.url, it.spotifyTrackId, durationMs = it.durationMs) }
        return (history + YouTubeLibrary.unifiedLiked(c) + YouTubeLibrary.historySongs(c)).distinctBy { it.id }
    }

    private fun writeMixes(name: String, stamp: String, mixes: List<Mix>) {
        if (mixes.isEmpty()) return
        val a = JSONArray()
        mixes.forEach { m ->
            a.put(JSONObject().put("title", m.title).put("subtitle", m.subtitle)
                .put("covers", JSONArray(m.covers)).put("songs", songsJson(m.songs)))
        }
        prefs(appCtx).edit().putString(name, a.toString()).putString("$name.stamp", stamp).apply()
    }

    private fun readMixes(name: String, stamp: String): List<Mix>? = runCatching {
        if (prefs(appCtx).getString("$name.stamp", null) != stamp) return null
        val a = JSONArray(prefs(appCtx).getString(name, "[]"))
        (0 until a.length()).map { i ->
            a.getJSONObject(i).let { o ->
                val cv = o.getJSONArray("covers")
                Mix(o.getString("title"), o.optString("subtitle"), (0 until cv.length()).map { cv.getString(it) }, songsFrom(o.getJSONArray("songs")))
            }
        }.takeIf { it.isNotEmpty() }
    }.getOrNull()

    // ── Artist radios ───────────────────────────────────────────────────────

    /** Radio stations for your top artists across every service (photo + name). */
    suspend fun artistStations(c: Context): List<ArtistsModel> {
        val names = topArtists(c, 10)
        val photos = artistPhotos(c, names)
        return names.mapNotNull { n -> photos[key(n)] ?: ArtistsModel(n, "", "") }
    }

    /** An artist's radio: a few of their best songs, then the engine keeps it going. */
    suspend fun artistRadio(c: Context, artist: ArtistsModel): List<SongsModel> {
        val db = RecoDb.get(c)
        val top = runCatching { Api(c).artistTopCandidates(artist.name) }.getOrDefault(emptyList())
            .let { db.resolveAll(it) }.map(SongBridge::toSongsModel)
        val known = knownSongs(c).filter { key(it.singer) == key(artist.name) }
        return (top.shuffled().take(4) + known.shuffled().take(3)).distinctBy { it.id }
    }

    // ── All-time favourites (every service) ─────────────────────────────────

    /**
     * Your all-time favourites: songs you play most in Aalap, plus what you've liked on
     * Spotify and YouTube Music and play on YouTube Music, ranked together.
     */
    fun allTimeFavourites(c: Context, limit: Int = 20): List<SongsModel> {
        val score = HashMap<Int, Double>()
        val byId = HashMap<Int, SongsModel>()
        com.music.aalap.data.preferences.getListeningHistory(c).filter { it.url.isNotBlank() }.forEach { h ->
            score[h.songId] = (score[h.songId] ?: 0.0) + 1.0
            byId.putIfAbsent(h.songId, SongsModel(h.songId, h.title, h.album, h.singer, h.image, h.url, h.spotifyTrackId, durationMs = h.durationMs))
        }
        YouTubeLibrary.historySongs(c).forEach { s -> score[s.id] = (score[s.id] ?: 0.0) + 1.0; byId.putIfAbsent(s.id, s) }
        val liked = com.music.aalap.data.preferences.LikedLibrary.all(c)
        liked.forEachIndexed { i, s ->
            // Liked counts like a few plays, recent likes a bit more.
            score[s.id] = (score[s.id] ?: 0.0) + 2.0 + 1.0 * (1.0 - i.toDouble() / liked.size.coerceAtLeast(1))
            byId.putIfAbsent(s.id, s)
        }
        // Artists from an Apple Music import lift their songs.
        val apple = com.music.aalap.data.local.AppleMusicImport.artistCounts(c).keys.map(::key).toSet()
        byId.values.forEach { s -> if (key(s.singer) in apple) score[s.id] = (score[s.id] ?: 0.0) + 1.0 }
        return score.entries.sortedByDescending { it.value }.mapNotNull { byId[it.key] }
            .distinctBy { "${it.title.lowercase()}|${key(it.singer)}" }.take(limit)
    }

    // ── New releases from your artists ──────────────────────────────────────

    suspend fun newReleases(c: Context): List<LibraryEntry> = coroutineScope {
        val stamp = today() + "v3" // v3: compilations and label mixtapes left out, so today's list is rebuilt once
        val p = prefs(c)
        if (p.getString("releases.stamp", null) == stamp) runCatching {
            val a = JSONArray(p.getString("releases", "[]"))
            return@coroutineScope (0 until a.length()).map { i -> a.getJSONObject(i).let {
                LibraryEntry(it.getString("id"), it.optString("name"), it.optString("sub"), it.optString("cover"), false, it.optString("artists"))
            } }
        }
        val api = Api(c)
        val artists = (topArtists(c, 8).map { ArtistsModel(it, "", "") } + ArtistPicker.following(c).take(12)).distinctBy { key(it.name) }.take(16)
        val all = artists.map { a ->
            async {
                val id = a.id.ifBlank { runCatching { api.searchArtists(a.name, 5).firstOrNull { key(it.name) == key(a.name) }?.id }.getOrNull().orEmpty() }
                runCatching { api.artistReleases(id, newestFirst = true) }.getOrDefault(emptyList()).take(2)
            }
        }.awaitAll().flatten()
        // Labels also publish mixtapes under a star's name ("Happy Vibes", "The Winter
        // Playlist", "Bollywood New Love Hits"); those aren't new music from the artist.
        val mixtape = Regex("""(?i)\b(playlist|hits|vibes|collection|best of|jukebox|non ?stop|mashup|top \d+|love songs|songs of|essentials|greatest|bangers)\b""")
        val list = all.distinctBy { it.spotifyId }.filterNot { mixtape.containsMatchIn(it.name) }
            .sortedByDescending { it.subtitle.take(4) }.take(16)
        if (list.isNotEmpty()) {
            val a = JSONArray()
            list.forEach { e -> a.put(JSONObject().put("id", e.spotifyId).put("name", e.name).put("sub", e.subtitle).put("cover", e.coverUri).put("artists", e.artists)) }
            p.edit().putString("releases", a.toString()).putString("releases.stamp", stamp).apply()
        }
        list
    }
}

package com.music.spotui.data.recommendation

import android.content.Context
import com.music.spotui.data.api.Api
import com.music.spotui.data.api.ArtistPicker
import com.music.spotui.data.api.YouTubeLibrary
import com.music.spotui.data.entity.ArtistsModel
import com.music.spotui.data.entity.HomeItem
import com.music.spotui.data.entity.LibraryEntry
import com.music.spotui.data.entity.SongsModel
import com.music.spotui.data.recommendation.engine.Candidate
import com.music.spotui.data.recommendation.engine.Diversifier
import com.music.spotui.data.recommendation.engine.Personalizer
import com.music.spotui.data.recommendation.engine.QueueEngine
import com.music.spotui.data.recommendation.engine.RecoDb
import com.music.spotui.data.recommendation.engine.SongBridge
import com.music.spotui.data.recommendation.engine.SongMatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
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

    private val appCtx get() = com.music.spotui.MyApplication.instance

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

    suspend fun quickPicks(c: Context): List<SongsModel> {
        val stamp = window3h()
        readSongs("quick", stamp)?.let { return it }
        val db = RecoDb.get(c)
        val fromYouTube = if (com.music.spotui.data.preferences.isYoutubeLoggedIn(c))
            com.metrolist.innertube.YouTube.homeSongs().getOrNull().orEmpty()
                .map { Candidate(title = it.title, artists = it.artists, durationSec = it.durationSec, album = it.album, cover = it.thumbnail, ytId = it.videoId) }
                .let { db.resolveAll(it) }.map(SongBridge::toSongsModel)
        else emptyList()
        // Mixed with fresh engine picks (Spotify + YouTube + JioSaavn), then ordered for your taste.
        val engine = runCatching { QueueEngine.freshPicks(c, salt = stamp.hashCode().toLong(), limit = 15) }.getOrDefault(emptyList())
        val mixed = ArrayList<SongsModel>()
        for (i in 0 until maxOf(fromYouTube.size, engine.size)) { fromYouTube.getOrNull(i)?.let(mixed::add); engine.getOrNull(i)?.let(mixed::add) }
        val result = QueueEngine.rerank(c, mixed.distinctBy { it.id }, familiar = 0.5f).take(24)
        writeSongs("quick", stamp, result)
        return result
    }

    // ── Recently played: your plays in Aalap, then on YouTube Music ─────────

    fun recentlyPlayed(c: Context, limit: Int = 20): List<SongsModel> {
        val aalap = com.music.spotui.data.preferences.getListeningHistory(c)
            .filter { it.url.isNotBlank() }
            .distinctBy { "${it.title.lowercase()}|${key(it.singer)}" }
            .map { SongsModel(it.songId, it.title, it.album, it.singer, it.image, it.url, it.spotifyTrackId, durationMs = it.durationMs) }
        val seen = aalap.mapTo(HashSet()) { "${it.title.lowercase()}|${key(it.singer)}" }
        val yt = YouTubeLibrary.historySongs(c).filter { "${it.title.lowercase()}|${key(it.singer)}" !in seen }
        return (aalap + yt).take(limit)
    }

    // ── Jump back in: what you opened and the artists you're into lately ───

    suspend fun jumpBackIn(c: Context): List<HomeItem> {
        val opened = com.music.spotui.data.preferences.getRecentItems(c).mapNotNull { r ->
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
        com.music.spotui.data.preferences.getListeningHistory(c).filter { it.ts >= cut }.forEach { h ->
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
        val history = com.music.spotui.data.preferences.getListeningHistory(c).filter { it.url.isNotBlank() }
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
        com.music.spotui.data.preferences.getListeningHistory(c).filter { it.url.isNotBlank() }.forEach { h ->
            score[h.songId] = (score[h.songId] ?: 0.0) + 1.0
            byId.putIfAbsent(h.songId, SongsModel(h.songId, h.title, h.album, h.singer, h.image, h.url, h.spotifyTrackId, durationMs = h.durationMs))
        }
        YouTubeLibrary.historySongs(c).forEach { s -> score[s.id] = (score[s.id] ?: 0.0) + 1.0; byId.putIfAbsent(s.id, s) }
        val liked = YouTubeLibrary.unifiedLiked(c)
        liked.forEachIndexed { i, s ->
            // Liked counts like a few plays, recent likes a bit more.
            score[s.id] = (score[s.id] ?: 0.0) + 2.0 + 1.0 * (1.0 - i.toDouble() / liked.size.coerceAtLeast(1))
            byId.putIfAbsent(s.id, s)
        }
        // Artists from an Apple Music import lift their songs.
        val apple = com.music.spotui.data.local.AppleMusicImport.artistCounts(c).keys.map(::key).toSet()
        byId.values.forEach { s -> if (key(s.singer) in apple) score[s.id] = (score[s.id] ?: 0.0) + 1.0 }
        return score.entries.sortedByDescending { it.value }.mapNotNull { byId[it.key] }
            .distinctBy { "${it.title.lowercase()}|${key(it.singer)}" }.take(limit)
    }

    // ── New releases from your artists ──────────────────────────────────────

    suspend fun newReleases(c: Context): List<LibraryEntry> = coroutineScope {
        val stamp = today()
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
        val list = all.distinctBy { it.spotifyId }.sortedByDescending { it.subtitle.take(4) }.take(16)
        if (list.isNotEmpty()) {
            val a = JSONArray()
            list.forEach { e -> a.put(JSONObject().put("id", e.spotifyId).put("name", e.name).put("sub", e.subtitle).put("cover", e.coverUri).put("artists", e.artists)) }
            p.edit().putString("releases", a.toString()).putString("releases.stamp", stamp).apply()
        }
        list
    }
}

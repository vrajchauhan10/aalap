package com.music.aalap.data.local

import android.content.Context
import com.music.aalap.data.entity.PodcastModel
import com.music.aalap.data.entity.SongsModel
import kotlinx.coroutines.async
import org.json.JSONArray
import org.json.JSONObject

/**
 * Podcasts on the phone: today's popular shows (one search per language, kept for the day),
 * and where you stopped in each episode so it continues from there.
 */
object Podcasts {
    private fun prefs(c: Context) = c.getSharedPreferences("podcasts", Context.MODE_PRIVATE)

    // ── Popular today ───────────────────────────────────────────────────────

    /**
     * Popular podcast shows today: the top results for podcasts in your languages (Spotify ranks
     * the most-listened first), taken in turn from each language so each gets a place. Searched
     * once a day and kept, so Home costs nothing the rest of the day.
     */
    suspend fun popularToday(c: Context, limit: Int = 10): List<PodcastModel> {
        val today = java.time.LocalDate.now().toString()
        val p = prefs(c)
        if (p.getString("popular.day", null) == today) readShows(p.getString("popular", "[]")!!).takeIf { it.isNotEmpty() }?.let { return it.take(limit) }
        val langs = runCatching {
            com.music.aalap.data.api.Discover.radioLanguages(c, com.music.aalap.data.api.Mood.HAPPY).map { it.first }.filter { it.isNotBlank() }
        }.getOrDefault(emptyList()).ifEmpty { listOf("hindi") }.take(3)
        val api = com.music.aalap.data.api.Api(c)
        val lists = kotlinx.coroutines.coroutineScope {
            (langs.map { "$it podcast" } + "top podcasts india").map { q ->
                async { runCatching { api.searchShows(q, 10) }.getOrDefault(emptyList()) }
            }.map { it.await() }
        }
        val merged = interleave(lists).take(20)
        if (merged.isNotEmpty()) p.edit().putString("popular", writeShows(merged)).putString("popular.day", today).apply()
        return merged.take(limit)
    }

    /** Takes one from each list in turn, each show once. Pure, so it is unit-tested. */
    fun interleave(lists: List<List<PodcastModel>>): List<PodcastModel> {
        val out = LinkedHashMap<String, PodcastModel>()
        val max = lists.maxOfOrNull { it.size } ?: 0
        for (i in 0 until max) lists.forEach { l -> l.getOrNull(i)?.let { out.putIfAbsent(it.id, it) } }
        return out.values.toList()
    }

    /** Today's list if it was already fetched (no network). */
    fun popularCached(c: Context): List<PodcastModel> {
        val p = prefs(c)
        if (p.getString("popular.day", null) != java.time.LocalDate.now().toString()) return emptyList()
        return readShows(p.getString("popular", "[]")!!)
    }

    // ── Your podcasts (followed shows) ──────────────────────────────────────

    /** Shows you follow, most recently followed first. */
    fun followed(c: Context): List<PodcastModel> = readShows(prefs(c).getString("followed", "[]")!!)

    fun isFollowed(c: Context, id: String) = followed(c).any { it.id == id }

    fun setFollowed(c: Context, show: PodcastModel, follow: Boolean) {
        val rest = followed(c).filter { it.id != show.id }
        prefs(c).edit().putString("followed", writeShows(if (follow) listOf(show) + rest else rest)).apply()
    }

    /** The newest episode of each show you follow (one request per show, up to [limit] shows), newest first. */
    suspend fun newFromFollowed(c: Context, limit: Int = 10): List<SongsModel> = kotlinx.coroutines.coroutineScope {
        val api = com.music.aalap.data.api.Api(c)
        followed(c).take(limit).map { sh ->
            async {
                runCatching {
                    var first: SongsModel? = null
                    api.getShowEpisodes(sh.id).collect { r -> if (r is com.music.aalap.data.api.Response.Success) first = r.data.firstOrNull() }
                    first?.also { rememberShow(it.url, sh.id) }
                }.getOrNull()
            }
        }.mapNotNull { it.await() }
    }

    // ── What plays after an episode ─────────────────────────────────────────

    /**
     * After a podcast episode, more podcast (never songs: it's a different kind of listening):
     * the show's next episodes in order (older ones after newer, as you'd work through a show),
     * skipping ones you finished; when the show has no more, new episodes from shows you follow.
     */
    suspend fun nextAfter(c: Context, ep: SongsModel, exclude: Set<Int>, n: Int = 3): List<SongsModel> {
        val api = com.music.aalap.data.api.Api(c)
        val show = ep.singer.ifBlank { ep.album }
        val showId = showIds[ep.url]
            ?: all(c).firstOrNull { it.episode.url == ep.url }?.showId?.takeIf { it.isNotBlank() }
            ?: runCatching { api.searchShows(show, 5).firstOrNull { it.name.equals(show, true) }?.id }.getOrNull()
        val list = if (showId.isNullOrBlank()) emptyList() else runCatching {
            var got: List<SongsModel> = emptyList()
            api.getShowEpisodes(showId, show).collect { r -> if (r is com.music.aalap.data.api.Response.Success) got = r.data }
            got
        }.getOrDefault(emptyList())
        val picks = pickNext(list, ep.url, exclude, finishedUrls(c), n).ifEmpty {
            runCatching { newFromFollowed(c) }.getOrDefault(emptyList()).filter { it.id !in exclude && it.url != ep.url }.take(n)
        }
        if (!showId.isNullOrBlank()) picks.forEach { rememberShow(it.url, showId) }
        return picks
    }

    private fun finishedUrls(c: Context) = all(c).filter { it.finished }.mapTo(HashSet()) { it.episode.url }

    /** The episodes after [currentUrl] in [list] (newest first), then the newest ones. Pure, unit-tested. */
    fun pickNext(list: List<SongsModel>, currentUrl: String, exclude: Set<Int>, finished: Set<String>, n: Int): List<SongsModel> {
        val at = list.indexOfFirst { it.url == currentUrl }
        val order = if (at >= 0) list.drop(at + 1) + list.take(at) else list
        return order.filter { it.url != currentUrl && it.id !in exclude && it.url !in finished }.take(n)
    }

    // ── Where you stopped ───────────────────────────────────────────────────

    /** An episode you started: how far in, how long, when, and its show. */
    data class Progress(val episode: SongsModel, val positionMs: Long, val durationMs: Long, val at: Long, val showId: String = "") {
        val finished: Boolean get() = durationMs > 0 && positionMs >= durationMs - 60_000L
        val fraction: Float get() = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    }

    private const val KEEP = 100
    private const val KEEP_DAYS = 30L

    @Volatile private var showIds = HashMap<String, String>()

    /** The show an episode belongs to, when it was started from the show's page. */
    fun rememberShow(episodeUrl: String, showId: String) { if (showId.isNotBlank()) showIds[episodeUrl] = showId }

    fun all(c: Context): List<Progress> = runCatching {
        val a = JSONArray(prefs(c).getString("progress", "[]"))
        val cut = System.currentTimeMillis() - KEEP_DAYS * 86_400_000L
        (0 until a.length()).map { a.getJSONObject(it) }.mapNotNull { o ->
            val e = o.getJSONObject("e")
            Progress(
                SongsModel(e.getInt("id"), e.optString("title"), e.optString("album"), e.optString("singer"), e.optString("cover"), e.optString("url"), durationMs = e.optInt("dur")),
                o.optLong("pos"), o.optLong("len"), o.optLong("at"), o.optString("show"),
            ).takeIf { it.at >= cut }
        }.sortedByDescending { it.at }
    }.getOrDefault(emptyList())

    /** Saves where [episode] is (called when it pauses, changes, or every few seconds). */
    fun save(c: Context, episode: SongsModel, positionMs: Long, durationMs: Long) {
        if (!episode.url.startsWith("episode:") || positionMs <= 0) return
        val now = System.currentTimeMillis()
        val old = all(c)
        val show = showIds[episode.url] ?: old.firstOrNull { it.episode.url == episode.url }?.showId.orEmpty()
        val list = (listOf(Progress(episode, positionMs, durationMs, now, show)) + old.filter { it.episode.url != episode.url }).take(KEEP)
        val a = JSONArray()
        list.forEach { pr ->
            val e = pr.episode
            a.put(JSONObject().put("pos", pr.positionMs).put("len", pr.durationMs).put("at", pr.at).put("show", pr.showId)
                .put("e", JSONObject().put("id", e.id).put("title", e.title).put("album", e.album).put("singer", e.singer)
                    .put("cover", e.coverUri).put("url", e.url).put("dur", e.durationMs)))
        }
        prefs(c).edit().putString("progress", a.toString()).apply()
    }

    /** Where to continue [episodeUrl] from: null when never started, barely started or finished. */
    fun resumeAt(c: Context, episodeUrl: String): Long? =
        all(c).firstOrNull { it.episode.url == episodeUrl }?.takeIf { !it.finished && it.positionMs > 30_000L }?.positionMs

    /** Episodes started but not finished, most recent first. */
    fun continueListening(c: Context, limit: Int = 10): List<Progress> =
        all(c).filter { !it.finished && it.positionMs > 30_000L }.take(limit)

    /** Shows you listen to most this month: (show name, cover, episodes, show id), most listened first. */
    data class TopShow(val name: String, val cover: String, val episodes: Int, val minutes: Long, val showId: String, val latest: Progress)

    fun topShows(c: Context, limit: Int = 5): List<TopShow> = topShows(all(c), limit)

    fun topShows(list: List<Progress>, limit: Int): List<TopShow> =
        list.groupBy { it.episode.singer.ifBlank { it.episode.album } }
            .filterKeys { it.isNotBlank() }
            .map { (name, eps) ->
                TopShow(name, eps.first().episode.coverUri, eps.size, eps.sumOf { it.positionMs } / 60_000,
                    eps.firstOrNull { it.showId.isNotBlank() }?.showId.orEmpty(), eps.maxBy { it.at })
            }
            .sortedWith(compareByDescending<TopShow> { it.minutes }.thenByDescending { it.episodes })
            .take(limit)

    private fun writeShows(l: List<PodcastModel>) = JSONArray().also { a ->
        l.forEach { a.put(JSONObject().put("id", it.id).put("name", it.name).put("pub", it.publisher).put("cover", it.coverUri)) }
    }.toString()

    private fun readShows(s: String): List<PodcastModel> = runCatching {
        val a = JSONArray(s)
        (0 until a.length()).map { a.getJSONObject(it).let { o -> PodcastModel(o.getString("id"), o.optString("name"), o.optString("pub"), o.optString("cover")) } }
    }.getOrDefault(emptyList())
}

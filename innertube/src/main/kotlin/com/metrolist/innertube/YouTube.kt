package com.metrolist.innertube

import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonArray
import com.metrolist.innertube.models.YouTubeClient
import com.metrolist.innertube.models.YouTubeClient.Companion.WEB_REMIX
import com.metrolist.innertube.models.YouTubeLocale
import com.metrolist.innertube.models.getContinuation
import com.metrolist.innertube.models.getItems
import com.metrolist.innertube.models.response.PlayerResponse
import com.metrolist.innertube.models.response.SearchResponse
import com.metrolist.innertube.pages.SearchPage
import com.metrolist.innertube.pages.SearchResult
import io.ktor.client.call.body
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import java.net.Proxy

/**
 * Parse useful data with [InnerTube] sending requests.
 * Modified from [ViMusic](https://github.com/vfsfitvnm/ViMusic).
 *
 * Trimmed for Aalap: only the pieces the app uses survive — track search
 * (to match a Spotify track to a YouTube video), the player endpoint (to
 * resolve the audio stream) and the NewPipe fallback deobfuscation.
 */
object YouTube {
    private val innerTube = InnerTube()

    var locale: YouTubeLocale
        get() = innerTube.locale
        set(value) {
            innerTube.locale = value
        }
    var visitorData: String?
        get() = innerTube.visitorData
        set(value) {
            innerTube.visitorData = value
        }
    var dataSyncId: String?
        get() = innerTube.dataSyncId
        set(value) {
            innerTube.dataSyncId = value
        }
    var cookie: String?
        get() = innerTube.cookie
        set(value) {
            innerTube.cookie = value
        }
    var proxy: Proxy?
        get() = innerTube.proxy
        set(value) {
            innerTube.proxy = value
        }
    var proxyAuth: String?
        get() = innerTube.proxyAuth
        set(value) {
            innerTube.proxyAuth = value
        }
    var useLoginForBrowse: Boolean
        get() = innerTube.useLoginForBrowse
        set(value) {
            innerTube.useLoginForBrowse = value
        }

    suspend fun search(query: String, filter: SearchFilter): Result<SearchResult> = runCatching {
        val response = innerTube.search(WEB_REMIX, query, filter.value).body<SearchResponse>()
        val shelves = response.contents?.tabbedSearchResultsRenderer?.tabs?.firstOrNull()
            ?.tabRenderer?.content?.sectionListRenderer?.contents
            ?.mapNotNull { it.musicShelfRenderer }
            .orEmpty()
        SearchResult(
            items = shelves.flatMap { shelf ->
                shelf.contents?.getItems()?.mapNotNull { SearchPage.toYTItem(it) } ?: emptyList()
            }.distinctBy { it.id },
            continuation = shelves.firstOrNull { it.continuations != null }
                ?.continuations?.getContinuation()
        )
    }

    suspend fun searchContinuation(continuation: String): Result<SearchResult> = runCatching {
        val response = innerTube.search(WEB_REMIX, continuation = continuation).body<SearchResponse>()
        val items = response.continuationContents?.musicShelfContinuation?.contents
            ?.mapNotNull {
                SearchPage.toYTItem(it.musicResponsiveListItemRenderer)
            } ?: emptyList()
        SearchResult(
            items = items,
            continuation = if (items.isEmpty()) null else response.continuationContents?.musicShelfContinuation?.continuations?.getContinuation()
        )
    }

    suspend fun player(videoId: String, playlistId: String? = null, client: YouTubeClient, signatureTimestamp: Int? = null, poToken: String? = null, authenticated: Boolean = false): Result<PlayerResponse> = runCatching {
        innerTube.player(client, videoId, playlistId, signatureTimestamp, poToken, authenticated).body<PlayerResponse>()
    }

    /** One song from YouTube Music's "Up next" radio for a video. */
    data class UpNextSong(
        val videoId: String,
        val title: String,
        val artists: String,
        val durationSec: Int,
        val thumbnail: String,
    )

    /**
     * YouTube Music's automatic "Up next" queue after [videoId] (its RDAMVM radio):
     * what YouTube Music listeners play after this song, in YouTube's order.
     */
    suspend fun upNext(videoId: String): Result<List<UpNextSong>> = runCatching {
        val json = Json.parseToJsonElement(
            innerTube.next(WEB_REMIX, videoId, "RDAMVM$videoId").bodyAsText()
        ).jsonObject
        fun JsonElement?.obj() = (this as? JsonObject)
        fun JsonElement?.arr() = (this as? JsonArray)
        fun JsonElement?.str() = (this as? JsonPrimitive)?.contentOrNull
        fun runsText(e: JsonElement?) = e.obj()?.get("runs").arr()?.joinToString("") { it.obj()?.get("text").str().orEmpty() }.orEmpty()
        val panel = json["contents"].obj()
            ?.get("singleColumnMusicWatchNextResultsRenderer").obj()
            ?.get("tabbedRenderer").obj()
            ?.get("watchNextTabbedResultsRenderer").obj()
            ?.get("tabs").arr()?.firstOrNull().obj()
            ?.get("tabRenderer").obj()?.get("content").obj()
            ?.get("musicQueueRenderer").obj()?.get("content").obj()
            ?.get("playlistPanelRenderer").obj()
            ?.get("contents").arr().orEmpty()
        panel.mapNotNull { item ->
            val r = item.obj()?.get("playlistPanelVideoRenderer").obj() ?: return@mapNotNull null
            val id = r["videoId"].str() ?: return@mapNotNull null
            // Byline is "Artist • Album • Year" (or "Artist & Artist • …"): keep the artists.
            val byline = r["longBylineText"].obj()?.get("runs").arr().orEmpty()
                .mapNotNull { it.obj()?.get("text").str() }
            val artists = byline.joinToString("").substringBefore(" • ").replace(" & ", ", ")
            val length = r["lengthText"].let(::runsText).split(":").mapNotNull { it.trim().toIntOrNull() }
                .fold(0) { acc, n -> acc * 60 + n }
            val thumb = r["thumbnail"].obj()?.get("thumbnails").arr()?.lastOrNull().obj()?.get("url").str().orEmpty()
            UpNextSong(id, runsText(r["title"]), artists, length, thumb)
        }.filter { it.videoId != videoId }
    }

    /** A song in the signed-in user's YouTube Music library. */
    data class LibrarySong(
        val videoId: String,
        val title: String,
        val artists: String,
        val album: String,
        val durationSec: Int,
        val thumbnail: String,
    )

    /** An artist the signed-in user subscribes to on YouTube Music. */
    data class LibraryArtist(val browseId: String, val name: String, val thumbnail: String)

    private fun JsonElement?.obj() = this as? JsonObject
    private fun JsonElement?.arr() = this as? JsonArray
    private fun JsonElement?.str() = (this as? JsonPrimitive)?.contentOrNull
    private fun runsText(e: JsonElement?) =
        e.obj()?.get("runs").arr()?.joinToString("") { it.obj()?.get("text").str().orEmpty() }.orEmpty()

    /** Every object stored under [key] anywhere in [root] (YouTube nests pages differently by layout). */
    private fun findAll(root: JsonElement?, key: String, out: MutableList<JsonObject> = mutableListOf()): MutableList<JsonObject> {
        when (root) {
            is JsonObject -> root.forEach { (k, v) -> if (k == key) v.obj()?.let(out::add) else findAll(v, key, out) }
            is JsonArray -> root.forEach { findAll(it, key, out) }
            else -> {}
        }
        return out
    }

    private fun continuationToken(root: JsonElement?): String? {
        findAll(root, "nextContinuationData").firstOrNull()?.get("continuation").str()?.let { return it }
        return findAll(root, "continuationCommand").firstOrNull()?.get("token").str()
    }

    /** The largest thumbnail anywhere in [r] (the last entry of its first thumbnails list). */
    private fun bestThumb(r: JsonObject): String {
        fun first(e: JsonElement?): JsonArray? = when (e) {
            is JsonObject -> e["thumbnails"].arr() ?: e.values.firstNotNullOfOrNull { first(it) }
            is JsonArray -> e.firstNotNullOfOrNull { first(it) }
            else -> null
        }
        return first(r)?.lastOrNull().obj()?.get("url").str().orEmpty()
    }

    /** Pages through a library list: [first] browse id, then continuations, at most [maxPages]. */
    private suspend fun <T> pages(browseId: String, maxPages: Int, parse: (JsonElement) -> List<T>): List<T> {
        val out = ArrayList<T>()
        var json: JsonElement = Json.parseToJsonElement(innerTube.browse(WEB_REMIX, browseId).bodyAsText())
        var page = 0
        while (true) {
            out += parse(json)
            val token = continuationToken(json) ?: break
            if (++page >= maxPages) break
            json = Json.parseToJsonElement(innerTube.browse(WEB_REMIX, null, token).bodyAsText())
        }
        return out
    }

    /** The signed-in user's liked songs (the "Liked music" playlist), newest first. */
    suspend fun likedSongs(maxPages: Int = 40): Result<List<LibrarySong>> = runCatching {
        check(cookie != null) { "not signed in" }
        pages("VLLM", maxPages) { json -> parseSongs(json) }.distinctBy { it.videoId }
    }

    private fun parseSongs(json: JsonElement): List<LibrarySong> =
            findAll(json, "musicResponsiveListItemRenderer").mapNotNull { r ->
                val videoId = r["playlistItemData"].obj()?.get("videoId").str()
                    ?: findAll(r, "watchEndpoint").firstOrNull()?.get("videoId").str()
                    ?: return@mapNotNull null
                val cols = r["flexColumns"].arr().orEmpty().map {
                    it.obj()?.get("musicResponsiveListItemFlexColumnRenderer").obj()?.get("text")
                }
                val title = runsText(cols.getOrNull(0)).ifBlank { return@mapNotNull null }
                // Column 2 is "Artist & Artist" (sometimes "Artist • Album"); column 3 the album.
                val artists = runsText(cols.getOrNull(1)).substringBefore(" • ").replace(" & ", ", ")
                val album = runsText(cols.getOrNull(2))
                val length = r["fixedColumns"].arr()?.firstOrNull().obj()
                    ?.get("musicResponsiveListItemFixedColumnRenderer").obj()?.get("text").let(::runsText)
                    .split(":").mapNotNull { it.trim().toIntOrNull() }.fold(0) { acc, n -> acc * 60 + n }
                LibrarySong(videoId, title, artists, album, length, bestThumb(r))
            }

    /** The signed-in user's recently played songs on YouTube Music (newest first). */
    suspend fun history(maxPages: Int = 3): Result<List<LibrarySong>> = runCatching {
        check(cookie != null) { "not signed in" }
        pages("FEmusic_history", maxPages) { json -> parseSongs(json) }
    }

    /**
     * Songs YouTube Music suggests on its home page for this user ("Quick picks",
     * "Listen again", "Mixed for you" shelves), in page order.
     */
    suspend fun homeSongs(maxPages: Int = 2): Result<List<LibrarySong>> = runCatching {
        pages("FEmusic_home", maxPages) { json ->
            val list = parseSongs(json).toMutableList()
            // Card shelves (musicTwoRowItemRenderer) hold songs too when they open a watch page.
            findAll(json, "musicTwoRowItemRenderer").forEach { r ->
                val vid = r["navigationEndpoint"].obj()?.get("watchEndpoint").obj()?.get("videoId").str() ?: return@forEach
                val title = runsText(r["title"]).ifBlank { return@forEach }
                val sub = runsText(r["subtitle"]).split(" • ").map { it.trim() }
                val artists = sub.firstOrNull { it.isNotBlank() && it != "Song" && it != "Video" && !it.contains("views") }.orEmpty()
                list += LibrarySong(vid, title, artists.replace(" & ", ", "), "", 0, bestThumb(r))
            }
            list
        }.distinctBy { it.videoId }
    }

    /**
     * What's trending on YouTube Music right now (Explore → Trending, falling back to
     * the Charts page), songs only, in page order.
     */
    suspend fun trendingSongs(): Result<List<LibrarySong>> = runCatching {
        val explore = runCatching { pages("FEmusic_explore", 1) { json -> parseSongs(json) } }.getOrDefault(emptyList())
        explore.ifEmpty { pages("FEmusic_charts", 1) { json -> parseSongs(json) } }.distinctBy { it.videoId }
    }

    /** Artists the signed-in user subscribes to on YouTube Music. */
    suspend fun libraryArtists(maxPages: Int = 10): Result<List<LibraryArtist>> = runCatching {
        check(cookie != null) { "not signed in" }
        pages("FEmusic_library_corpus_artists", maxPages) { json ->
            findAll(json, "musicResponsiveListItemRenderer").mapNotNull { r ->
                val id = r["navigationEndpoint"].obj()?.get("browseEndpoint").obj()?.get("browseId").str()
                    ?: return@mapNotNull null
                val name = r["flexColumns"].arr()?.firstOrNull().obj()
                    ?.get("musicResponsiveListItemFlexColumnRenderer").obj()?.get("text").let(::runsText)
                if (name.isBlank()) null else LibraryArtist(id, name, bestThumb(r))
            }
        }.distinctBy { it.browseId }
    }

    suspend fun visitorData(): Result<String> = runCatching {
        Json.parseToJsonElement(innerTube.getSwJsData().bodyAsText().substring(5))
            .jsonArray[0]
            .jsonArray[2]
            .jsonArray.first {
                (it as? JsonPrimitive)?.contentOrNull?.let { candidate ->
                    VISITOR_DATA_REGEX.containsMatchIn(candidate)
                } ?: false
            }
            .jsonPrimitive.content
    }

    /**
     * Validates that the current cookie/session tuple resolves an active YouTube account.
     * A 200 response alone is not enough: the endpoint can return a signed-out menu.
     */
    suspend fun validateLogin(): Result<Boolean> = runCatching {
        val body = innerTube.accountMenu(WEB_REMIX).bodyAsText()
        body.contains("activeAccountHeaderRenderer") ||
            (body.contains("accountName") && !body.contains("Sign in"))
    }

    @JvmInline
    value class SearchFilter(val value: String) {
        companion object {
            val FILTER_SONG = SearchFilter("EgWKAQIIAWoKEAkQBRAKEAMQBA%3D%3D")
            val FILTER_VIDEO = SearchFilter("EgWKAQIQAWoKEAkQChAFEAMQBA%3D%3D")
        }
    }

    private val VISITOR_DATA_REGEX = Regex("^Cg[t|s]")

    fun getNewPipeStreamUrls(videoId: String): List<Pair<Int, String>> {
        return NewPipeExtractor.newPipePlayer(videoId)
    }

    suspend fun newPipePlayer(
        videoId: String,
        tempRes: PlayerResponse,
    ): PlayerResponse? {
        if (tempRes.playabilityStatus.status != "OK") {
            return null
        }

        val streamsList = getNewPipeStreamUrls(videoId)
        if (streamsList.isEmpty()) return null

        val decodedSigResponse = tempRes.copy(
            streamingData = tempRes.streamingData?.copy(
                formats = tempRes.streamingData.formats?.map { format ->
                    format.copy(
                        url = streamsList.find { it.first == format.itag }?.second ?: format.url,
                    )
                },
                adaptiveFormats = tempRes.streamingData.adaptiveFormats.map { adaptiveFormat ->
                    adaptiveFormat.copy(
                        url = streamsList.find { it.first == adaptiveFormat.itag }?.second ?: adaptiveFormat.url,
                    )
                },
            ),
        )

        val urlList = (
            decodedSigResponse.streamingData?.adaptiveFormats?.mapNotNull { it.url }?.toMutableList() ?: mutableListOf()
        ).apply {
            decodedSigResponse.streamingData?.formats?.mapNotNull { it.url }?.let { addAll(it) }
        }

        return if (urlList.isNotEmpty()) {
            decodedSigResponse
        } else {
            null
        }
    }
}

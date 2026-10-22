package com.music.spotui.data.api

import android.content.Context
import com.music.spotui.data.entity.ArtistsModel
import com.music.spotui.data.preferences.addFollowedArtist
import com.music.spotui.data.preferences.cacheFollowedArtists
import com.music.spotui.data.preferences.getCachedFollowedArtists
import com.music.spotui.data.preferences.removeFollowedArtist
import com.music.spotui.data.recommendation.engine.Personalizer
import com.music.spotui.data.recommendation.engine.SongMatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * The artist picker: who you follow (Spotify + YouTube Music + Aalap), artists you
 * play a lot but don't follow yet (with your play count), and artists similar to your
 * favourites. Following here follows on Spotify too and shapes recommendations.
 */
object ArtistPicker {

    /** An artist on the picker with why it's suggested ("42 plays", "Like Arijit Singh"). */
    data class Pick(val artist: ArtistsModel, val reason: String)

    private fun key(name: String) = SongMatcher.artistKeys(name).firstOrNull().orEmpty()

    /** Everyone you follow right now, each once. */
    fun following(c: Context): List<ArtistsModel> = YouTubeLibrary.allFollowed(c, null)

    /** Spotify's listing (id + photo) for an artist known only by name. */
    private suspend fun resolve(api: Api, name: String): ArtistsModel? =
        api.searchArtists(name, limit = 5).firstOrNull { key(it.name) == key(name) }

    /**
     * Your most-played artists with your finished plays ("42 plays"), followed or not;
     * the ones you don't follow yet come first.
     */
    suspend fun fromYourListening(c: Context, limit: Int = 15): List<Pick> = coroutineScope {
        val followed = following(c).mapTo(HashSet()) { key(it.name) }
        val api = Api(c)
        Personalizer.artistPlayCounts(c)
            .filter { (_, n) -> n >= 2 }
            .take(limit)
            .sortedBy { (name, _) -> key(name) in followed }
            .map { (name, n) -> async { resolve(api, name)?.let { Pick(it, "$n plays") } } }
            .awaitAll().filterNotNull().distinctBy { key(it.artist.name) }
    }

    /** "Fans also like" for your top artists, minus anyone you already follow or were shown. */
    suspend fun similar(c: Context, exclude: Set<String>, limit: Int = 18): List<Pick> = coroutineScope {
        val api = Api(c)
        val skip = following(c).mapTo(HashSet()) { key(it.name) } + exclude
        val seeds = Personalizer.summary(c).artists.take(4)
        val lists = seeds.map { seed ->
            async {
                val found = resolve(api, seed) ?: return@async emptyList<Pick>()
                api.relatedArtists(found.id).take(8).map { Pick(it, "Like ${found.name}") }
            }
        }.awaitAll()
        // Round-robin so one favourite doesn't fill the whole row.
        val out = ArrayList<Pick>()
        var i = 0
        while (out.size < limit && lists.any { i < it.size }) {
            lists.forEach { l -> l.getOrNull(i)?.takeIf { key(it.artist.name) !in skip && out.none { o -> key(o.artist.name) == key(it.artist.name) } }?.let { out += it } }
            i++
        }
        out.take(limit)
    }

    /** A few artists like [artist] that aren't in [exclude] (keys): shown right after you follow it. */
    suspend fun relatedTo(c: Context, artist: ArtistsModel, exclude: Set<String>, n: Int = 3): List<Pick> {
        val api = Api(c)
        val id = artist.id.ifBlank { resolve(api, artist.name)?.id.orEmpty() }
        return api.relatedArtists(id).filter { key(it.name) !in exclude }.take(n).map { Pick(it, "Like ${artist.name}") }
    }

    private const val ALBUMS_PREF = "favourite_albums"

    /**
     * Popular albums by the artists you follow and play most, two each, mixed. Cached
     * for the day; [force] rebuilds.
     */
    suspend fun albumsFromFavourites(c: Context, force: Boolean = false): List<com.music.spotui.data.entity.LibraryEntry> = coroutineScope {
        val prefs = c.getSharedPreferences(ALBUMS_PREF, Context.MODE_PRIVATE)
        val today = java.time.LocalDate.now().toString()
        if (!force && prefs.getString("day", null) == today) readAlbums(prefs.getString("list", "[]"))?.let { return@coroutineScope it }
        val api = Api(c)
        // A new mix every day: your 4 most-played artists always, plus 8 picked from the
        // rest of your top artists and follows; for each, 2 of their top 6 releases,
        // preferring albums that weren't shown yesterday.
        val rnd = java.util.Random(today.hashCode().toLong())
        val played = Personalizer.artistPlayCounts(c).take(20).map { ArtistsModel(it.first, "", "") }
        val pool = (played.drop(4) + following(c)).distinctBy { key(it.name) }.shuffled(rnd)
        val artists = (played.take(4) + pool).distinctBy { key(it.name) }.take(12)
        val yesterday = prefs.getStringSet("shown", emptySet()).orEmpty()
        val lists = artists.map { a ->
            async {
                val id = a.id.ifBlank { resolve(api, a.name)?.id.orEmpty() }
                // Skip dialogue albums and dubbed versions ("… (Telugu)"); keep real music releases.
                val notMusic = Regex("""dialogue|\b(telugu|tamil|malayalam|kannada)\b.*(version|\]|\))|\((telugu|tamil|malayalam|kannada)\)|\[(telugu|tamil|malayalam|kannada)\]""", RegexOption.IGNORE_CASE)
                val releases = runCatching { api.artistReleases(id) }.getOrDefault(emptyList()).filterNot { notMusic.containsMatchIn(it.name) }.take(6)
                (releases.filter { it.spotifyId !in yesterday }.shuffled(rnd) + releases.filter { it.spotifyId in yesterday }).take(2)
            }
        }.awaitAll()
        val mixed = ArrayList<com.music.spotui.data.entity.LibraryEntry>()
        for (i in 0 until 2) lists.forEach { l -> l.getOrNull(i)?.let { if (mixed.none { m -> m.spotifyId == it.spotifyId }) mixed += it } }
        if (mixed.isNotEmpty()) prefs.edit().putString("day", today).putString("list", writeAlbums(mixed))
            .putStringSet("shown", mixed.mapTo(HashSet()) { it.spotifyId }).apply()
        mixed
    }

    private fun writeAlbums(list: List<com.music.spotui.data.entity.LibraryEntry>) = org.json.JSONArray().also { a ->
        list.forEach { e ->
            a.put(org.json.JSONObject().put("id", e.spotifyId).put("name", e.name).put("sub", e.subtitle)
                .put("cover", e.coverUri).put("artists", e.artists))
        }
    }.toString()

    private fun readAlbums(raw: String?): List<com.music.spotui.data.entity.LibraryEntry>? = runCatching {
        val a = org.json.JSONArray(raw ?: "[]")
        (0 until a.length()).map { i -> a.getJSONObject(i).let {
            com.music.spotui.data.entity.LibraryEntry(it.getString("id"), it.optString("name"), it.optString("sub"), it.optString("cover"), false, it.optString("artists"))
        } }
    }.getOrNull()?.takeIf { it.isNotEmpty() }

    suspend fun search(c: Context, query: String): List<ArtistsModel> = Api(c).searchArtists(query, limit = 18)

    /** Follows or unfollows [artist] in Aalap and on Spotify; returns the artist with its id. */
    suspend fun setFollowed(c: Context, artist: ArtistsModel, follow: Boolean): ArtistsModel {
        val a = if (artist.id.isNotBlank()) artist else resolve(Api(c), artist.name) ?: artist
        if (follow) {
            YouTubeLibrary.setArtistHidden(c, a.name, false)
            if (a.id.isNotBlank()) {
                addFollowedArtist(c, a.id, a.name)
                SpotifySync.setArtistFollowed(c, a.id, true)
            }
            val cached = getCachedFollowedArtists(c)
            if (cached.none { key(it.name) == key(a.name) }) cacheFollowedArtists(c, listOf(a) + cached)
            val sp = YouTubeLibrary.spotifyArtists(c)
            if (a.id.isNotBlank() && sp.none { key(it.name) == key(a.name) }) YouTubeLibrary.saveSpotifyArtists(c, listOf(a) + sp)
        } else {
            if (a.id.isNotBlank()) {
                removeFollowedArtist(c, a.id)
                SpotifySync.setArtistFollowed(c, a.id, false)
            }
            cacheFollowedArtists(c, getCachedFollowedArtists(c).filter { key(it.name) != key(a.name) })
            YouTubeLibrary.saveSpotifyArtists(c, YouTubeLibrary.spotifyArtists(c).filter { key(it.name) != key(a.name) })
            // Keeps a YouTube Music subscription out of Aalap too (unsubscribing there is up to you).
            YouTubeLibrary.setArtistHidden(c, a.name, true)
        }
        Personalizer.invalidate()
        return a
    }
}

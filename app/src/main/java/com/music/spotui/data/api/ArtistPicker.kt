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
    fun following(c: Context): List<ArtistsModel> {
        val local = c.getSharedPreferences("FollowedArtists", Context.MODE_PRIVATE).all
            .mapNotNull { (id, name) -> (name as? String)?.let { ArtistsModel(it, "", id) } }
        val merged = YouTubeLibrary.mergeArtists(getCachedFollowedArtists(c), YouTubeLibrary.artists(c))
        val keys = merged.mapTo(HashSet()) { key(it.name) }
        val hidden = YouTubeLibrary.hiddenArtists(c)
        return merged + local.filter { key(it.name) !in keys && key(it.name) !in hidden }
    }

    /** Spotify's listing (id + photo) for an artist known only by name. */
    private suspend fun resolve(api: Api, name: String): ArtistsModel? =
        api.searchArtists(name, limit = 5).firstOrNull { key(it.name) == key(name) }

    /** Artists you play most that you don't follow yet, with your finished plays. */
    suspend fun fromYourListening(c: Context, limit: Int = 15): List<Pick> = coroutineScope {
        val followed = following(c).mapTo(HashSet()) { key(it.name) }
        val api = Api(c)
        Personalizer.artistPlayCounts(c)
            .filter { (name, n) -> n >= 2 && key(name) !in followed }
            .take(limit)
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
        } else {
            if (a.id.isNotBlank()) {
                removeFollowedArtist(c, a.id)
                SpotifySync.setArtistFollowed(c, a.id, false)
            }
            cacheFollowedArtists(c, getCachedFollowedArtists(c).filter { key(it.name) != key(a.name) })
            // Keeps a YouTube Music subscription out of Aalap too (unsubscribing there is up to you).
            YouTubeLibrary.setArtistHidden(c, a.name, true)
        }
        Personalizer.invalidate()
        return a
    }
}

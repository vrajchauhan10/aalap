package com.music.aalap.data.recommendation.engine.sources

import android.content.Context
import com.music.aalap.data.api.Api
import com.music.aalap.data.recommendation.engine.Song
import com.music.aalap.data.recommendation.engine.Source
import com.music.aalap.data.recommendation.engine.SourceResult

/**
 * Spotify's song radio. Spotify closed its public Recommendations API to new apps
 * (Nov 2024), so this uses what the Spotify web player itself queues after a song:
 * the track-radio station, then Spotify's "recommended tracks" list, then an artist
 * radio (artist + related artists' top tracks) when neither answers. See [Api.radioCandidates].
 */
class SpotifyRadioSource(private val context: Context) : NextSongSource {
    override val source = Source.SPOTIFY

    override suspend fun getNextSongs(seed: Song): SourceResult {
        val api = Api(context)
        val found = if (seed.info.spotifyId.isBlank()) api.findTrack(seed.info) else null
        val spotifyId = seed.info.spotifyId.ifBlank { found?.spotifyId.orEmpty() }
        if (spotifyId.isBlank()) return SourceResult(emptyList())
        com.music.aalap.data.recommendation.engine.RecoTrace.log("    Spotify: track $spotifyId" + (if (found != null) " (found by searching '${seed.title} ${seed.artists.substringBefore(",")}')" else "") + " → its song radio")
        val ranked = api.radioCandidates(spotifyId).filter { it.spotifyId != spotifyId }
        return SourceResult(ranked, seedUpdate = found?.let { seed.info.copy(spotifyId = it.spotifyId, isrc = seed.info.isrc.ifBlank { it.isrc }) })
    }
}

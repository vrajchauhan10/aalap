package com.music.spotui.data.recommendation.engine.sources

import com.metrolist.innertube.YouTube
import com.music.spotui.data.recommendation.engine.Candidate
import com.music.spotui.data.recommendation.engine.Song
import com.music.spotui.data.recommendation.engine.SongBridge
import com.music.spotui.data.recommendation.engine.Source
import com.music.spotui.data.recommendation.engine.SourceResult
import com.music.spotui.di.SongPlayer

/**
 * PRIMARY source: YouTube Music's "Up next" radio for the song (the RDAMVM mix, the
 * same list ytmusicapi's get_watch_playlist returns): what YouTube Music listeners
 * worldwide play after it.
 */
object YouTubeMusicSource : NextSongSource {
    override val source = Source.YOUTUBE

    override suspend fun getNextSongs(seed: Song): SourceResult {
        val videoId = seed.info.ytId.ifBlank { SongPlayer.youTubeIdFor(SongBridge.playQuery(seed)) }
            ?: return SourceResult(emptyList())
        com.music.spotui.data.recommendation.engine.RecoTrace.log("    YouTube Music: video $videoId" + (if (seed.info.ytId.isBlank()) (" (found by searching '${SongBridge.playQuery(seed)}')") else "") + " → its Up next radio")
        val upNext = YouTube.upNext(videoId).getOrThrow()
        val ranked = upNext.filter { it.videoId != videoId }.map {
            Candidate(title = it.title, artists = it.artists, durationSec = it.durationSec, cover = it.thumbnail, ytId = it.videoId)
        }
        return SourceResult(ranked, seedUpdate = seed.info.copy(ytId = videoId))
    }
}

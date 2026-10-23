package com.music.aalap.data.recommendation.engine

import com.music.aalap.data.entity.SongsModel
import com.music.aalap.di.SongPlayer

/** Conversions between the app's [SongsModel] and the engine's canonical [Song]. */
object SongBridge {

    /** The app id a song gets elsewhere in the app (same formula as Api), so likes and history line up. */
    fun appId(song: Song): Int =
        if (song.info.spotifyId.isNotBlank()) ("track:${song.info.spotifyId}".hashCode() and 0x7fffffff)
        else ("aalap:${song.id}".hashCode() and 0x7fffffff)

    fun toCandidate(s: SongsModel) = Candidate(
        title = s.title,
        artists = s.singer,
        durationSec = s.durationMs / 1000,
        album = s.album,
        cover = s.coverUri,
        spotifyId = s.spotifyTrackId,
        explicit = s.explicit,
    )

    fun playQuery(song: Song): String =
        SongPlayer.buildSpotifyPlayQuery(song.info.spotifyId, song.title, song.artists)

    fun toSongsModel(song: Song): SongsModel {
        val query = playQuery(song)
        // A song YouTube suggested plays that exact video instead of searching again.
        if (song.info.ytId.isNotBlank()) SongPlayer.rememberVideoId(query, song.info.ytId)
        return SongsModel(
            id = appId(song),
            title = song.title,
            album = song.info.album,
            singer = song.artists,
            coverUri = song.info.cover,
            url = query,
            spotifyTrackId = song.info.spotifyId,
            explicit = song.info.explicit,
            durationMs = song.info.durationSec * 1000,
        )
    }
}

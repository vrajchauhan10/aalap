package com.music.spotui.data.recommendation.engine.sources

import com.music.spotui.data.recommendation.engine.Song
import com.music.spotui.data.recommendation.engine.Source
import com.music.spotui.data.recommendation.engine.SourceResult
import com.music.spotui.saavn.JioSaavnSource

/**
 * Third source for Indian and regional music: JioSaavn's song radio. Strong where the
 * global services know little (Haryanvi, Punjabi, Gujarati, Bhojpuri). Also tells us
 * the song's language, which feeds the taste profile.
 */
object JioSaavnRadioSource : NextSongSource {
    override val source = Source.JIOSAAVN

    override suspend fun getNextSongs(seed: Song): SourceResult {
        val hit = if (seed.info.saavnId.isBlank()) JioSaavnSource.findSong(seed.title, seed.artists, seed.info.durationSec) else null
        val id = seed.info.saavnId.ifBlank { hit?.saavnId.orEmpty() }
        if (id.isBlank()) return SourceResult(emptyList())
        return SourceResult(
            JioSaavnSource.radio(id),
            seedUpdate = hit?.let { seed.info.copy(saavnId = it.saavnId, language = seed.info.language.ifBlank { it.language }) },
        )
    }
}

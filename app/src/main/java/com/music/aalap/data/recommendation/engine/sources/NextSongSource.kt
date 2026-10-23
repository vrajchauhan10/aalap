package com.music.aalap.data.recommendation.engine.sources

import com.music.aalap.data.recommendation.engine.Song
import com.music.aalap.data.recommendation.engine.Source
import com.music.aalap.data.recommendation.engine.SourceResult

/**
 * A plug-in "what do people play after this song" source. Each adapter finds the
 * seed on its service (using ids already known for it when it can) and returns that
 * service's ranked list. Adapters throw on failure; the engine times them out, caches
 * their results for 24 hours and carries on without any that fail.
 */
interface NextSongSource {
    val source: Source

    /** False when the adapter can't run (e.g. no API key configured). */
    val enabled: Boolean get() = true

    suspend fun getNextSongs(seed: Song): SourceResult
}

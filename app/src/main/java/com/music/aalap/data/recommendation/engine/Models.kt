package com.music.aalap.data.recommendation.engine

/**
 * The "what plays next" sources. [weight] is the source's weight in Reciprocal Rank
 * Fusion: the two big global signals count fully, the regional/similarity ones less.
 */
enum class Source(val weight: Double) {
    YOUTUBE(1.0),
    SPOTIFY(1.0),
    JIOSAAVN(0.7),
    LASTFM(0.7),
}

/**
 * One song as a source (or the app) knows it. Ids are filled where that source has
 * them; the matcher merges them into one canonical song.
 */
data class Candidate(
    val title: String,
    val artists: String,
    val durationSec: Int = 0,
    val album: String = "",
    val cover: String = "",
    val isrc: String = "",
    val ytId: String = "",
    val spotifyId: String = "",
    val saavnId: String = "",
    val lastfmMbid: String = "",
    val language: String = "",
    val explicit: Boolean = false,
)

/** A song with its canonical id (the `songs` table row). */
data class Song(val id: String, val info: Candidate) {
    val title get() = info.title
    val artists get() = info.artists
}

/** What an adapter returns: its ranked list, plus ids it learned about the seed. */
data class SourceResult(
    val ranked: List<Candidate>,
    val seedUpdate: Candidate? = null,
)

/** A candidate with every score the queue builder used (also the debug endpoint's output). */
data class Scored(
    val song: Song,
    val global: Double,      // fused RRF score, normalised to 0..1 within the candidate set
    val taste: Double,       // 0..1
    val habit: Double,       // 0..1
    val final: Double,
    val sources: Set<Source> = emptySet(),
)

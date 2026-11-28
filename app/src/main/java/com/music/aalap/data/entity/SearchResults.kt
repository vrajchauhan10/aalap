package com.music.aalap.data.entity

/** Combined Spotify search results: tracks, albums, artists, podcasts + episodes. */
data class SearchResults(
    val songs: List<SongsModel> = emptyList(),
    val albums: List<AlbumsModel> = emptyList(),
    val artists: List<ArtistsModel> = emptyList(),
    val shows: List<PodcastModel> = emptyList(),
    val episodes: List<SongsModel> = emptyList(),
    /** The song you meant (the original, or the one whose lyrics you typed), shown big on top. */
    val topSong: SongsModel? = null,
    /** The top song was found by its lyrics, not its title. */
    val topByLyrics: Boolean = false,
)

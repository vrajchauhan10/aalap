package com.music.spotui.ui.repository

import com.music.spotui.data.api.Api
import javax.inject.Inject
import kotlinx.coroutines.flow.map

class AppRepository @Inject constructor(private val api : Api) {

    suspend fun provideAlbums() = api.getAlbums()

    suspend fun provideHomeFeed() = api.getHomeFeed()

    suspend fun provideArtists() = api.getArtists()

    suspend fun provideSongs() = api.getSongs()

    suspend fun searchSongs(query: String) = api.searchTracks(query)

    suspend fun searchEverything(query: String) = api.searchEverything(query)

    suspend fun provideAlbumSongs(albumName: String, artist: String = "") = api.getAlbumSongs(albumName, artist)

    suspend fun provideArtistSongs(artistName: String) = api.getArtistSongs(artistName)

    suspend fun provideArtistOverview(artistName: String, artistId: String = "") = api.getArtistOverview(artistName, artistId)

    /** Aalap playlists live on the phone (and update live as songs are added); the rest come from Spotify. */
    suspend fun providePlaylistSongs(playlistId: String): kotlinx.coroutines.flow.Flow<com.music.spotui.data.api.Response<List<com.music.spotui.data.entity.SongsModel>>> =
        if (com.music.spotui.data.local.AalapPlaylists.isLocal(playlistId))
            com.music.spotui.data.local.AalapPlaylists.version.map {
                com.music.spotui.data.api.Response.Success(
                    com.music.spotui.data.local.AalapPlaylists.get(com.music.spotui.MyApplication.instance, playlistId)?.songs.orEmpty()
                )
            }
        else api.getPlaylistSongs(playlistId)

    suspend fun providePlaylist(playlistId: String): kotlinx.coroutines.flow.Flow<com.music.spotui.data.api.Response<com.music.spotui.data.entity.AlbumsModel>> =
        if (com.music.spotui.data.local.AalapPlaylists.isLocal(playlistId))
            com.music.spotui.data.local.AalapPlaylists.version.map {
                val p = com.music.spotui.data.local.AalapPlaylists.get(com.music.spotui.MyApplication.instance, playlistId)
                com.music.spotui.data.api.Response.Success(
                    com.music.spotui.data.entity.AlbumsModel(
                        id = playlistId.hashCode() and 0x7fffffff,
                        artists = com.music.spotui.data.preferences.getAalapName(com.music.spotui.MyApplication.instance).ifBlank { "You" },
                        coverUri = p?.songs?.firstOrNull()?.coverUri.orEmpty(),
                        name = p?.name ?: "Playlist",
                        time = com.music.spotui.ui.components.counted(p?.songs?.size ?: 0, "song") + " · made in Aalap",
                    )
                )
            }
        else api.getPlaylist(playlistId)

    suspend fun provideShowEpisodes(showId: String) = api.getShowEpisodes(showId)

    suspend fun provideShow(showId: String) = api.getShow(showId)

    suspend fun provideLibrary() = api.getLibrary()

    /** Everyone you follow on Spotify, YouTube Music and in Aalap, each artist once. */
    suspend fun provideFollowedArtists() = com.music.spotui.data.api.YouTubeLibrary.allFollowed(
        com.music.spotui.MyApplication.instance,
        runCatching { api.getFollowedArtists() }.getOrNull(),
    )

    suspend fun provideCategoryPlaylists(genre: String) = api.getCategoryPlaylists(genre)

    /**
     * Every "what next" in the app goes through the shared engine: songs arrive one by
     * one as the chain picks them (each pick seeds the next).
     */
    fun provideRecommendations(seed: com.music.spotui.data.entity.SongsModel, excludeIds: Set<Int> = emptySet()) =
        com.music.spotui.data.recommendation.Recommender.next(com.music.spotui.MyApplication.instance, seed, excludeIds)

    suspend fun provideLikedSongs() = api.getLikedSongs()

    suspend fun provideAccount() = api.getAccount()

    suspend fun provideCanvasUrl(trackId: String) = api.getCanvasUrl(trackId)
}
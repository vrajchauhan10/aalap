package com.music.spotui.ui.repository

import com.music.spotui.data.api.Api
import javax.inject.Inject

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

    suspend fun providePlaylistSongs(playlistId: String) = api.getPlaylistSongs(playlistId)

    suspend fun providePlaylist(playlistId: String) = api.getPlaylist(playlistId)

    suspend fun provideShowEpisodes(showId: String) = api.getShowEpisodes(showId)

    suspend fun provideShow(showId: String) = api.getShow(showId)

    suspend fun provideLibrary() = api.getLibrary()

    /** Followed artists on Spotify plus subscriptions on YouTube Music, each artist once. */
    suspend fun provideFollowedArtists() = com.music.spotui.data.api.YouTubeLibrary.mergeArtists(
        api.getFollowedArtists(),
        com.music.spotui.data.api.YouTubeLibrary.artists(com.music.spotui.MyApplication.instance),
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
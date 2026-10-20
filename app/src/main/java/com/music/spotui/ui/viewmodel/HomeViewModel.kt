package com.music.spotui.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.music.spotui.data.api.Response
import com.music.spotui.data.entity.AlbumsModel
import com.music.spotui.data.entity.ArtistsModel
import com.music.spotui.data.entity.HomeFeedModel
import com.music.spotui.ui.repository.AppRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: AppRepository,
    @dagger.hilt.android.qualifiers.ApplicationContext private val appContext: android.content.Context,
)  : ViewModel() {

    // ── Daily discovery (moods / charts are static lists; these are fetched) ──
    private val _dailyPicks = MutableStateFlow<List<com.music.spotui.data.entity.SongsModel>>(emptyList())
    val dailyPicks: StateFlow<List<com.music.spotui.data.entity.SongsModel>> = _dailyPicks
    private val _favourites = MutableStateFlow<List<com.music.spotui.data.entity.SongsModel>>(emptyList())
    val favourites: StateFlow<List<com.music.spotui.data.entity.SongsModel>> = _favourites
    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing
    /** Day the page was last built for; a new day triggers a rebuild. */
    private var loadedDay: java.time.LocalDate? = null


    private val _albums : MutableStateFlow<Response<List<AlbumsModel>>> = MutableStateFlow(Response.Loading())
    val albums : StateFlow<Response<List<AlbumsModel>>> = _albums

    private val _artists : MutableStateFlow<Response<List<ArtistsModel>>> = MutableStateFlow(Response.Loading())
    val artists : StateFlow<Response<List<ArtistsModel>>> = _artists

    private val _home : MutableStateFlow<Response<HomeFeedModel>> = MutableStateFlow(Response.Loading())
    val home : StateFlow<Response<HomeFeedModel>> = _home


    init {
        fetchHome()
        fetchArtists()
        fetchAlbums()
        loadDiscovery()
    }

    private fun loadDiscovery() = viewModelScope.launch(Dispatchers.IO) {
        loadedDay = java.time.LocalDate.now()
        _favourites.value = com.music.spotui.data.api.Discover.allTimeFavourites(appContext)
        _dailyPicks.value = com.music.spotui.data.api.Discover.dailyPicks(appContext)
    }

    /** Called when Home comes back on screen: rebuild if the date changed since. */
    fun refreshIfNewDay() {
        if (loadedDay != java.time.LocalDate.now()) refresh(force = false)
    }

    /** Pull-to-refresh: reload the Spotify feed and rebuild today's discovery. */
    fun refresh(force: Boolean = true) = viewModelScope.launch(Dispatchers.IO) {
        _refreshing.value = true
        if (force) {
            com.music.spotui.data.api.Discover.clearToday(appContext)
            com.music.spotui.data.api.Api.HomeCache.clear()
        }
        fetchHome().join()
        loadDiscovery().join()
        _refreshing.value = false
    }

    private fun fetchHome() = viewModelScope.launch(Dispatchers.IO) {
        repository.provideHomeFeed().collect { feed ->
            _home.value = feed
        }
    }

    private fun fetchAlbums() = viewModelScope.launch(Dispatchers.IO) {
            repository.provideAlbums().collect{ album ->
                _albums.value = album as Response<List<AlbumsModel>>
            }
    }

    private fun fetchArtists() = viewModelScope.launch(Dispatchers.IO) {
        repository.provideArtists().collect { artist ->
            _artists.value = artist as Response<List<ArtistsModel>>
        }
    }



}
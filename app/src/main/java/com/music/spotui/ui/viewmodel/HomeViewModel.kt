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
    /** 6-hour window the page was last built for; a new window triggers a rebuild. */
    private var loadedWindow: String? = null


    private val _albums : MutableStateFlow<Response<List<AlbumsModel>>> = MutableStateFlow(Response.Loading())
    val albums : StateFlow<Response<List<AlbumsModel>>> = _albums

    private val _artists : MutableStateFlow<Response<List<ArtistsModel>>> = MutableStateFlow(Response.Loading())
    val artists : StateFlow<Response<List<ArtistsModel>>> = _artists

    private val _home : MutableStateFlow<Response<HomeFeedModel>> = MutableStateFlow(Response.Loading())
    val home : StateFlow<Response<HomeFeedModel>> = _home

    // ── Sections made from all your music (Spotify + YouTube Music + Aalap + imports) ──
    private val M = com.music.spotui.data.recommendation.HomeMixes
    /** Quick picks, each tagged with why it's there (Trending, Top hit, Liked, Your taste…). */
    val quickPicks = MutableStateFlow<List<com.music.spotui.data.recommendation.HomeMixes.TodaySong>>(emptyList())
    val recentlyPlayed = MutableStateFlow<List<com.music.spotui.data.entity.SongsModel>>(emptyList())
    val jumpBackIn = MutableStateFlow<List<com.music.spotui.data.entity.HomeItem>>(emptyList())
    val dailyMixes = MutableStateFlow<List<com.music.spotui.data.recommendation.HomeMixes.Mix>>(emptyList())
    val stations = MutableStateFlow<List<ArtistsModel>>(emptyList())
    val newReleases = MutableStateFlow<List<com.music.spotui.data.entity.LibraryEntry>>(emptyList())
    val today = MutableStateFlow<List<com.music.spotui.data.recommendation.HomeMixes.TodaySong>>(emptyList())

    private fun loadUnified(force: Boolean = false) = viewModelScope.launch(Dispatchers.IO) {
        recentlyPlayed.value = M.recentlyPlayed(appContext)
        _favourites.value = M.allTimeFavourites(appContext)
        launch { runCatching { jumpBackIn.value = M.jumpBackIn(appContext) } }
        launch { loadQuickPicks(force) }
        launch { runCatching { today.value = M.todayPlaylist(appContext) } }
        launch { runCatching { stations.value = M.artistStations(appContext) } }
        launch { runCatching { newReleases.value = M.newReleases(appContext) } }
        launch { runCatching { dailyMixes.value = M.dailyMixes(appContext, force) } }
        launch { runCatching { com.music.spotui.data.api.SpotifyTaste.refreshIfDue(appContext) } }
        // Your Mix is rebuilt in the background every 3 hours, ready before you tap it.
        launch { runCatching { M.yourMix(appContext) } }
    }

    private suspend fun loadQuickPicks(force: Boolean) {
        runCatching { quickPicks.value = M.tagged(appContext, M.quickPicks(appContext, force)) }
    }

    /** Home came back on screen: Recently played and favourites change with every play. */
    fun onResumeRefresh() = viewModelScope.launch(Dispatchers.IO) {
        if (_home.value is Response.Error) fetchHome()
        if (today.value.isEmpty()) launch { runCatching { today.value = M.todayPlaylist(appContext) } }
        if (quickPicks.value.isEmpty()) launch { loadQuickPicks(false) }
        recentlyPlayed.value = M.recentlyPlayed(appContext)
        _favourites.value = M.allTimeFavourites(appContext)
    }


    init {
        fetchHome()
        fetchArtists()
        fetchAlbums()
        loadDiscovery()
        loadUnified()
    }

    private fun loadDiscovery() = viewModelScope.launch(Dispatchers.IO) {
        loadedWindow = com.music.spotui.data.api.Discover.currentWindow()
        _dailyPicks.value = com.music.spotui.data.api.Discover.dailyPicks(appContext)
    }

    /** Called when Home comes back on screen: rebuild if a new window started since. */
    fun refreshIfNewDay() {
        if (loadedWindow != com.music.spotui.data.api.Discover.currentWindow()) refresh(force = false)
    }

    /** Pull-to-refresh: reload the Spotify feed and rebuild today's discovery. */
    fun refresh(force: Boolean = true) = viewModelScope.launch(Dispatchers.IO) {
        _refreshing.value = true
        if (force) {
            com.music.spotui.data.api.Discover.clearToday(appContext)
            com.music.spotui.data.api.Api.HomeCache.clear()
        }
        // The spinner waits only for what you see first (new Quick picks and the feed),
        // and never more than 6 s; the slower mixes keep rebuilding in the background.
        val feed = fetchHome()
        val quick = launch { loadQuickPicks(force) }
        loadDiscovery()
        loadUnified(force = false)
        if (force) launch { runCatching { dailyMixes.value = M.dailyMixes(appContext, true) } }
        kotlinx.coroutines.withTimeoutOrNull(6_000) { quick.join(); feed.join() }
        _refreshing.value = false
    }

    private fun fetchHome() = viewModelScope.launch(Dispatchers.IO) {
        repository.provideHomeFeed().collect { feed ->
            // While refreshing, keep the page on screen instead of flashing the skeleton;
            // a failed refresh also keeps what was there.
            val showing = _home.value is Response.Success
            if (showing && feed !is Response.Success) return@collect
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
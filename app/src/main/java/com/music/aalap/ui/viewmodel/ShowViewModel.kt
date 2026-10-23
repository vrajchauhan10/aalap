package com.music.aalap.ui.viewmodel

import androidx.compose.runtime.State
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.music.aalap.data.api.Response
import com.music.aalap.data.entity.PodcastModel
import com.music.aalap.data.entity.SongsModel
import com.music.aalap.di.CurrentSongState
import com.music.aalap.ui.repository.AppRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ShowViewModel @Inject constructor(
    private val repository: AppRepository,
    private val currentSongState: CurrentSongState,
) : ViewModel() {

    val currentSongPlayingState: State<Boolean> get() = currentSongState.playingState
    val currentSongId: State<Int> get() = currentSongState.songId

    private val _episodes: MutableStateFlow<Response<List<SongsModel>>> = MutableStateFlow(Response.Loading())
    val episodes: StateFlow<Response<List<SongsModel>>> = _episodes

    private val _show: MutableStateFlow<PodcastModel?> = MutableStateFlow(null)
    val show: StateFlow<PodcastModel?> = _show

    val queue: State<List<SongsModel>> get() = currentSongState.queue

    fun updateQueue(songs: List<SongsModel>) = currentSongState.updateQueue(songs)

    fun updateSongState(coverUri: String, title: String, singer: String, playingState: Boolean, songId: Int, songIndex: Int = 0, album: String = "") {
        currentSongState.updateSongState(coverUri, title, singer, playingState, songId, songIndex, album)
    }

    private var showKey: String? = null

    fun loadShow(showId: String) {
        if (showKey == showId) return
        showKey = showId
        viewModelScope.launch(Dispatchers.IO) {
            _show.value = repository.provideShow(showId)
        }
        viewModelScope.launch(Dispatchers.IO) {
            repository.provideShowEpisodes(showId).collect { _episodes.value = it }
        }
    }
}

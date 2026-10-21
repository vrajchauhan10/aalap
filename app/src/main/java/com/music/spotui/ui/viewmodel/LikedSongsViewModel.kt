package com.music.spotui.ui.viewmodel

import androidx.compose.runtime.State
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.music.spotui.data.api.Response
import com.music.spotui.data.entity.SongsModel
import com.music.spotui.di.CurrentSongState
import com.music.spotui.ui.repository.AppRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LikedSongsViewModel @Inject constructor(
    private val repository: AppRepository,
    private val currentSongState: CurrentSongState,
) : ViewModel() {

    private val _songs = MutableStateFlow<Response<List<SongsModel>>>(Response.Loading())
    val songs: StateFlow<Response<List<SongsModel>>> = _songs

    val currentSongPlayingState: State<Boolean> get() = currentSongState.playingState
    val currentSongId: State<Int> get() = currentSongState.songId

    val queue: State<List<SongsModel>> get() = currentSongState.queue
    fun updateQueue(songs: List<SongsModel>) = currentSongState.updateQueue(songs)

    fun startShuffled(songs: List<SongsModel>) = currentSongState.startShuffled(songs)

    /** Pause/resume global playback (the header play button stays visible while playing). */
    fun setPlaying(playing: Boolean) {
        if (playing) com.music.spotui.di.SongPlayer.play() else com.music.spotui.di.SongPlayer.pause()
        currentSongState.updatePlayingState(playing)
    }

    fun updateSongState(coverUri: String, title: String, singer: String, playingState: Boolean, songId: Int, songIndex: Int = 0, album: String = "") {
        currentSongState.updateSongState(coverUri, title, singer, playingState, songId, songIndex, album)
    }

    private val ctx get() = com.music.spotui.MyApplication.instance

    /** The saved unified list (Spotify + YouTube Music, each song once): the same list Profile counts. */
    private fun publish() {
        val list = com.music.spotui.data.api.YouTubeLibrary.unifiedLiked(ctx)
        if (list.isNotEmpty() || _songs.value !is Response.Success) _songs.value = Response.Success(list)
    }

    init {
        // Show the saved list at once (built off the main thread), then refresh it after
        // Spotify's pages have all arrived, so the count doesn't climb page by page.
        viewModelScope.launch(Dispatchers.IO) {
            if (com.music.spotui.data.api.YouTubeLibrary.unifiedLiked(ctx).isNotEmpty()) publish()
        }
        viewModelScope.launch(Dispatchers.IO) {
            var failed: Response<List<SongsModel>>? = null
            repository.provideLikedSongs().collect { r -> if (r is Response.Error) failed = r }
            if (com.music.spotui.data.api.YouTubeLibrary.unifiedLiked(ctx).isEmpty() && failed != null) _songs.value = failed!!
            else publish()
        }
        viewModelScope.launch(Dispatchers.IO) {
            if (com.music.spotui.data.api.YouTubeLibrary.syncIfDue(com.music.spotui.MyApplication.instance)) {
                if (_songs.value is Response.Success) publish()
            }
        }
    }

    /** Drops an unliked song from the displayed list without a refetch. */
    fun removeLocally(songId: Int) {
        val current = _songs.value
        if (current is Response.Success) {
            _songs.value = Response.Success(current.data.filterNot { it.id == songId })
        }
    }
}

package com.music.aalap.ui.viewmodel

import androidx.compose.runtime.State
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.music.aalap.data.api.Response
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

    /** A song tapped in a list: it plays, then a mix of recommendations and the list (see [com.music.aalap.di.SmartPlay]). */
    fun playFromList(song: SongsModel, list: List<SongsModel>, name: String, context: android.content.Context) =
        com.music.aalap.di.SmartPlay.fromList(context, currentSongState, song, list, name)

    /** The whole list in order (a list's Play button). */
    fun playAll(list: List<SongsModel>, name: String, context: android.content.Context, index: Int = 0) =
        com.music.aalap.di.SmartPlay.playAll(context, currentSongState, list, name, index)

    fun startShuffled(songs: List<SongsModel>) = currentSongState.startShuffled(songs)

    /** Pause/resume global playback (the header play button stays visible while playing). */
    fun setPlaying(playing: Boolean) {
        if (playing) com.music.aalap.di.SongPlayer.play() else com.music.aalap.di.SongPlayer.pause()
        currentSongState.updatePlayingState(playing)
    }

    fun updateSongState(coverUri: String, title: String, singer: String, playingState: Boolean, songId: Int, songIndex: Int = 0, album: String = "") {
        currentSongState.updateSongState(coverUri, title, singer, playingState, songId, songIndex, album)
    }

    private val ctx get() = com.music.aalap.MyApplication.instance

    /** Liked Songs (Spotify + YouTube Music + likes made here, each song once): the list every screen counts. */
    private fun publish() {
        val list = com.music.aalap.data.preferences.LikedLibrary.all(ctx)
        if (list.isNotEmpty() || _songs.value !is Response.Success) _songs.value = Response.Success(list)
    }

    init {
        // Show the saved list at once (built off the main thread), then refresh it after
        // Spotify's pages have all arrived, so the count doesn't climb page by page.
        viewModelScope.launch(Dispatchers.IO) {
            if (com.music.aalap.data.preferences.LikedLibrary.all(ctx).isNotEmpty()) publish()
        }
        // A like or unlike anywhere (player, menus, notification) updates the list here too.
        viewModelScope.launch(Dispatchers.IO) {
            com.music.aalap.data.preferences.likedVersion.collect { if (_songs.value is Response.Success) publish() }
        }
        viewModelScope.launch(Dispatchers.IO) {
            var failed: Response<List<SongsModel>>? = null
            repository.provideLikedSongs().collect { r -> if (r is Response.Error) failed = r }
            if (com.music.aalap.data.preferences.LikedLibrary.all(ctx).isEmpty() && failed != null) _songs.value = failed!!
            else publish()
        }
        viewModelScope.launch(Dispatchers.IO) {
            if (com.music.aalap.data.api.YouTubeLibrary.syncIfDue(com.music.aalap.MyApplication.instance)) {
                if (_songs.value is Response.Success) publish()
            }
        }
    }

    /** Pull-to-refresh: sync YouTube Music likes and re-read Spotify's, then show the merged list. */
    suspend fun refresh() = kotlinx.coroutines.withContext(Dispatchers.IO) {
        kotlinx.coroutines.coroutineScope {
            launch { runCatching { com.music.aalap.data.api.YouTubeLibrary.syncIfDue(ctx, force = true) } }
            launch { runCatching { repository.provideLikedSongs().collect { } } }
        }
        com.music.aalap.data.api.YouTubeLibrary.invalidateUnified()
        publish()
    }

    /** Drops an unliked song from the displayed list without a refetch. */
    fun removeLocally(songId: Int) {
        val current = _songs.value
        if (current is Response.Success) {
            _songs.value = Response.Success(current.data.filterNot { it.id == songId })
        }
    }
}

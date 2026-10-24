package com.music.aalap.ui.viewmodel

import androidx.compose.runtime.State
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.music.aalap.data.api.Response
import com.music.aalap.data.entity.AlbumsModel
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
class AlbumViewModel @Inject constructor(private val repository: AppRepository, private val currentSongState: CurrentSongState) :  ViewModel() {

    val currentSongPlayingState: State<Boolean> get() = currentSongState.playingState

    val currentSongId: State<Int> get() = currentSongState.songId

    private val _songs : MutableStateFlow<Response<List<SongsModel>>> = MutableStateFlow(Response.Loading())
    val songs : StateFlow<Response<List<SongsModel>>> = _songs

    private val _albums : MutableStateFlow<Response<List<AlbumsModel>>> = MutableStateFlow(Response.Loading())
    val albums : StateFlow<Response<List<AlbumsModel>>> = _albums

    val queue: State<List<SongsModel>> get() = currentSongState.queue

    fun updateQueue(songs: List<SongsModel>) = currentSongState.updateQueue(songs)

    /** A song tapped in a list: it plays, then a mix of recommendations and the list (see [com.music.aalap.di.SmartPlay]). */
    fun playFromList(song: SongsModel, list: List<SongsModel>, name: String, context: android.content.Context) =
        com.music.aalap.di.SmartPlay.fromList(context, currentSongState, song, list, name)

    fun startShuffled(songs: List<SongsModel>) = currentSongState.startShuffled(songs)

    /** Pause/resume global playback (the header play button stays visible while playing). */
    fun setPlaying(playing: Boolean) {
        if (playing) com.music.aalap.di.SongPlayer.play() else com.music.aalap.di.SongPlayer.pause()
        currentSongState.updatePlayingState(playing)
    }

    fun updateSongState(coverUri: String, title: String, singer: String, playingState: Boolean, songId: Int, songIndex : Int = 0, album : String = "") {
        currentSongState.updateSongState(coverUri, title, singer, playingState, songId, songIndex, album)
    }

    val likeState = currentSongState.likeState

    fun updateLikeState(likeState : Boolean){
        currentSongState.updateLikeState(likeState)
    }
    init {
        fetchAlbums()
    }

    private var albumKey: String? = null

    /** Loads the tracks for a specific album (resolved via Spotify search). */
    fun loadAlbumSongs(name: String, artist: String = "") {
        val key = "$name|$artist"
        if (albumKey == key) return
        albumKey = key
        viewModelScope.launch(Dispatchers.IO) {
            repository.provideAlbumSongs(name, artist).collect { songs ->
                _songs.value = songs as Response<List<SongsModel>>
            }
        }
    }

    private fun fetchAlbums() = viewModelScope.launch(Dispatchers.IO) {
        repository.provideAlbums().collect{ album ->
            _albums.value = album as Response<List<AlbumsModel>>
        }
    }


}
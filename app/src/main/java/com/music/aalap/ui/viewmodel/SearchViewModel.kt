package com.music.aalap.ui.viewmodel

import androidx.compose.runtime.State
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.music.aalap.data.api.Response
import com.music.aalap.data.entity.SearchResults
import com.music.aalap.data.entity.SongsModel
import com.music.aalap.di.CurrentSongState
import com.music.aalap.ui.repository.AppRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SearchViewModel @Inject constructor(private val repository: AppRepository, private val currentSongState: CurrentSongState) : ViewModel() {

    private val _songs : MutableStateFlow<Response<List<SongsModel>>> = MutableStateFlow(Response.Loading())
    val songs : StateFlow<Response<List<SongsModel>>> = _songs

    private val _results : MutableStateFlow<Response<SearchResults>> = MutableStateFlow(Response.Success(SearchResults()))
    val results : StateFlow<Response<SearchResults>> = _results

    val likeState = currentSongState.likeState

    val currentSongId: State<Int> get() = currentSongState.songId


    fun updateLikeState(likeState : Boolean){
        currentSongState.updateLikeState(likeState)
    }

    private var searchJob: Job? = null

    /** The artist you searched for, shown at the top as a big card that opens their page. */
    data class ArtistTop(val artist: com.music.aalap.data.entity.ArtistsModel, val songs: List<SongsModel>)
    private val _artistTop = MutableStateFlow<ArtistTop?>(null)
    val artistTop: StateFlow<ArtistTop?> = _artistTop

    /** The artist the search is for: a name that matches what you typed (not just one word of it). */
    private fun artistFor(query: String, results: SearchResults): com.music.aalap.data.entity.ArtistsModel? {
        fun norm(t: String) = t.lowercase().replace(Regex("[^\\p{L}\\p{N}]+"), " ").replace(Regex("\\s+"), " ").trim()
        val q = norm(query)
        if (q.length < 3) return null
        return results.artists.take(3).firstOrNull { norm(it.name) == q }
            ?: results.artists.firstOrNull()?.takeIf { norm(it.name).startsWith(q) && q.length * 2 >= norm(it.name).length }
    }

    private fun loadArtistTop(query: String, results: SearchResults) {
        // Just the artist: a tap opens their page (songs, albums, about). Nothing extra to load.
        _artistTop.value = artistFor(query, results)?.let { ArtistTop(it, emptyList()) }
    }


    init {
        // Start empty — results come from live searches, not the rate-limited
        // personalized top-tracks feed (which returned 429 and blanked the screen).
        _songs.value = Response.Success(emptyList())
    }

    fun updateQueue(songs: List<SongsModel>) = currentSongState.updateQueue(songs)

    /**
     * Start playback of a single search result as a *radio*, the way Spotify does:
     * the queue becomes just this track, then Spotify-recommended tracks (seeded from
     * it) are appended as they load — instead of queuing the rest of the search list
     * (which made playback "just go down the search results"). The append is skipped
     * if the user has since started something else.
     */
    fun startRadioFromSong(song: SongsModel) {
        currentSongState.updateQueue(listOf(song))
        com.music.aalap.di.VoicePlay.openPlayerRequest.value = true // the full player opens
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                repository.provideRecommendations(song, setOf(song.id)).collect { next ->
                    val current = currentSongState.queue.value
                    // Only extend while this radio is still the active queue (user didn't tap away).
                    if (current.firstOrNull()?.id != song.id) throw kotlinx.coroutines.CancellationException("queue replaced")
                    if (current.none { it.id == next.id }) currentSongState.updateQueue(current + next)
                }
            }
        }
    }

    fun updateSongState(coverUri: String, title: String, singer: String, playingState: Boolean, songId : Int, songIndex : Int = 0, album : String = "") {
        currentSongState.updateSongState(coverUri, title, singer, playingState, songId, songIndex, album)
    }

    fun search(query: String) {
        searchJob?.cancel()
        // A different search: the last artist's card goes at once instead of lingering
        // until the new results arrive.
        val q = query.lowercase().trim()
        _artistTop.value?.artist?.name?.lowercase()?.let { a -> if (q.isEmpty() || !(a.startsWith(q) || q.startsWith(a))) _artistTop.value = null }
        searchJob = viewModelScope.launch(Dispatchers.IO) {
            delay(150) // short debounce for snappy real-time results
            repository.searchEverything(query).collect { result ->
                _results.value = result
                if (result is Response.Success) loadArtistTop(query, result.data)
                // Keep the legacy songs flow in sync for any remaining consumers.
                _songs.value = when (result) {
                    is Response.Success -> Response.Success(result.data.songs)
                    is Response.Error -> Response.Error(result.error)
                    is Response.Loading -> Response.Loading()
                }
            }
        }
    }
}
package com.music.aalap.ui.viewmodel

import android.content.Context
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.music.aalap.data.api.Response
import com.music.aalap.data.entity.SongsModel
import com.music.aalap.di.CurrentSongState
import com.music.aalap.di.SongPlayer
import com.music.aalap.ui.navigation.artistRoute
import com.music.aalap.ui.repository.AppRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject constructor(private val currentSongState: CurrentSongState, private val repository: AppRepository) : ViewModel(){

    val currentSongTitle: State<String> get() = currentSongState.title
    val currentSongSinger: State<String> get() = currentSongState.singer
    val currentSongCoverUri: State<String> get() = currentSongState.coverUri
    val currentSongPlayingState: State<Boolean> get() = currentSongState.playingState
    val currentSongIndex : State<Int> get() = currentSongState.songIndex
    val currentSongAlbum : State<String> get() = currentSongState.album

    val currentSongId : State<Int> get() = currentSongState.songId

    val queue : State<List<SongsModel>> get() = currentSongState.queue

    fun updateQueue(songs: List<SongsModel>) = currentSongState.updateQueue(songs)

    /** A song tapped in a list: it plays, then a mix of recommendations and the list (see [com.music.aalap.di.SmartPlay]). */
    fun playFromList(song: SongsModel, list: List<SongsModel>, name: String, context: android.content.Context) =
        com.music.aalap.di.SmartPlay.fromList(context, currentSongState, song, list, name)

    /** The whole list in order (a list's Play button). */
    fun playAll(list: List<SongsModel>, name: String, context: android.content.Context, index: Int = 0) =
        com.music.aalap.di.SmartPlay.playAll(context, currentSongState, list, name, index)

    /** Insert a track right after the one currently playing ("Play next"). */
    fun playNext(song: SongsModel) {
        val q = currentSongState.queue.value.toMutableList()
        q.removeAll { it.id == song.id }
        val cur = q.indexOfFirst { it.id == currentSongId.value }
        val insertAt = (if (cur >= 0) cur + 1 else 0).coerceAtMost(q.size)
        q.add(insertAt, song)
        currentSongState.updateQueue(q)
    }

    /** Append a track to the end of the queue ("Add to queue"). False if already queued. */
    fun addToQueue(song: SongsModel): Boolean {
        val q = currentSongState.queue.value
        if (q.any { it.id == song.id }) return false
        currentSongState.updateQueue(q + song)
        return true
    }

    /** Reorder the queue, moving the track at [from] to [to] (both absolute indices). */
    fun moveQueueItem(from: Int, to: Int) {
        val q = currentSongState.queue.value.toMutableList()
        if (from !in q.indices || to !in q.indices || from == to) return
        q.add(to, q.removeAt(from))
        currentSongState.updateQueue(q)
    }

    /** Remove a track from the queue entirely. */
    fun removeFromQueue(song: SongsModel) {
        val q = currentSongState.queue.value.toMutableList()
        q.removeAll { it.id == song.id }
        currentSongState.updateQueue(q)
    }


    val shuffleState = currentSongState.shuffle
    val repeatState = currentSongState.repeat
    val likeState = currentSongState.likeState


    private val _songs : MutableStateFlow<Response<List<SongsModel>>> = MutableStateFlow(Response.Loading())
    val songs : StateFlow<Response<List<SongsModel>>> = _songs

    val playingArtist by mutableStateOf(currentSongSinger.value)

    init {
        fetchSongs()
    }


    //val songsResponse = (songs.value as Response.Success).data

    // Resolve where we currently are in the queue. The stored index can be stale
    // (e.g. queue swapped out), so match by song id first and fall back to the index.
    private fun currentPositionIn(queueSongs: List<SongsModel>): Int {
        val byId = queueSongs.indexOfFirst { it.id == currentSongId.value }
        if (byId >= 0) return byId
        return currentSongIndex.value.coerceIn(0, queueSongs.size - 1)
    }

    // ── Autoplay radio (Spotify recommendations) ──
    // When the queue nears its end, fetch Spotify-recommended tracks seeded by what's
    // playing and append them, so music keeps going like Spotify's autoplay instead of
    // looping the same list. On by default; can be turned off via [autoplayRadioEnabled].
    var autoplayRadioEnabled = true
    @Volatile private var radioLoading = false

    private fun maybeExtendRadio(queueSongs: List<SongsModel>, cur: Int) {
        if (!autoplayRadioEnabled || radioLoading) return
        // Looping the queue: it starts over at the end, nothing gets added.
        if (currentSongState.loopsQueue) return
        // Only start fetching when we're within one track of the end.
        if (cur < queueSongs.size - 2) return
        // The chain continues from the last song in the queue; each song is appended as soon as it's picked.
        val seed = queueSongs.lastOrNull() ?: return
        radioLoading = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                repository.provideRecommendations(seed, queueSongs.mapTo(HashSet()) { it.id }).collect { song ->
                    val existing = currentSongState.queue.value
                    if (existing.none { it.id == song.id }) currentSongState.updateQueue(existing + song)
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                android.util.Log.w("PlayerViewModel", "radio top-up failed: $e")
            } finally {
                radioLoading = false
            }
        }
    }

    // Function to play the next song in the album
    fun playNextSongs(queueSongs : List<SongsModel>, context: Context) {
        if (queueSongs.isEmpty()) return
        // A crossfade is already advancing the queue itself — don't double-skip.
        if (SongPlayer.isCrossfadeActive()) return
        val cur = currentPositionIn(queueSongs)
        // Top up the queue with Spotify recommendations as we approach the end.
        maybeExtendRadio(queueSongs, cur)
        if (cur >= queueSongs.size - 1 && currentSongState.loopsQueue) {
            // Loop the playlist: back to its first song ("once" turns itself off after this lap).
            if (currentSongState.repeatMode.value == com.music.aalap.di.RepeatMode.LIST_ONCE) currentSongState.updateRepeatMode(com.music.aalap.di.RepeatMode.OFF)
            val first = queueSongs[0]
            updateSongState(first.coverUri, first.title, first.singer, true, first.id, 0, first.album)
            SongPlayer.playSong(first.url, context)
            return
        }
        if (cur >= queueSongs.size - 1 && autoplayRadioEnabled) {
            // End of the queue (e.g. a single). Don't loop back to the start —
            // wait for the radio fetch kicked off above to append tracks and
            // continue into them, like Spotify's autoplay.
            continueIntoRadio(queueSongs, context)
            return
        }
        val nextIdx = if (cur < queueSongs.size - 1) cur + 1 else 0
        val nextSong = queueSongs[nextIdx]
        updateSongState(
            nextSong.coverUri,
            nextSong.title,
            nextSong.singer,
            true,
            nextSong.id,
            nextIdx,
            nextSong.album
        )
        SongPlayer.playSong(nextSong.url, context)
    }

    /**
     * Opens the artist page for a track: resolves the EXACT artist (name + id)
     * from the track's Spotify id when available, so a display name like "RAM"
     * can't fuzzy-match to "Rammstein". Falls back to the display name.
     */
    // Spotify Canvas (looping video) URL for the current track, or null. Fetched
    // per track; null means no canvas / not resolved yet.
    private val _canvasUrl = mutableStateOf<String?>(null)
    val canvasUrl: State<String?> get() = _canvasUrl
    @Volatile private var canvasRequestId: String = ""

    fun loadCanvas(trackId: String) {
        // Queue edits re-run this for the same track; clearing would rebuild the
        // video player and flash black for a second.
        if (trackId == canvasRequestId && _canvasUrl.value != null) return
        _canvasUrl.value = null
        canvasRequestId = trackId
        if (trackId.isBlank()) return
        // Song videos can be turned off (or kept to Wi-Fi) in Profile → Playback.
        if (!com.music.aalap.data.preferences.songVideosAllowed(com.music.aalap.MyApplication.instance)) return
        viewModelScope.launch {
            val url = withContext(Dispatchers.IO) { repository.provideCanvasUrl(trackId) }
            // Ignore a slow fetch for a track the user already skipped past.
            if (canvasRequestId == trackId) _canvasUrl.value = url
        }
    }

    fun goToArtist(trackId: String, fallbackName: String, navigate: (route: String) -> Unit) {
        viewModelScope.launch {
            val route = withContext(Dispatchers.IO) {
                val artist = if (trackId.isNotBlank())
                    com.metrolist.spotify.Spotify.track(trackId).getOrNull()?.artists?.firstOrNull()
                else null
                artistRoute(
                    artist?.name?.ifBlank { null } ?: fallbackName.substringBefore(",").trim(),
                    artist?.id.orEmpty(),
                )
            }
            navigate(route)
        }
    }

    @Volatile private var awaitingRadioContinue = false

    /** Waits (max ~10s) for the autoplay radio to extend the queue past
     *  [queueSongs] and plays the first appended track; falls back to looping
     *  the queue if no radio tracks arrive. */
    private fun continueIntoRadio(queueSongs: List<SongsModel>, context: Context) {
        if (awaitingRadioContinue) return
        awaitingRadioContinue = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                repeat(40) {
                    val q = currentSongState.queue.value
                    if (q.size > queueSongs.size) {
                        val next = q[queueSongs.size]
                        withContext(Dispatchers.Main) {
                            updateSongState(next.coverUri, next.title, next.singer, true, next.id, queueSongs.size, next.album)
                            SongPlayer.playSong(next.url, context)
                        }
                        return@launch
                    }
                    delay(250L)
                }
                // Radio never arrived — stop cleanly instead of looping the same
                // track again (issue 1: song replayed itself when repeat was off).
                withContext(Dispatchers.Main) {
                    SongPlayer.stop()
                    currentSongState.updatePlayingState(false)
                }
            } finally {
                awaitingRadioContinue = false
            }
        }
    }

    /**
     * Play the track at an absolute [index] in [queueSongs]. Used by the now-playing
     * swipe pager, where the artwork follows the finger and settles on a chosen page.
     * No-op if [index] is already the current track (prevents a feedback replay when
     * the pager is merely snapping to reflect an external track change).
     */
    /**
     * Plays [song] on its own, then continues with recommendations for it (the shared
     * engine), instead of playing the list it was tapped from in order.
     */
    fun playWithRadio(song: SongsModel, context: Context) {
        com.music.aalap.data.recommendation.PlayContext.chose(song)
        updateQueue(listOf(song))
        updateSongState(song.coverUri, song.title, song.singer, true, song.id, 0, song.album)
        SongPlayer.playSong(song.url, context)
        appendRadio(song)
    }

    /**
     * Appends the engine's chain after [song], one song at a time, while [song]'s radio
     * is still the active queue (the user didn't start something else).
     */
    private fun appendRadio(song: SongsModel) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                repository.provideRecommendations(song, setOf(song.id)).collect { next ->
                    val current = currentSongState.queue.value
                    if (current.firstOrNull()?.id != song.id) throw kotlinx.coroutines.CancellationException("queue replaced")
                    if (current.none { it.id == next.id }) currentSongState.updateQueue(current + next)
                }
            }
        }
    }

    fun playSongAt(queueSongs: List<SongsModel>, index: Int, context: Context) {
        if (index !in queueSongs.indices) return
        val song = queueSongs[index]
        if (song.id == currentSongId.value) return
        maybeExtendRadio(queueSongs, index)
        updateSongState(song.coverUri, song.title, song.singer, true, song.id, index, song.album)
        SongPlayer.playSong(song.url, context)
    }

    // Function to play the previous song in the album
    fun playPreviousSong(queueSongs : List<SongsModel>, context: Context) {
        if (queueSongs.isEmpty()) return
        val cur = currentPositionIn(queueSongs)
        val prevIdx = if (cur > 0) cur - 1 else queueSongs.size - 1
        val previousSong = queueSongs[prevIdx]
        updateSongState(previousSong.coverUri, previousSong.title, previousSong.singer, true, previousSong.id, prevIdx, previousSong.album)
        SongPlayer.playSong(previousSong.url, context)
    }

    private fun fetchSongs() = viewModelScope.launch(Dispatchers.IO) {

        repository.provideSongs().collect { songs ->
            _songs.value = songs as Response<List<SongsModel>>

        }
    }
    fun formatDuration(durationMillis: Long): String {
        // Unknown/unset lengths arrive as large negatives; never print those.
        if (durationMillis <= 0L) return "0:00"
        val minutes = TimeUnit.MILLISECONDS.toMinutes(durationMillis)
        val seconds = TimeUnit.MILLISECONDS.toSeconds(durationMillis) - TimeUnit.MINUTES.toSeconds(minutes)
        return String.format("%01d:%02d", minutes, seconds)
    }
    fun updateSongState(coverUri: String, title: String, singer: String, playingState: Boolean, songId : Int, songIndex : Int = 0, album : String = "") {
        currentSongState.updateSongState(coverUri, title, singer, playingState, songId, songIndex, album)
    }

    fun updateShuffleState(shuffleState : Boolean){
        currentSongState.updateShuffleState(shuffleState)
    }
    fun updateRepeatState(repeatState : Boolean){
        currentSongState.updateRepeatState(repeatState)
    }

    /** "More / less like this", "don't play this song / artist" (see [com.music.aalap.di.SmartPlay.feedback]). */
    fun feedback(song: SongsModel, kind: com.music.aalap.data.recommendation.engine.Feedback.Kind, context: Context) =
        com.music.aalap.di.SmartPlay.feedback(context, currentSongState, song, kind)

    val repeatMode = currentSongState.repeatMode
    fun updateRepeatMode(mode: com.music.aalap.di.RepeatMode) = currentSongState.updateRepeatMode(mode)
    fun updateLikeState(likeState : Boolean){
        currentSongState.updateLikeState(likeState)
    }
}

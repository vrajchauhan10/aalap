package com.music.aalap.di

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import com.music.aalap.data.entity.SongsModel
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The loop button. Tap cycles Off → loop the queue → loop this song; press and hold
 * for the "once" versions.
 */
enum class RepeatMode(val label: String) {
    OFF("Loop off"),
    /** The whole queue (playlist, album…) over and over: back to the first song at the end, no recommendations. */
    LIST("Looping the playlist"),
    /** The queue one more time, then recommendations carry on as usual. */
    LIST_ONCE("Playing the playlist once more"),
    /** This song over and over. */
    SONG("Looping this song"),
    /** This song one more time, then the queue carries on. */
    SONG_ONCE("Playing this song once more");

    companion object {
        /**
         * The song at [cur] of a queue of [size] ended under [mode]: which index plays next
         * (null = carry on into recommendations) and the mode afterwards ("once" is used up).
         */
        fun afterEnd(mode: RepeatMode, cur: Int, size: Int): Pair<Int?, RepeatMode> = when {
            size == 0 -> null to mode
            mode == SONG -> cur to SONG
            mode == SONG_ONCE -> cur to OFF
            cur in 0 until size - 1 -> cur + 1 to mode
            mode == LIST -> 0 to LIST
            mode == LIST_ONCE -> 0 to OFF
            else -> null to mode
        }
    }
}

@Singleton
class CurrentSongState @Inject constructor() {
    private val _title: MutableState<String> = mutableStateOf("")
    val title: State<String> get() = _title

    private val _album: MutableState<String> = mutableStateOf("")
    val album : State<String> get() = _album

    private val _singer: MutableState<String> = mutableStateOf("")
    val singer: State<String> get() = _singer

    private val _coverUri: MutableState<String> = mutableStateOf("")
    val coverUri: State<String> get() = _coverUri

    private val _playingState: MutableState<Boolean> = mutableStateOf(false)
    val playingState: State<Boolean> get() = _playingState

    private val _songIndex: MutableState<Int> = mutableStateOf(0)
    val songIndex : State<Int> get() = _songIndex

    private val _songId: MutableState<Int> = mutableStateOf(0)
    val songId : State<Int> get() = _songId

    // The actual list the user is playing (album tracks, search results, liked
    // songs…). Next/previous operate on THIS, not on a re-derived global feed.
    private val _queue: MutableState<List<SongsModel>> = mutableStateOf(emptyList())
    val queue: State<List<SongsModel>> get() = _queue

    fun updateQueue(queue: List<SongsModel>) {
        // Playback finds the current track by id, and the queue screen keys rows by
        // id, so a track listed twice (e.g. in a playlist) is queued once.
        val songs = queue.distinctBy { it.id }
        _queue.value = songs
        // Seed the lossless resolver: map each track's play query → its Spotify id so
        // SongPlayer can resolve a FLAC stream from a play site that only has the query.
        SongPlayer.registerLossless(songs.map { it.url to it.spotifyTrackId })
        SongPlayer.registerAlternativeKeys(songs.map {
            it.url to com.music.aalap.data.preferences.alternativeStreamKey(it)
        })
        // Seed explicit flags so the YouTube fallback picks the matching edit.
        SongPlayer.registerExplicit(songs.map { it.url to it.explicit })
        // Seed durations so the YouTube match can reject same-title wrong-artist
        // songs (they almost always have a different length).
        SongPlayer.registerDuration(songs.mapNotNull { s -> if (s.durationMs > 0) s.url to s.durationMs else null })
        // Seed exact title/artist/album metadata so the YouTube fallback can score
        // candidates against the actual Spotify track instead of only the search
        // query string.
        SongPlayer.registerMetadata(songs.map {
            it.url to SongPlayer.TrackMatchMetadata(
                title = it.title,
                artist = it.singer,
                album = it.album,
            )
        })
        // Seed the lyrics resolver with track ids so it can use Spotify's own
        // color-lyrics endpoint (exact synced lyrics) instead of LRCLIB matching.
        com.music.aalap.data.api.LyricsApi.registerTracks(songs)
    }

    val shuffle = mutableStateOf(false)
    /** True while the current song is set to repeat ([RepeatMode.SONG] or [RepeatMode.SONG_ONCE]). */
    val repeat = mutableStateOf(false)
    val repeatMode = mutableStateOf(RepeatMode.OFF)
    val likeState = mutableStateOf(false)

    // Original queue order, kept while shuffle is on so turning it off restores
    // the list instead of leaving it permanently scrambled.
    private var unshuffledQueue: List<SongsModel>? = null

    /**
     * Toggling shuffle reorders the queue ONCE: the current track stays where it
     * is and the rest follow in random order. (Skipping used to re-shuffle the
     * whole list on every tap, which could repeat or skip songs.)
     */
    fun updateShuffleState(newShuffleState: Boolean) {
        if (newShuffleState == shuffle.value) return
        shuffle.value = newShuffleState
        val q = _queue.value
        if (newShuffleState) {
            unshuffledQueue = q
            val curIdx = q.indexOfFirst { it.id == _songId.value }
            if (curIdx >= 0) {
                _queue.value = listOf(q[curIdx]) +
                    q.filterIndexed { i, _ -> i != curIdx }.shuffled()
                _songIndex.value = 0
            } else {
                _queue.value = q.shuffled()
            }
        } else {
            val original = unshuffledQueue
            unshuffledQueue = null
            // Restore only if we're still inside that queue (it may have been
            // replaced by another list while shuffled). Keep tracks appended in
            // the meantime (queue edits, autoplay radio).
            if (original != null && original.any { it.id == _songId.value }) {
                val appended = q.filter { s -> original.none { it.id == s.id } }
                val restored = original + appended
                _queue.value = restored
                val idx = restored.indexOfFirst { it.id == _songId.value }
                if (idx >= 0) _songIndex.value = idx
            }
        }
    }

    /**
     * Starts shuffled playback of a full list (the shuffle button on
     * playlist/album/liked screens): the queue becomes a shuffled copy, shuffle
     * turns on, and the caller plays the returned first track.
     */
    fun startShuffled(songs: List<SongsModel>): SongsModel? {
        if (songs.isEmpty()) return null
        updateQueue(songs.shuffled())
        unshuffledQueue = songs
        shuffle.value = true
        return _queue.value.firstOrNull()
    }
    fun updateRepeatState(newRepeatState : Boolean){
        updateRepeatMode(if (newRepeatState) RepeatMode.SONG else RepeatMode.OFF)
    }

    fun updateRepeatMode(mode: RepeatMode) {
        repeatMode.value = mode
        repeat.value = mode == RepeatMode.SONG || mode == RepeatMode.SONG_ONCE
    }

    /** Whether the queue goes back to its first song at the end (instead of recommendations). */
    val loopsQueue: Boolean get() = repeatMode.value == RepeatMode.LIST || repeatMode.value == RepeatMode.LIST_ONCE

    /**
     * The current song ended. Returns what to do: repeat it, play [index], or null for
     * "go on to recommendations". Uses up a "once" loop.
     */
    fun nextAfterEnd(): Int? {
        val q = _queue.value
        val (next, mode) = RepeatMode.afterEnd(repeatMode.value, q.indexOfFirst { it.id == _songId.value }, q.size)
        if (mode != repeatMode.value) updateRepeatMode(mode)
        return next
    }

    /** Sync the play/pause state without touching the rest of the now-playing
     *  metadata — used to reflect the web player's real state (e.g. after the
     *  system notification's pause button) back into the in-app UI. */
    fun updatePlayingState(playing: Boolean) {
        _playingState.value = playing
    }

    fun updateLikeState(newLikeState : Boolean){
        likeState.value = newLikeState
    }

    /** The last song handed to the recommendation engine (pause/resume isn't a new play). */
    private var lastLoggedSongId = Int.MIN_VALUE

    fun updateSongState(coverUri: String, title: String, singer: String, playingState: Boolean, songId : Int, songIndex : Int, album : String) {
        _coverUri.value = coverUri
        _title.value = title
        _album.value = album
        _singer.value = singer
        _playingState.value = playingState
        _songIndex.value = songIndex
        _songId.value = songId
        // Feed the system media notification (MediaSession) with the current track.
        SongPlayer.setNowPlayingMeta(title, singer, coverUri)
        // Persist the current track so a fresh launch can restore the session.
        if (playingState && title.isNotBlank()) {
            _queue.value.firstOrNull { it.id == songId }?.let { track ->
                com.music.aalap.data.preferences.saveLastPlayback(
                    com.music.aalap.MyApplication.instance, track)
            }
        }
        // Fast loading: resolve the next two queue songs now so skipping to them starts at once.
        if (playingState && com.music.aalap.data.preferences.isFastLoading(com.music.aalap.MyApplication.instance)) {
            val idx = _queue.value.indexOfFirst { it.id == songId }
            if (idx >= 0) _queue.value.drop(idx + 1).take(2).forEach {
                SongPlayer.prefetch(it.url, com.music.aalap.MyApplication.instance)
            }
        }
        // Warm the lyrics cache in the background so they're already loaded by the
        // time the user opens the player / scrolls to the lyrics card.
        if (playingState && title.isNotBlank()) {
            // With the length, so a fallback match is checked against it before caching.
            val durationSec = (_queue.value.firstOrNull { it.id == songId }?.durationMs ?: 0) / 1000
            com.music.aalap.data.api.LyricsApi.prefetch(title, singer, album, durationSec)
            // Log the play into the local listening history (History & stats screen).
            // Not for songs the offline skip passes over: those never actually play.
            val trackUrl = _queue.value.firstOrNull { it.id == songId }?.url.orEmpty()
            val skippedOffline = com.music.aalap.data.preferences.OfflineState.isOffline &&
                !SongPlayer.playableOffline(trackUrl, com.music.aalap.MyApplication.instance)
            // Recommendation engine: logs the play (skips, habits) and updates the taste.
            val track = _queue.value.firstOrNull { it.id == songId }
            // The audio that plays from now on belongs to this song (tempo and energy measuring).
            if (track != null) com.music.aalap.audio.AudioAnalysis.newSong(track)
            if (!skippedOffline && track != null && songId != lastLoggedSongId) {
                lastLoggedSongId = songId
                val q = _queue.value
                val upcoming = q.drop(q.indexOfFirst { it.id == songId } + 1)
                com.music.aalap.data.recommendation.engine.QueueEngine.onPlay(com.music.aalap.MyApplication.instance, track, upcoming = upcoming) { drop ->
                    // You skipped a song the engine queued: drop the queued ones too much like it.
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        val now = _queue.value
                        val cur = now.indexOfFirst { it.id == _songId.value }
                        if (cur >= 0) _queue.value = now.filterIndexed { i, s -> i <= cur || s.id !in drop }
                    }
                }
            }
            if (!skippedOffline) com.music.aalap.data.preferences.addListeningHistory(
                com.music.aalap.MyApplication.instance,
                com.music.aalap.data.preferences.HistoryEntry(
                    ts = System.currentTimeMillis(),
                    songId = songId,
                    title = title,
                    singer = singer,
                    album = album,
                    image = coverUri,
                    url = _queue.value.firstOrNull { it.id == songId }?.url.orEmpty(),
                    spotifyTrackId = _queue.value.firstOrNull { it.id == songId }?.spotifyTrackId.orEmpty(),
                    durationMs = _queue.value.firstOrNull { it.id == songId }?.durationMs ?: 0,
                ),
            )
            // Start measuring this song's real listening time.
            if (!skippedOffline) com.music.aalap.data.preferences.ListenClock.songStarted(com.music.aalap.MyApplication.instance, songId)
        }
    }
}

package com.music.aalap.di

import android.content.Context
import com.music.aalap.data.entity.SongsModel
import com.music.aalap.data.recommendation.PlayContext
import com.music.aalap.data.recommendation.Recommender
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Starting music from a list.
 *
 *  - Tap a song in Liked Songs, a playlist, Downloads or an artist's songs: that song
 *    plays, then recommendations that fit it, with songs from the list mixed in now and
 *    then at random (only ones that fit). The rest of the list doesn't play in order.
 *  - The list's Play or Shuffle button: the whole list, as before ([playAll]).
 *
 * Offline, or for a list of three songs or fewer, a tap plays the list from that song,
 * since there's nothing to recommend.
 */
object SmartPlay {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var job: Job? = null

    fun fromList(context: Context, state: CurrentSongState, song: SongsModel, list: List<SongsModel>, name: String) {
        val app = context.applicationContext
        if (com.music.aalap.data.preferences.OfflineState.isOffline || list.size <= 3) {
            playAll(app, state, list, name, list.indexOfFirst { it.id == song.id }.coerceAtLeast(0))
            return
        }
        job?.cancel()
        state.updateQueue(listOf(song))
        state.updateSongState(song.coverUri, song.title, song.singer, true, song.id, 0, name)
        SongPlayer.playSong(song.url, app)
        PlayContext.choseFromList(song, list, name)
        job = scope.launch {
            runCatching {
                Recommender.next(app, song, setOf(song.id), length = 6).collect { next ->
                    val q = state.queue.value
                    // You started something else meanwhile: stop adding to this one.
                    if (q.firstOrNull()?.id != song.id) throw CancellationException("queue replaced")
                    if (q.none { it.id == next.id }) state.updateQueue(q + next)
                }
            }
        }
    }

    /** One song (a voice request, a search result): it plays, then recommendations that fit it. */
    fun single(context: Context, state: CurrentSongState, song: SongsModel) {
        val app = context.applicationContext
        job?.cancel()
        state.updateQueue(listOf(song))
        state.updateSongState(song.coverUri, song.title, song.singer, true, song.id, 0, song.album)
        SongPlayer.playSong(song.url, app)
        PlayContext.chose(song)
        job = scope.launch {
            runCatching {
                Recommender.next(app, song, setOf(song.id), length = 6).collect { next ->
                    val q = state.queue.value
                    if (q.firstOrNull()?.id != song.id) throw CancellationException("queue replaced")
                    if (q.none { it.id == next.id }) state.updateQueue(q + next)
                }
            }
        }
    }

    /** The whole list in order from [index] (a list's Play button). */
    fun playAll(context: Context, state: CurrentSongState, list: List<SongsModel>, name: String, index: Int = 0) {
        val song = list.getOrNull(index) ?: return
        job?.cancel()
        state.updateQueue(list)
        state.updateSongState(song.coverUri, song.title, song.singer, true, song.id, index, name)
        SongPlayer.playSong(song.url, context.applicationContext)
        PlayContext.playingList(list, name)
    }
}

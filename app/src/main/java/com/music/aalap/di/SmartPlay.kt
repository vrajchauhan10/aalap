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
import com.music.aalap.data.recommendation.engine.Feedback.Kind as K

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

    /**
     * Your feedback on [song] from its menu. It's remembered for good, and acts now:
     * "more like this" redoes the queued recommendations toward it; "less like this",
     * "don't play this song" and "don't play this artist" skip it if it's playing and
     * take matching songs out of the queue.
     */
    fun feedback(context: Context, state: CurrentSongState, song: SongsModel, kind: com.music.aalap.data.recommendation.engine.Feedback.Kind) {
        val app = context.applicationContext
        when (kind) {
            K.MORE -> PlayContext.nudge(song, 1.0)
            K.LESS -> PlayContext.nudge(song, -1.0)
            else -> {}
        }
        scope.launch {
            runCatching { com.music.aalap.data.recommendation.engine.Feedback.record(app, song, kind) }
            val artist = com.music.aalap.data.recommendation.engine.SongMatcher.artistKeys(song.singer).firstOrNull()
            kotlinx.coroutines.withContext(Dispatchers.Main) {
                val q = state.queue.value
                val cur = q.indexOfFirst { it.id == state.songId.value }
                // Take out what shouldn't play any more.
                val gone: (SongsModel) -> Boolean = { s ->
                    when (kind) {
                        K.BLOCK_SONG, K.LESS -> s.id == song.id
                        K.BLOCK_ARTIST -> artist != null && artist in com.music.aalap.data.recommendation.engine.SongMatcher.artistKeys(s.singer)
                        K.MORE -> false
                    }
                }
                val skipNow = cur >= 0 && kind != K.MORE && gone(q[cur])
                // The engine's queued picks after the current song are redone with the feedback.
                val kept = q.filterIndexed { i, s -> i <= cur || (!gone(s) && !PlayContext.isAuto(s.id)) }
                state.updateQueue(kept)
                val playing = kept.getOrNull(cur.coerceAtLeast(0))
                if (skipNow) {
                    val next = kept.getOrNull(cur + 1)
                    if (next != null) {
                        state.updateSongState(next.coverUri, next.title, next.singer, true, next.id, cur + 1, next.album)
                        SongPlayer.playSong(next.url, app)
                    }
                }
                val seed = if (skipNow) kept.getOrNull(cur + 1) ?: playing else playing
                // Only where the engine was choosing (a playlist playing in order stays as it is).
                val hadPicks = q.drop(cur + 1).any { PlayContext.isAuto(it.id) }
                if (seed != null && (hadPicks || kept.size <= cur + 2)) refill(app, state, seed)
                android.widget.Toast.makeText(app, when (kind) {
                    K.MORE -> "More like this, coming up"
                    K.LESS -> "Less like this from now on"
                    K.BLOCK_SONG -> "Won't play this song again"
                    K.BLOCK_ARTIST -> "Won't play ${song.singer.substringBefore(",")} again"
                }, android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    /** Adds fresh recommendations after [seed] (the engine sees the feedback). */
    private fun refill(app: Context, state: CurrentSongState, seed: SongsModel) {
        job?.cancel()
        job = scope.launch {
            runCatching {
                Recommender.next(app, seed, state.queue.value.mapTo(HashSet()) { it.id }, length = 6).collect { next ->
                    val q = state.queue.value
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

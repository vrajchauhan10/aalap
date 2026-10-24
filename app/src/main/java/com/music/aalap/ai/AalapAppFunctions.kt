package com.music.aalap.ai

import android.content.ComponentName
import android.content.Context
import androidx.appfunctions.AppFunctionContext
import androidx.appfunctions.AppFunctionSerializable
import androidx.appfunctions.service.AppFunction
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionToken
import com.music.aalap.di.CurrentSongState
import com.music.aalap.ui.notification.PlaybackService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Aalap's actions for phone assistants and agents (Gemini) through Android AppFunctions:
 * "Play Kesariya on Aalap", "pause Aalap", "what's playing on Aalap?".
 *
 * Each call goes through Aalap's media session, the same way the widget, the
 * notification and screen-off voice requests do, so it also works while Aalap is closed.
 * The descriptions below are what the assistant reads to pick a function.
 */
class AalapAppFunctions(private val state: CurrentSongState) {

    /**
     * Plays music in Aalap, the user's music app. Use this whenever the user asks to play
     * music "on Aalap" or "in Aalap". It understands a song ("Kesariya", "Kesariya by
     * Arijit Singh"), an artist ("Arijit Singh songs"), a mood or genre ("party songs",
     * "lofi", "sad songs", "bhajans", "workout music", "road trip songs"), "my liked songs",
     * "Aaj ka Aalap" (the user's playlist for today), or nothing in particular ("play
     * music"), which carries on with what was playing. After the first song, Aalap keeps
     * playing recommendations that fit it.
     *
     * @param appFunctionContext The context in which the function is executed.
     * @param request What to play, in the user's words, for example "Kesariya by Arijit Singh",
     *   "party songs" or "my liked songs". Leave empty to carry on with what was playing.
     * @return The song that is now playing.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun playMusic(appFunctionContext: AppFunctionContext, request: String?): NowPlaying {
        val ctx = appFunctionContext.context
        val before = state.title.value to state.songId.value
        command(ctx) { c ->
            c.setMediaItem(
                MediaItem.Builder().setMediaId("")
                    .setRequestMetadata(MediaItem.RequestMetadata.Builder().setSearchQuery(request.orEmpty()).build())
                    .build(),
            )
            c.prepare()
            c.play()
        }
        // Finding and starting the song takes a few seconds.
        withTimeoutOrNull(20_000) {
            while (true) {
                // Aalap finished this request ("'…' → plan → result"): nothing found, or the
                // song was already the one playing.
                val handled = com.music.aalap.di.VoicePlay.last.let { it.startsWith("'${request.orEmpty()}'") && it.split(" → ").size >= 3 }
                if (handled && com.music.aalap.di.VoicePlay.last.endsWith("nothing found")) break
                val changed = (state.title.value to state.songId.value) != before || request.isNullOrBlank() || handled
                if (changed && playing()) break
                delay(300)
            }
        }
        val result = com.music.aalap.di.VoicePlay.last
        return nowPlaying(if (result.contains("nothing found")) "Aalap couldn't find \"${request.orEmpty()}\"." else "")
    }

    /**
     * Pauses the music playing in Aalap.
     *
     * @param appFunctionContext The context in which the function is executed.
     * @return The song that was playing, now paused.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun pauseMusic(appFunctionContext: AppFunctionContext): NowPlaying {
        command(appFunctionContext.context) { it.pause() }
        return nowPlaying()
    }

    /**
     * Resumes the music in Aalap where it was paused.
     *
     * @param appFunctionContext The context in which the function is executed.
     * @return The song that is now playing.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun resumeMusic(appFunctionContext: AppFunctionContext): NowPlaying =
        playMusic(appFunctionContext, null)

    /**
     * Skips to the next song in Aalap.
     *
     * @param appFunctionContext The context in which the function is executed.
     * @return The song that is now playing.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun nextSong(appFunctionContext: AppFunctionContext): NowPlaying = skip(appFunctionContext.context, forward = true)

    /**
     * Goes back to the previous song in Aalap.
     *
     * @param appFunctionContext The context in which the function is executed.
     * @return The song that is now playing.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun previousSong(appFunctionContext: AppFunctionContext): NowPlaying = skip(appFunctionContext.context, forward = false)

    /**
     * Tells which song is playing in Aalap right now (title and artist), and whether it is
     * playing or paused.
     *
     * @param appFunctionContext The context in which the function is executed.
     * @return The current song.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun getNowPlaying(appFunctionContext: AppFunctionContext): NowPlaying = nowPlaying()

    /**
     * Adds the song playing in Aalap to the user's Liked Songs.
     *
     * @param appFunctionContext The context in which the function is executed.
     * @return The song that was liked.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun likeCurrentSong(appFunctionContext: AppFunctionContext): NowPlaying {
        val ctx = appFunctionContext.context
        if (state.title.value.isBlank()) return nowPlaying("Nothing is playing in Aalap.")
        val id = state.songId.value.toString()
        if (com.music.aalap.data.preferences.isSongLiked(ctx, id)) return nowPlaying("It's already in Liked Songs.")
        command(ctx) { it.sendCustomCommand(SessionCommand(PlaybackService.ACTION_LIKE, android.os.Bundle.EMPTY), android.os.Bundle.EMPTY) }
        return nowPlaying("Added to Liked Songs.")
    }

    private suspend fun skip(ctx: Context, forward: Boolean): NowPlaying {
        val before = state.songId.value
        command(ctx) { if (forward) it.seekToNext() else it.seekToPrevious() }
        withTimeoutOrNull(12_000) { while (state.songId.value == before) delay(300) }
        return nowPlaying()
    }

    /** The player may only be read on the main thread. */
    private suspend fun playing(): Boolean = withContext(Dispatchers.Main) { com.music.aalap.di.SongPlayer.isPlaying() }

    private suspend fun nowPlaying(message: String = ""): NowPlaying = NowPlaying(
        title = state.title.value,
        artist = state.singer.value,
        isPlaying = playing(),
        message = message.ifBlank { if (state.title.value.isBlank()) "Nothing is playing in Aalap." else "" },
    )

    /** Runs [block] on a controller of Aalap's media session (main thread), then lets go once music settles. */
    private suspend fun command(ctx: Context, block: (MediaController) -> Unit) {
        val app = ctx.applicationContext
        val controller = withContext(Dispatchers.Main) {
            MediaController.Builder(app, SessionToken(app, ComponentName(app, PlaybackService::class.java))).buildAsync().await()
        }
        try {
            withContext(Dispatchers.Main) { block(controller) }
            // Stay connected a moment: letting go at once can stop a song that's still starting.
            delay(2_000)
        } finally {
            withContext(Dispatchers.Main) { controller.release() }
        }
    }
}

/** A song in Aalap, as told to the assistant. */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class NowPlaying(
    /** The song's title (empty if nothing is playing). */
    val title: String,
    /** The song's artists. */
    val artist: String,
    /** Whether it is playing right now (false when paused). */
    val isPlaying: Boolean,
    /** Anything the user should hear, e.g. that a song couldn't be found. Empty when all went well. */
    val message: String,
)

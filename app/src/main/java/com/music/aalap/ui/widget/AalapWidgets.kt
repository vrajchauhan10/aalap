package com.music.aalap.ui.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.widget.RemoteViews
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionToken
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.google.common.util.concurrent.MoreExecutors
import com.music.aalap.MainActivity
import com.music.aalap.R
import com.music.aalap.ui.notification.PlaybackService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock

/** What the widgets show. Pushed by [PlaybackService] whenever playback changes. */
data class WidgetState(
    val title: String = "",
    val artist: String = "",
    val coverUri: String = "",
    val playing: Boolean = false,
    val liked: Boolean = false,
    val progress: Float = 0f, // 0..1
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
)

/** Large widget: cover on the left; song, progress and like / previous / play / next on the right. */
class AalapWidgetLarge : AalapWidgetBase(R.layout.widget_large, large = true)

/** Small 2×2 widget: cover and song, then previous / play / next. */
class AalapWidgetCompact : AalapWidgetBase(R.layout.widget_compact, large = false)

abstract class AalapWidgetBase(private val layout: Int, private val large: Boolean) : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        // Newly placed or system refresh: draw the last known state.
        AalapWidgets.render(context, AalapWidgets.lastState(context))
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: android.os.Bundle) {
        // Resized: switch between the square and the one-row layout.
        AalapWidgets.render(context, AalapWidgets.lastState(context))
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val action = intent.action ?: return
        if (action !in AalapWidgets.ACTIONS) return
        AalapWidgets.control(context, action, intent.getFloatExtra(AalapWidgets.EXTRA_FRACTION, -1f))
    }
}

object AalapWidgets {
    const val ACTION_PLAY_PAUSE = "com.music.aalap.widget.PLAY_PAUSE"
    const val ACTION_NEXT = "com.music.aalap.widget.NEXT"
    const val ACTION_PREV = "com.music.aalap.widget.PREV"
    const val ACTION_LIKE = "com.music.aalap.widget.LIKE"
    const val ACTION_SEEK = "com.music.aalap.widget.SEEK"
    const val EXTRA_FRACTION = "fraction"
    val ACTIONS = setOf(ACTION_PLAY_PAUSE, ACTION_NEXT, ACTION_PREV, ACTION_LIKE, ACTION_SEEK)

    private val SEEK_ZONES = listOf(
        R.id.widget_seek_0, R.id.widget_seek_1, R.id.widget_seek_2, R.id.widget_seek_3, R.id.widget_seek_4,
        R.id.widget_seek_5, R.id.widget_seek_6, R.id.widget_seek_7, R.id.widget_seek_8, R.id.widget_seek_9,
        R.id.widget_seek_10, R.id.widget_seek_11, R.id.widget_seek_12, R.id.widget_seek_13, R.id.widget_seek_14,
        R.id.widget_seek_15, R.id.widget_seek_16, R.id.widget_seek_17, R.id.widget_seek_18, R.id.widget_seek_19,
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var current = WidgetState()
    @Volatile private var artKey: String? = null
    @Volatile private var art: Bitmap? = null

    /** Current state, falling back to the last session's track (paused) after a restart. */
    fun lastState(context: Context): WidgetState {
        if (current.title.isNotBlank()) return current
        val saved = com.music.aalap.data.preferences.loadLastPlayback(context)?.first
            ?: return current.copy(playing = false)
        val liked = com.music.aalap.data.preferences.isSongLiked(context, saved.id.toString())
        val (song, positionMs) = com.music.aalap.data.preferences.loadLastPlayback(context)!!
        val dur = song.durationMs.toLong()
        return WidgetState(saved.title, saved.singer, saved.coverUri, playing = false, liked = liked,
            progress = if (dur > 0) (positionMs.toFloat() / dur).coerceIn(0f, 1f) else 0f, positionMs = positionMs, durationMs = dur)
    }

    /** Called by the playback service when anything shown on the widget changes. */
    fun update(context: Context, state: WidgetState) {
        current = state
        // Nothing loaded yet (fresh start): keep showing the last session's track.
        render(context.applicationContext, if (state.title.isBlank()) lastState(context) else state)
    }

    fun render(context: Context, state: WidgetState) {
        val app = context.applicationContext
        val manager = AppWidgetManager.getInstance(app)
        val largeIds = manager.getAppWidgetIds(ComponentName(app, AalapWidgetLarge::class.java))
        val compactIds = manager.getAppWidgetIds(ComponentName(app, AalapWidgetCompact::class.java))
        if (largeIds.isEmpty() && compactIds.isEmpty()) return
        latest = state
        scope.launch {
            // One redraw at a time, always of the newest state: parallel redraws could
            // finish out of order and leave the widget showing an older state.
            renderLock.withLock {
                val s = latest
                val rounded = artwork(app, s.coverUri)
                if (largeIds.isNotEmpty()) manager.updateAppWidget(largeIds, views(app, R.layout.widget_large, true, s, rounded))
                // The small widget fits how it's placed: square at 2×2, a one-row bar when
                // it's wide and short (e.g. placed before 3.3 as 4×1).
                compactIds.forEach { id ->
                    val o = manager.getAppWidgetOptions(id)
                    val h = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)
                    val w = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
                    val layout = if (h in 1..129 && w > h * 2) R.layout.widget_compact_wide else R.layout.widget_compact
                    manager.updateAppWidget(id, views(app, layout, false, s, rounded))
                }
            }
        }
    }

    private val renderLock = kotlinx.coroutines.sync.Mutex()

    @Volatile private var latest = WidgetState()

    private fun artwork(context: Context, uri: String): Bitmap? {
        if (uri.isBlank()) return null
        if (uri == artKey) return art
        // Large enough to stay sharp as the large widget's cover.
        val result = runCatching {
            Glide.with(context).asBitmap().load(uri).transform(CenterCrop(), RoundedCorners(44)).submit(384, 384).get()
        }.getOrNull()
        artKey = uri
        art = result
        return result
    }

    private fun views(context: Context, layout: Int, large: Boolean, s: WidgetState, bitmap: Bitmap?): RemoteViews =
        RemoteViews(context.packageName, layout).apply {
            val idle = s.title.isBlank()
            setTextViewText(R.id.widget_title, if (idle) context.getString(R.string.widget_idle_title) else s.title)
            if (bitmap != null) setImageViewBitmap(R.id.widget_art, bitmap) else setImageViewResource(R.id.widget_art, R.drawable.logo)
            setImageViewResource(R.id.widget_play, if (s.playing) R.drawable.ic_widget_pause else R.drawable.ic_widget_play)
            setContentDescription(R.id.widget_play, if (s.playing) "Pause" else "Play")

            val open = PendingIntent.getActivity(
                context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            setOnClickPendingIntent(R.id.widget_art, open)
            setOnClickPendingIntent(R.id.widget_title, open)
            // Nothing loaded yet (fresh start): play opens the app instead.
            setOnClickPendingIntent(R.id.widget_play, if (idle) open else action(context, ACTION_PLAY_PAUSE, large))
            setOnClickPendingIntent(R.id.widget_next, action(context, ACTION_NEXT, large))
            setOnClickPendingIntent(R.id.widget_prev, action(context, ACTION_PREV, large))
            setTextViewText(R.id.widget_artist, if (idle) context.getString(R.string.widget_idle_subtitle) else s.artist)
            if (large) {
                setProgressBar(R.id.widget_progress, 1000, (s.progress * 1000).toInt(), false)
                // Tap zones along the bar: each seeks to the middle of its slice.
                if (!idle) SEEK_ZONES.forEachIndexed { i, id ->
                    setOnClickPendingIntent(id, seekAction(context, (i + 0.5f) / SEEK_ZONES.size))
                }
                // The elapsed time ticks by itself while playing (no redraw needed).
                setChronometer(
                    R.id.widget_elapsed,
                    android.os.SystemClock.elapsedRealtime() - s.positionMs, null, s.playing && !idle,
                )
                setTextViewText(R.id.widget_duration, if (s.durationMs > 0) formatTime(s.durationMs) else "")
                // Times only when the song's length is known (else just the bar).
                setViewVisibility(R.id.widget_times, if (s.durationMs > 0 && !idle) android.view.View.VISIBLE else android.view.View.GONE)
                setImageViewResource(R.id.widget_like, if (s.liked) R.drawable.ic_notif_heart_filled else R.drawable.ic_notif_heart)
                // Gold when liked, white otherwise.
                setInt(R.id.widget_like, "setColorFilter", if (s.liked) 0xFFFFC94D.toInt() else 0xFFE9E9EE.toInt())
                setOnClickPendingIntent(R.id.widget_like, action(context, ACTION_LIKE, large))
            }
        }

    private fun formatTime(ms: Long): String {
        val total = ms / 1000
        return "%02d:%02d".format(total / 60, total % 60) // same style as the elapsed time
    }

    /** True when at least one Aalap widget is on the home screen. */
    fun anyPlaced(context: Context): Boolean {
        val m = AppWidgetManager.getInstance(context)
        return m.getAppWidgetIds(ComponentName(context, AalapWidgetLarge::class.java)).isNotEmpty() ||
            m.getAppWidgetIds(ComponentName(context, AalapWidgetCompact::class.java)).isNotEmpty()
    }

    private fun seekAction(context: Context, fraction: Float): PendingIntent {
        val intent = Intent(context, AalapWidgetLarge::class.java)
            .setAction(ACTION_SEEK)
            .putExtra(EXTRA_FRACTION, fraction)
        // Distinct request code per zone, or the extras would be shared.
        return PendingIntent.getBroadcast(
            context, 5_000 + (fraction * 1000).toInt(), intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private fun action(context: Context, action: String, large: Boolean): PendingIntent {
        val cls = if (large) AalapWidgetLarge::class.java else AalapWidgetCompact::class.java
        val intent = Intent(context, cls).setAction(action)
        return PendingIntent.getBroadcast(
            context, action.hashCode() + if (large) 1 else 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    /** Sends a widget button press to the media session (same path as the notification). */
    fun control(context: Context, action: String, fraction: Float = -1f) {
        val app = context.applicationContext
        val token = SessionToken(app, ComponentName(app, PlaybackService::class.java))
        val future = MediaController.Builder(app, token).buildAsync()
        future.addListener({
            val controller = runCatching { future.get() }.getOrNull() ?: return@addListener
            when (action) {
                ACTION_PLAY_PAUSE -> if (controller.isPlaying) controller.pause() else controller.play()
                ACTION_NEXT -> controller.seekToNext()
                ACTION_PREV -> controller.seekToPrevious()
                ACTION_SEEK -> if (fraction in 0f..1f && controller.duration > 0) {
                    controller.seekTo((controller.duration * fraction).toLong())
                }
                ACTION_LIKE -> controller.sendCustomCommand(
                    SessionCommand(PlaybackService.ACTION_LIKE, android.os.Bundle.EMPTY), android.os.Bundle.EMPTY,
                )
            }
            // Stay connected until the music is playing (finding the song can take several
            // seconds): disconnecting earlier let Android shut the player down mid-start.
            val handler = android.os.Handler(app.mainLooper)
            val release = Runnable { MediaController.releaseFuture(future) }
            val starts = action == ACTION_PLAY_PAUSE || action == ACTION_NEXT || action == ACTION_PREV
            if (starts && !controller.isPlaying) {
                controller.addListener(object : androidx.media3.common.Player.Listener {
                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        if (isPlaying) { handler.removeCallbacks(release); handler.postDelayed(release, 1500) }
                    }
                })
                handler.postDelayed(release, 25_000)
            } else handler.postDelayed(release, 1500)
        }, MoreExecutors.directExecutor())
    }
}

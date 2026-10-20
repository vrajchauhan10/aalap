package com.music.spotui.ui.widget

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
import com.bumptech.glide.load.resource.bitmap.CircleCrop
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.google.common.util.concurrent.MoreExecutors
import com.music.spotui.MainActivity
import com.music.spotui.R
import com.music.spotui.ui.notification.PlaybackService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** What the widgets show. Pushed by [PlaybackService] whenever playback changes. */
data class WidgetState(
    val title: String = "",
    val artist: String = "",
    val coverUri: String = "",
    val playing: Boolean = false,
    val liked: Boolean = false,
    val progress: Float = 0f, // 0..1
)

/** 4×2 widget: artwork, title, progress, like + previous / play / next. */
class AalapWidgetLarge : AalapWidgetBase(R.layout.widget_large, large = true)

/** 4×1 widget: round artwork, title, play / next. */
class AalapWidgetCompact : AalapWidgetBase(R.layout.widget_compact, large = false)

abstract class AalapWidgetBase(private val layout: Int, private val large: Boolean) : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        // Newly placed or system refresh: draw the last known state.
        AalapWidgets.render(context, AalapWidgets.lastState(context))
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val action = intent.action ?: return
        if (action !in AalapWidgets.ACTIONS) return
        AalapWidgets.control(context, action)
    }
}

object AalapWidgets {
    const val ACTION_PLAY_PAUSE = "com.music.spotui.widget.PLAY_PAUSE"
    const val ACTION_NEXT = "com.music.spotui.widget.NEXT"
    const val ACTION_PREV = "com.music.spotui.widget.PREV"
    const val ACTION_LIKE = "com.music.spotui.widget.LIKE"
    val ACTIONS = setOf(ACTION_PLAY_PAUSE, ACTION_NEXT, ACTION_PREV, ACTION_LIKE)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var current = WidgetState()
    @Volatile private var artKey: String? = null
    @Volatile private var art: Pair<Bitmap?, Bitmap?> = null to null // rounded, round

    /** Current state, falling back to the last session's track (paused) after a restart. */
    fun lastState(context: Context): WidgetState {
        if (current.title.isNotBlank()) return current
        val saved = com.music.spotui.data.preferences.loadLastPlayback(context)?.first ?: return current
        return WidgetState(saved.title, saved.singer, saved.coverUri, playing = false)
    }

    /** Called by the playback service when anything shown on the widget changes. */
    fun update(context: Context, state: WidgetState) {
        current = state
        render(context.applicationContext, state)
    }

    fun render(context: Context, state: WidgetState) {
        val app = context.applicationContext
        val manager = AppWidgetManager.getInstance(app)
        val largeIds = manager.getAppWidgetIds(ComponentName(app, AalapWidgetLarge::class.java))
        val compactIds = manager.getAppWidgetIds(ComponentName(app, AalapWidgetCompact::class.java))
        if (largeIds.isEmpty() && compactIds.isEmpty()) return
        scope.launch {
            val (rounded, round) = artwork(app, state.coverUri)
            if (largeIds.isNotEmpty()) manager.updateAppWidget(largeIds, views(app, R.layout.widget_large, true, state, rounded))
            if (compactIds.isNotEmpty()) manager.updateAppWidget(compactIds, views(app, R.layout.widget_compact, false, state, round))
        }
    }

    private fun artwork(context: Context, uri: String): Pair<Bitmap?, Bitmap?> {
        if (uri.isBlank()) return null to null
        if (uri == artKey) return art
        fun load(vararg t: com.bumptech.glide.load.Transformation<Bitmap>) = runCatching {
            Glide.with(context).asBitmap().load(uri).transform(*t).submit(256, 256).get()
        }.getOrNull()
        val result = load(CenterCrop(), RoundedCorners(36)) to load(CircleCrop())
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
            if (large) {
                setTextViewText(R.id.widget_artist, if (idle) context.getString(R.string.widget_idle_subtitle) else s.artist)
                setProgressBar(R.id.widget_progress, 1000, (s.progress * 1000).toInt(), false)
                setImageViewResource(R.id.widget_like, if (s.liked) R.drawable.ic_notif_heart_filled else R.drawable.ic_notif_heart)
                setOnClickPendingIntent(R.id.widget_prev, action(context, ACTION_PREV, large))
                setOnClickPendingIntent(R.id.widget_like, action(context, ACTION_LIKE, large))
            }
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
    fun control(context: Context, action: String) {
        val app = context.applicationContext
        val token = SessionToken(app, ComponentName(app, PlaybackService::class.java))
        val future = MediaController.Builder(app, token).buildAsync()
        future.addListener({
            val controller = runCatching { future.get() }.getOrNull() ?: return@addListener
            when (action) {
                ACTION_PLAY_PAUSE -> if (controller.isPlaying) controller.pause() else controller.play()
                ACTION_NEXT -> controller.seekToNext()
                ACTION_PREV -> controller.seekToPrevious()
                ACTION_LIKE -> controller.sendCustomCommand(
                    SessionCommand(PlaybackService.ACTION_LIKE, android.os.Bundle.EMPTY), android.os.Bundle.EMPTY,
                )
            }
            // Give the command a moment to reach the session before disconnecting.
            android.os.Handler(app.mainLooper).postDelayed({ MediaController.releaseFuture(future) }, 1500)
        }, MoreExecutors.directExecutor())
    }
}

package com.music.aalap.di

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

/**
 * "Hey Aalap" / "OK Aalap": ask for music without touching the phone.
 *
 * Off by default (Profile → Voice). When on, a listener runs while Aalap is open, and
 * keeps running in the background for as long as Aalap music is playing; it stops when
 * Aalap is closed and nothing plays. It uses the phone's own offline speech engine, so
 * nothing you say leaves the phone, and it never takes the mic from another app: during
 * a call, a WhatsApp voice note or any other recording it pauses until the mic is free.
 *
 * Heard with Aalap open: the voice card opens. In the background: the music pauses, Aalap
 * listens for the request and plays it. "Hey Aalap, play Kesariya" in one go works too.
 */
object HeyAalap {
    private const val PREFS = "hey_aalap"

    fun isEnabled(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("on", false)

    fun setEnabled(c: Context, on: Boolean) {
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("on", on).apply()
        if (on) start(c) else c.stopService(Intent(c, HeyAalapService::class.java))
    }

    /** True when this phone has an offline speech engine (needed: nothing is sent online). */
    fun supported(c: Context): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && SpeechRecognizer.isOnDeviceRecognitionAvailable(c)

    /** Aalap's screen is showing (set by MainActivity). */
    @Volatile var appVisible = false

    /** Opens the in-app voice card (set by MainActivity while it's showing). */
    @Volatile var openVoiceCard: (() -> Unit)? = null

    /** True while the listener holds the mic; [releaseMic] frees it at once (the tap mic needs it). */
    @Volatile var holdingMic = false
    @Volatile var releaseMic: (() -> Unit)? = null

    /** Starts the listener if it's switched on and allowed (call while Aalap is on screen). */
    fun start(c: Context) {
        if (!isEnabled(c) || !supported(c)) return
        if (ContextCompat.checkSelfPermission(c, android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) return
        runCatching { ContextCompat.startForegroundService(c, Intent(c, HeyAalapService::class.java)) }
            .onFailure { Log.w("HeyAalap", "start: $it") }
    }

    // ── hearing the wake words (pure, tested) ───────────────────────────────

    data class Heard(val woke: Boolean, val request: String = "")

    private fun squash(s: String) = s.replace(Regex("(.)\\1+"), "$1").replace("aa", "a")

    private fun dist(a: String, b: String): Int {
        var prev = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            val cur = IntArray(b.length + 1); cur[0] = i
            for (j in 1..b.length) cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1)
            prev = cur
        }
        return prev[b.length]
    }

    /**
     * "hey aalap", "ok aalap", "okay alap", "hey all up", "hey a lap"… anywhere in what was
     * heard; whatever follows is the request ("hey aalap play kesariya" → "play kesariya").
     */
    fun hear(text: String): Heard {
        val words = text.lowercase().replace(Regex("[^a-z ]"), " ").split(" ").filter { it.isNotBlank() }
        for (i in words.indices) {
            if (words[i] !in setOf("hey", "hay", "hi", "ok", "okay", "o.k", "hello")) continue
            // The name may be heard as one word or split in two ("all up", "a lap").
            for (n in 1..2) {
                if (i + 1 + n > words.size) break
                val name = squash(words.subList(i + 1, minOf(words.size, i + 1 + n)).joinToString(""))
                // Close to "alap" ("alap", "olap", "alup"), or a common mishearing of it that
                // starts the same way ("alarm", "all of", "all off").
                val close = dist(name, "alap") <= 1 && name.first() in "ao" ||
                    name.length in 4..5 && name.startsWith("al") && dist(name, "alap") <= 2
                if (name.length in 3..6 && close) {
                    return Heard(true, words.drop(i + 1 + n).joinToString(" "))
                }
            }
        }
        // The "hey" lost ("ke all of play kesariya", "a alarm play…"): the name followed straight
        // by "play" is still clearly a request to Aalap.
        for (i in words.indices) for (n in 1..2) {
            if (i + n >= words.size) break
            val name = squash(words.subList(i, i + n).joinToString(""))
            val isName = name.length in 3..6 && (dist(name, "alap") <= 1 && name.first() in "ao" || name.length in 4..5 && name.startsWith("al") && dist(name, "alap") <= 2)
            if (isName && words[i + n] in setOf("play", "bajao", "chalao", "put")) return Heard(true, words.drop(i + n).joinToString(" "))
        }
        // "Hey Aalap" said quickly often blurs into one word: "hal aap", "haal aap", "halaap".
        for (i in words.indices) for (n in 1..2) {
            if (i + n > words.size) break
            val joined = squash(words.subList(i, i + n).joinToString(""))
            if (joined.length in 4..7 && joined.first() == 'h' && dist(joined, "halap") <= 1) return Heard(true, words.drop(i + n).joinToString(" "))
        }
        return Heard(false)
    }
}

/** The "Hey Aalap" listener: a microphone foreground service, see [HeyAalap]. */
class HeyAalapService : Service() {
    private val tag = "HeyAalap"
    private val main = Handler(Looper.getMainLooper())
    private var rec: SpeechRecognizer? = null
    /** Waiting for the request after the wake words (in the background). */
    private var awaitingRequest = false
    private var pausedMusic = false
    private var idleSince = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        HeyAalap.releaseMic = { if (Looper.myLooper() == Looper.getMainLooper()) release() else main.post { release() } }
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Hey Aalap", NotificationManager.IMPORTANCE_MIN).apply {
            description = "Shown while Aalap listens for “Hey Aalap”"
            setShowBadge(false)
        })
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_OFF) { HeyAalap.setEnabled(this, false); stopSelf(); return START_NOT_STICKY }
        val ok = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) startForeground(ID, notification("Say “Hey Aalap” to ask for music"), ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
            else startForeground(ID, notification("Say “Hey Aalap” to ask for music"))
        }.onFailure { Log.w(tag, "can't listen now: $it") }.isSuccess
        if (!ok) { stopSelf(); return START_NOT_STICKY }
        main.removeCallbacks(loop)
        main.post(loop)
        return START_NOT_STICKY
    }

    private fun notification(text: String): Notification {
        val open = PendingIntent.getActivity(this, 0, packageManager.getLaunchIntentForPackage(packageName), PendingIntent.FLAG_IMMUTABLE)
        val off = PendingIntent.getService(this, 1, Intent(this, HeyAalapService::class.java).setAction(ACTION_OFF), PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(com.music.aalap.R.drawable.ic_mic)
            .setContentTitle("Hey Aalap")
            .setContentText(text)
            .setContentIntent(open)
            .addAction(0, "Turn off", off)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
    }

    private fun update(text: String) = getSystemService(NotificationManager::class.java).notify(ID, notification(text))

    /** Every second or so: keep listening if it's allowed, pause if not, stop when Aalap is idle. */
    private val loop: Runnable = object : Runnable {
        override fun run() {
            if (!HeyAalap.isEnabled(this@HeyAalapService)) { stopSelf(); return }
            // Aalap closed and nothing playing for a minute: nothing to ask for, so stop (battery).
            // Not at once: between songs (a request just made, the next song loading) nothing
            // plays for a moment, and this service is what keeps Aalap's network on then
            // (Android cuts a background app's network, e.g. in Battery Saver).
            if (!HeyAalap.appVisible && !SongPlayer.isPlaying() && !awaitingRequest) {
                if (idleSince == 0L) idleSince = System.currentTimeMillis()
                if (System.currentTimeMillis() - idleSince > 60_000) { stopSelf(); return }
            } else idleSince = 0L
            // The card closed by itself (song shown, or nothing heard): take it off the screen.
            if (overlay != null && VoiceUi.state.value == null) hideOverlay()
            if (rec == null) {
                when {
                    micBusy() -> {}                                        // a call or another app's recording: wait
                    VoiceUi.state.value != null -> {}                      // the voice card has the mic
                    else -> listen()
                }
            }
            main.postDelayed(this, 1_000)
        }
    }

    private fun micBusy(): Boolean = runCatching {
        val am = getSystemService(AudioManager::class.java)
        am.mode != AudioManager.MODE_NORMAL || am.activeRecordingConfigurations.isNotEmpty()
    }.getOrDefault(true)

    private fun listen() {
        val r = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) SpeechRecognizer.createOnDeviceSpeechRecognizer(this) else return
        rec = r
        HeyAalap.holdingMic = true
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {
                // While Aalap waits for the request, the card's mic and bottom edge follow your voice.
                val st = VoiceUi.state.value as? VoiceUi.State.Listening ?: return
                if (!awaitingRequest) return
                val level = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
                if (kotlin.math.abs(level - st.level) > 0.05f) VoiceUi.state.value = st.copy(level = level)
            }
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
            override fun onPartialResults(partial: Bundle?) {
                if (awaitingRequest) {
                    val words = partial?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                    (VoiceUi.state.value as? VoiceUi.State.Listening)?.let { if (words.isNotBlank()) VoiceUi.state.value = it.copy(heard = words) }
                    return
                }
                val text = partial?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                if (HeyAalap.hear(text).woke && HeyAalap.hear(text).request.split(" ").size < 2) woke("")
            }
            override fun onResults(results: Bundle?) {
                val all = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
                Log.d(tag, "heard: $all")
                release()
                if (awaitingRequest) { request(all); return }
                val heard = all.map { HeyAalap.hear(it) }.firstOrNull { it.woke } ?: run { relisten(); return }
                woke(heard.request)
            }
            override fun onError(error: Int) {
                Log.d(tag, "listen ended: error $error")
                release()
                if (awaitingRequest) {
                    awaitingRequest = false; resumeMusic()
                    VoiceUi.state.value = VoiceUi.State.Failed("Didn't catch that. Say \u201CHey Aalap\u201D again.")
                    update("Didn't catch that. Say “Hey Aalap” again")
                }
                relisten()
            }
        })
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN")
            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
        runCatching { r.startListening(i) }.onFailure { Log.w(tag, "can't listen: $it"); release() }
    }

    /** Straight back to listening, so words said between two listening rounds aren't missed. */
    private fun relisten() = main.post {
        if (rec == null && HeyAalap.isEnabled(this) && !micBusy() && VoiceUi.state.value == null) listen()
    }

    private fun release() {
        rec?.let { runCatching { it.cancel() }; runCatching { it.destroy() } }
        rec = null
        HeyAalap.holdingMic = false
    }

    /** The wake words were heard; [request] is anything said with them. */
    private fun woke(request: String) {
        release()
        Log.d(tag, "woke: '$request'")
        if (!HeyAalap.appVisible) showOverlay()
        if (request.split(" ").filter { it.isNotBlank() }.size >= 2) { play(listOf(request)); return }
        if (HeyAalap.appVisible && HeyAalap.openVoiceCard != null) { HeyAalap.openVoiceCard?.invoke(); return }
        VoiceUi.state.value = VoiceUi.State.Listening()
        // In the background: turn the music right down (not paused: a paused app loses its
        // network in the background on some phones, and the request needs it) and listen.
        if (SongPlayer.isPlaying()) { SongPlayer.exoPlayer?.volume = 0.12f; pausedMusic = true }
        awaitingRequest = true
        update("Listening… say a song, a singer or a mood")
        main.postDelayed({ if (awaitingRequest && rec == null) listen() }, 250)
    }

    private fun request(words: List<String>) {
        awaitingRequest = false
        if (words.isEmpty()) { resumeMusic(); VoiceUi.state.value = VoiceUi.State.Failed("Didn't catch that. Say \u201CHey Aalap\u201D again."); return }
        play(words)
    }

    private fun play(words: List<String>) {
        // If nothing is found, the music that was paused for the request comes back.
        // Back to full volume as the request goes off (the new song starts a moment later).
        if (pausedMusic) SongPlayer.exoPlayer?.volume = 1f
        pausedMusic = false
        main.postDelayed({
            if (VoicePlay.last.endsWith("nothing found")) update("Couldn't find \u201C${words.first()}\u201D. Try the song and the singer")
        }, 6_000)
        val state = dagger.hilt.android.EntryPointAccessors.fromApplication(applicationContext, StateEntryPoint::class.java).currentSongState()
        // The card (in Aalap, or over other apps) shows Aalap thinking and then the song.
        VoicePlay.handle(this, state, VoicePlay.Request(words.first(), alternatives = words.drop(1)), fromApp = HeyAalap.appVisible || overlay != null)
        update("Asked for “${words.first()}”")
        main.postDelayed({ update("Say “Hey Aalap” to ask for music") }, 10_000)
    }

    private fun resumeMusic() { if (pausedMusic) { pausedMusic = false; SongPlayer.exoPlayer?.volume = 1f } }

    // ── the card over other apps ────────────────────────────────────────────

    private var overlay: androidx.compose.ui.platform.ComposeView? = null
    private var overlayOwner: OverlayOwner? = null

    /**
     * The voice card over whatever app is on screen (needs "Display over other apps",
     * which Profile → Voice asks for). Without it, the notification shows what's happening.
     */
    private fun showOverlay() {
        if (overlay != null || !android.provider.Settings.canDrawOverlays(this)) return
        runCatching {
            val owner = OverlayOwner().also { it.start() }
            val view = androidx.compose.ui.platform.ComposeView(this).apply {
                setViewTreeLifecycleOwner(owner)
                setViewTreeSavedStateRegistryOwner(owner)
                setContent {
                    com.music.aalap.ui.theme.AalapTheme {
                        com.music.aalap.ui.components.VoiceOverlay(onCancel = { cancelCard() }, onRetry = { cancelCard() })
                    }
                }
            }
            val lp = android.view.WindowManager.LayoutParams(
                android.view.WindowManager.LayoutParams.MATCH_PARENT, android.view.WindowManager.LayoutParams.MATCH_PARENT,
                android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or android.view.WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                android.graphics.PixelFormat.TRANSLUCENT,
            )
            getSystemService(android.view.WindowManager::class.java).addView(view, lp)
            overlay = view; overlayOwner = owner
        }.onFailure { Log.w(tag, "overlay: $it") }
    }

    private fun hideOverlay() {
        overlay?.let { v -> runCatching { getSystemService(android.view.WindowManager::class.java).removeView(v) } }
        overlayOwner?.stop()
        overlay = null; overlayOwner = null
    }

    /** Tapped outside the card: stop waiting for a request, music back to full. */
    private fun cancelCard() {
        awaitingRequest = false
        release()
        resumeMusic()
        VoiceUi.state.value = null
    }

    /** A lifecycle for Compose in a window that has no activity. */
    private class OverlayOwner : androidx.lifecycle.LifecycleOwner, androidx.savedstate.SavedStateRegistryOwner {
        private val registry = androidx.lifecycle.LifecycleRegistry(this)
        private val saved = androidx.savedstate.SavedStateRegistryController.create(this)
        override val lifecycle: androidx.lifecycle.Lifecycle get() = registry
        override val savedStateRegistry: androidx.savedstate.SavedStateRegistry get() = saved.savedStateRegistry
        fun start() { saved.performRestore(null); registry.currentState = androidx.lifecycle.Lifecycle.State.RESUMED }
        fun stop() { registry.currentState = androidx.lifecycle.Lifecycle.State.DESTROYED }
    }

    override fun onDestroy() {
        hideOverlay()
        main.removeCallbacksAndMessages(null)
        release()
        HeyAalap.releaseMic = null
        resumeMusic()
        super.onDestroy()
    }

    @dagger.hilt.EntryPoint
    @dagger.hilt.InstallIn(dagger.hilt.components.SingletonComponent::class)
    interface StateEntryPoint { fun currentSongState(): CurrentSongState }

    companion object {
        private const val CHANNEL = "hey_aalap"
        private const val ID = 4207
        private const val ACTION_OFF = "com.music.aalap.HEY_AALAP_OFF"
    }
}

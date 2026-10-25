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
                if (name.length in 3..6 && dist(name, "alap") <= 1 && name.first() in "ao") {
                    return Heard(true, words.drop(i + 1 + n).joinToString(" "))
                }
            }
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
            // Aalap closed and nothing playing: nothing to ask for, so stop (battery).
            if (!HeyAalap.appVisible && !SongPlayer.isPlaying() && !awaitingRequest) { stopSelf(); return }
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
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
            override fun onPartialResults(partial: Bundle?) {
                if (awaitingRequest) return
                val text = partial?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                if (HeyAalap.hear(text).woke && HeyAalap.hear(text).request.split(" ").size < 2) woke("")
            }
            override fun onResults(results: Bundle?) {
                val all = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
                release()
                if (awaitingRequest) { request(all); return }
                val heard = all.map { HeyAalap.hear(it) }.firstOrNull { it.woke } ?: return
                woke(heard.request)
            }
            override fun onError(error: Int) {
                release()
                if (awaitingRequest) { awaitingRequest = false; resumeMusic(); update("Didn't catch that. Say “Hey Aalap” again") }
            }
        })
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN")
            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
        runCatching { r.startListening(i) }.onFailure { release() }
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
        if (request.split(" ").filter { it.isNotBlank() }.size >= 2) { play(listOf(request)); return }
        if (HeyAalap.appVisible && HeyAalap.openVoiceCard != null) { HeyAalap.openVoiceCard?.invoke(); return }
        // In the background: quiet the music and listen for the request.
        if (SongPlayer.isPlaying()) { SongPlayer.pause(); pausedMusic = true }
        awaitingRequest = true
        update("Listening… say a song, a singer or a mood")
        main.postDelayed({ if (awaitingRequest && rec == null) listen() }, 250)
    }

    private fun request(words: List<String>) {
        awaitingRequest = false
        if (words.isEmpty()) { resumeMusic(); return }
        play(words)
    }

    private fun play(words: List<String>) {
        pausedMusic = false
        val state = dagger.hilt.android.EntryPointAccessors.fromApplication(applicationContext, StateEntryPoint::class.java).currentSongState()
        VoicePlay.handle(this, state, VoicePlay.Request(words.first(), alternatives = words.drop(1)), fromApp = HeyAalap.appVisible)
        update("Asked for “${words.first()}”")
        main.postDelayed({ update("Say “Hey Aalap” to ask for music") }, 6_000)
    }

    private fun resumeMusic() { if (pausedMusic) { pausedMusic = false; SongPlayer.play() } }

    override fun onDestroy() {
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

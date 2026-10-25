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
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

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

    const val ACTION_ASK = "com.music.aalap.ASK_NOW"

    /**
     * The bar comes up and Aalap listens for one request right now, over any app: from a
     * held volume key or the side-key "Ask Aalap" (works with "Hey Aalap" off too).
     * Call while something of Aalap's is on screen (see [AskAalapActivity]).
     */
    fun ask(c: Context) {
        if (!supported(c) || ContextCompat.checkSelfPermission(c, android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            // Without the mic or the offline engine: Aalap's own voice card asks for what's missing.
            runCatching { c.startActivity(Intent(c, com.music.aalap.MainActivity::class.java).setAction(VoiceAsk.ACTION).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            return
        }
        runCatching { ContextCompat.startForegroundService(c, Intent(c, HeyAalapService::class.java).setAction(ACTION_ASK)) }
            .onFailure { Log.w("HeyAalap", "ask: $it") }
    }

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

    /**
     * The last guess for a short burst of speech that the engine then dropped (it often gives
     * no final words for a name it doesn't know): "okay al", "hey ala", "ki hala", "hey all".
     * Only a greeting and the start of the name, and nothing more, counts.
     */
    fun heardShort(partial: String): Boolean {
        val words = partial.lowercase().replace(Regex("[^a-z ]"), " ").split(" ").filter { it.isNotBlank() }
        if (words.size !in 2..3) return false
        val greet = words[0] in setOf("hey", "hay", "hi", "ok", "okay", "ke", "ki", "k", "jay", "a")
        val name = squash(words.drop(1).joinToString(""))
        return greet && name.length in 2..6 && (name.startsWith("al") || name.startsWith("hal") || name.startsWith("ol"))
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
    private var listenedAt = 0L
    private var foreground = false
    private var wakeGuess = ""
    /** The request's own sentence has begun (after the wake words). */
    private var requestSpoken = false
    private var retries = 0

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
        // Already listening (started once while Aalap was on screen): Android refuses a second
        // microphone start from the background, and that must not stop the one that's running.
        if (foreground) { main.removeCallbacks(loop); main.post(loop); if (intent?.action == HeyAalap.ACTION_ASK) askNow(); return START_NOT_STICKY }
        val ok = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) startForeground(ID, notification("Say “Hey Aalap” to ask for music"), ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
            else startForeground(ID, notification("Say “Hey Aalap” to ask for music"))
        }.onFailure { Log.w(tag, "can't listen now: $it") }.isSuccess
        if (!ok) { stopSelf(); return START_NOT_STICKY }
        foreground = true
        main.removeCallbacks(loop)
        main.post(loop)
        if (intent?.action == HeyAalap.ACTION_ASK) askNow()
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
            // "Hey Aalap" off: only here for one request (a held volume key); done once it's answered.
            if (!HeyAalap.isEnabled(this@HeyAalapService) && !awaitingRequest && VoiceUi.state.value == null) { stopSelf(); return }
            // Aalap closed and nothing playing for a minute: nothing to ask for, so stop (battery).
            // Not at once: between songs (a request just made, the next song loading) nothing
            // plays for a moment, and this service is what keeps Aalap's network on then
            // (Android cuts a background app's network, e.g. in Battery Saver).
            if (!HeyAalap.appVisible && !SongPlayer.isPlaying() && !awaitingRequest) {
                if (idleSince == 0L) idleSince = System.currentTimeMillis()
                if (System.currentTimeMillis() - idleSince > 60_000) { stopSelf(); return }
            } else idleSince = 0L
            // The bar was closed in Aalap while waiting for the request: stop waiting.
            if (awaitingRequest && VoiceUi.state.value == null) { main.removeCallbacks(settle); main.removeCallbacks(noRequest); awaitingRequest = false; resumeMusic() }
            // The card closed by itself (song shown, or nothing heard): take it off the screen.
            if (overlay != null && VoiceUi.state.value == null) hideOverlay()
            // A long listening session gets stale on some phones: start a fresh one every minute.
            if (rec != null && !awaitingRequest && VoiceUi.state.value == null && System.currentTimeMillis() - listenedAt > 60_000) {
                Log.d(tag, "fresh listening session"); release()
            }
            if (rec == null) {
                when {
                    micBusy() -> {}                                        // a call or another app's recording: wait
                    VoiceUi.state.value != null -> {}                      // the voice card has the mic
                    !HeyAalap.isEnabled(this@HeyAalapService) -> {}        // no wake words to listen for
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
        Log.d(tag, "listening (${if (awaitingRequest) "for the request" else "for the wake words"})")
        val r = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) SpeechRecognizer.createOnDeviceSpeechRecognizer(this) else return
        rec = r
        listenedAt = System.currentTimeMillis()
        HeyAalap.holdingMic = true
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() { if (awaitingRequest) requestSpoken = true }
            override fun onRmsChanged(rmsdB: Float) {
                // While Aalap waits for the request, the card's mic and bottom edge follow your voice.
                val st = VoiceUi.state.value as? VoiceUi.State.Listening ?: return
                if (!awaitingRequest) return
                val level = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
                if (kotlin.math.abs(level - st.level) > 0.05f) VoiceUi.state.value = st.copy(level = level)
            }
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {
                Log.d(tag, "end of speech")
                // A short "hey aal…" whose final words the engine dropped: still the wake words.
                val guess = wakeGuess; wakeGuess = ""
                if (!awaitingRequest && HeyAalap.heardShort(guess)) main.postDelayed({ if (!awaitingRequest && VoiceUi.state.value == null) woke("") }, 400)
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
            override fun onPartialResults(partial: Bundle?) {
                if (awaitingRequest) {
                    val said = partial?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                    // Still the wake words' own sentence: only what follows them ("hey aalap play
                    // kesariya" said in one go) is the request.
                    val words = if (requestSpoken) said else HeyAalap.hear(said).let { if (it.woke) it.request else "" }
                    if (words.isBlank()) return
                    (VoiceUi.state.value as? VoiceUi.State.Listening)?.let { if (words.isNotBlank()) VoiceUi.state.value = it.copy(heard = words) }
                    // The engine fed from Aalap's own mic stream doesn't always end by itself:
                    // once the words stop changing for a moment, that's the request.
                    if (words.isNotBlank() && words != lastPartial) {
                        lastPartial = words
                        main.removeCallbacks(settle); main.postDelayed(settle, 1_300)
                    }
                    return
                }
                val text = partial?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                if (HeyAalap.hear(text).woke && HeyAalap.hear(text).request.split(" ").size < 2) woke("")
                else wakeGuess = text
            }
            override fun onResults(results: Bundle?) {
                val all = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
                Log.d(tag, "heard: $all")
                release()
                if (awaitingRequest) { request(all.ifEmpty { listOfNotNull(lastPartial.takeIf { it.isNotBlank() }) }); return }
                val heard = all.map { HeyAalap.hear(it) }.firstOrNull { it.woke } ?: run { relisten(); return }
                woke(heard.request)
            }
            // Fed from Aalap's own mic stream the engine runs as one long session and hands
            // over each stretch of speech here (the final words, not just the partial guesses).
            override fun onSegmentResults(segment: Bundle) {
                val all = segment.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty().filter { it.isNotBlank() }
                if (all.isEmpty()) return
                Log.d(tag, "segment: $all")
                if (awaitingRequest) {
                    val req = if (requestSpoken) all else all.map { HeyAalap.hear(it) }.filter { it.woke && it.request.isNotBlank() }.map { it.request }
                    if (req.isNotEmpty()) { main.removeCallbacks(settle); release(); request(req) }
                    return
                }
                val heard = all.map { HeyAalap.hear(it) }.firstOrNull { it.woke } ?: return
                woke(heard.request)
            }
            override fun onEndOfSegmentedSession() {
                Log.d(tag, "session ended")
                release()
                if (awaitingRequest && lastPartial.isNotBlank()) { request(listOf(lastPartial)); return }
                relisten()
            }
            override fun onError(error: Int) {
                Log.d(tag, "listen ended: error $error")
                release()
                // The engine let go of the last session as this one began: just start again.
                if (awaitingRequest && lastPartial.isBlank() && error == SpeechRecognizer.ERROR_SERVER_DISCONNECTED && retries++ < 2) {
                    main.postDelayed({ if (awaitingRequest && rec == null) listen() }, 300); return
                }
                if (awaitingRequest && lastPartial.isNotBlank()) { request(listOf(lastPartial)); return }
                if (awaitingRequest) {
                    main.removeCallbacks(noRequest)
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
        // "Aalap" isn't an English word: tell the engine to expect it.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            i.putStringArrayListExtra(RecognizerIntent.EXTRA_BIASING_STRINGS, arrayListOf("hey aalap", "aalap", "ok aalap", "hey alap"))
        // Aalap records the mic itself with the phone's echo canceller on, and hands the
        // cleaned sound to the speech engine: the song playing from the phone's own speaker is
        // taken out, so "Hey Aalap" is heard over the music (the engine alone heard the lyrics).
        // A request in a session of its own (asked by a key) uses the phone's mic directly: the
        // first sentence of a fresh session on Aalap's own stream came back empty, and the music
        // is turned down then anyway.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !awaitingRequest) cleanedMic()?.let { fd ->
            i.putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE, fd)
                .putExtra(RecognizerIntent.EXTRA_SEGMENTED_SESSION, RecognizerIntent.EXTRA_AUDIO_SOURCE)
                .putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_CHANNEL_COUNT, 1)
                .putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_ENCODING, android.media.AudioFormat.ENCODING_PCM_16BIT)
                .putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_SAMPLING_RATE, RATE)
        }
        runCatching { r.startListening(i) }.onFailure { Log.w(tag, "can't listen: $it"); release() }
    }

    private var record: android.media.AudioRecord? = null
    @Volatile private var pumping = false
    /** Where the mic's sound goes now: the current speech session's pipe (swapped per session). */
    @Volatile private var sink: java.io.OutputStream? = null

    /**
     * The mic, echo-cancelled and noise-suppressed, as a stream for a new speech session (null
     * if it can't). The recording itself keeps running across sessions when it can: a fresh one
     * takes a second to settle and missed the start of what was said.
     */
    @android.annotation.SuppressLint("MissingPermission")
    private fun cleanedMic(): android.os.ParcelFileDescriptor? = runCatching {
        val (read, write) = android.os.ParcelFileDescriptor.createPipe()
        val old = sink
        sink = android.os.ParcelFileDescriptor.AutoCloseOutputStream(write)
        runCatching { old?.close() }
        if (record == null) {
            val min = android.media.AudioRecord.getMinBufferSize(RATE, android.media.AudioFormat.CHANNEL_IN_MONO, android.media.AudioFormat.ENCODING_PCM_16BIT)
            val rec = android.media.AudioRecord(SOURCE, RATE, android.media.AudioFormat.CHANNEL_IN_MONO, android.media.AudioFormat.ENCODING_PCM_16BIT, maxOf(min, RATE))
            if (rec.state != android.media.AudioRecord.STATE_INITIALIZED) { rec.release(); closeSink(); read.close(); return null }
            if (android.media.audiofx.AcousticEchoCanceler.isAvailable()) android.media.audiofx.AcousticEchoCanceler.create(rec.audioSessionId)?.enabled = true
            if (android.media.audiofx.NoiseSuppressor.isAvailable()) android.media.audiofx.NoiseSuppressor.create(rec.audioSessionId)?.enabled = true
            rec.startRecording()
            record = rec; pumping = true
            Thread {
                val buf = ByteArray(3200)
                while (pumping) {
                    val n = rec.read(buf, 0, buf.size)
                    if (n <= 0) { Log.d(tag, "mic read $n"); break }
                    val out = sink ?: continue
                    runCatching { out.write(buf, 0, n) }.onFailure {
                        // The engine let go of its session: it then just waits, so listen afresh.
                        if (sink === out) { sink = null; main.post { if (this@HeyAalapService.rec != null && record === rec) { Log.d(tag, "engine let go, listening again"); release(keepMic = true); if (awaitingRequest) listen() else relisten() } } }
                    }
                }
                // The mic itself stopped (taken by a call, say): start over once it's free.
                main.post { if (record === rec) { Log.d(tag, "mic stream ended, listening again"); release(); if (awaitingRequest) listen() else relisten() } }
            }.apply { isDaemon = true; name = "HeyAalapMic" }.start()
        }
        read
    }.onFailure { Log.w(tag, "cleaned mic: $it") }.getOrNull()

    private fun closeSink() { val s = sink; sink = null; runCatching { s?.close() } }

    private fun stopMic() {
        pumping = false
        closeSink()
        record?.let { r -> runCatching { r.stop() }; runCatching { r.release() } }
        record = null
    }

    /** Straight back to listening, so words said between two listening rounds aren't missed. */
    private fun relisten() = main.post {
        if (rec == null && HeyAalap.isEnabled(this) && !micBusy() && VoiceUi.state.value == null) listen()
    }

    /** Ends the speech session; [keepMic] keeps the recording going for the next one. */
    private fun release(keepMic: Boolean = false) {
        rec?.let { runCatching { it.cancel() }; runCatching { it.destroy() } }
        rec = null
        if (keepMic) closeSink() else stopMic()
        HeyAalap.holdingMic = false
    }

    /** Asked by a key: as if "Hey Aalap" was just said. */
    private fun askNow() {
        if (awaitingRequest) return
        if (micBusy() && rec == null) { VoiceUi.state.value = VoiceUi.State.Failed("The mic is busy (a call or a recording)"); showOverlay(); return }
        // A clean listening session (one already running may be partway through a stretch of
        // the song playing, which would swallow the start of the request), on the mic that's
        // already recording and settled.
        // The engine needs a moment between two sessions (one started at once was dropped):
        // woke() starts the new one shortly.
        release()
        woke("")
        requestSpoken = true  // no wake words to skip: what's said next is the request
    }

    /** The wake words were heard; [request] is anything said with them. */
    private fun woke(request: String) {
        Log.d(tag, "woke: '$request'")
        // Keep the same listening session for the request (a fresh one is deaf for its first
        // seconds on some phones, and the request was lost): only from the next sentence on.
        val keep = record != null && rec != null && request.split(" ").count { it.isNotBlank() } < 2
        if (!keep) release()
        requestSpoken = false
        retries = 0
        // Its own window over everything: over other apps, and in Aalap over the player too
        // (the player is a window of its own above Aalap's screen, which hid the bar).
        showOverlay()
        if (request.split(" ").filter { it.isNotBlank() }.size >= 2) { play(listOf(request)); return }
        // In Aalap, the same bar shows (it follows VoiceUi); the app's own listener is only
        // the fallback where Aalap can't feed the speech engine itself.
        if (!keep && Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU && HeyAalap.appVisible && HeyAalap.openVoiceCard != null) { HeyAalap.openVoiceCard?.invoke(); return }
        VoiceUi.state.value = VoiceUi.State.Listening()
        // In the background: turn the music right down (not paused: a paused app loses its
        // network in the background on some phones, and the request needs it) and listen.
        if (SongPlayer.isPlaying()) { SongPlayer.exoPlayer?.volume = 0.12f; pausedMusic = true }
        awaitingRequest = true
        lastPartial = ""
        main.removeCallbacks(noRequest); main.postDelayed(noRequest, 8_000)
        update("Listening… say a song, a singer or a mood")
        if (!keep) main.postDelayed({ if (awaitingRequest && rec == null) listen() }, 250)
    }

    private var lastPartial = ""

    /** The request's words have settled: take them. */
    private val settle = Runnable {
        if (!awaitingRequest || lastPartial.isBlank()) return@Runnable
        Log.d(tag, "heard (settled): $lastPartial")
        val words = listOf(lastPartial)
        release(); request(words)
    }

    /** Nothing said after the wake words. */
    private val noRequest = Runnable {
        if (!awaitingRequest || lastPartial.isNotBlank() || VoiceUi.state.value !is VoiceUi.State.Listening) return@Runnable
        Log.d(tag, "no request")
        release(); awaitingRequest = false; resumeMusic()
        VoiceUi.state.value = VoiceUi.State.Failed("Didn't catch that. Say \u201CHey Aalap\u201D again.")
        relisten()
    }

    private fun request(words: List<String>) {
        main.removeCallbacks(settle); main.removeCallbacks(noRequest)
        lastPartial = ""
        // "Hey Aalap" said again (or with the request after it): take what follows, or keep listening.
        val again = words.firstOrNull()?.let { HeyAalap.hear(it) }
        if (again != null && again.woke) {
            if (again.request.split(" ").count { it.isNotBlank() } >= 2) { awaitingRequest = false; play(listOf(again.request)); return }
            main.postDelayed({ if (awaitingRequest && rec == null) listen() }, 150)
            main.postDelayed(noRequest, 8_000)
            return
        }
        awaitingRequest = false
        if (words.isEmpty()) { resumeMusic(); VoiceUi.state.value = VoiceUi.State.Failed("Didn't catch that. Say \u201CHey Aalap\u201D again."); return }
        play(words)
    }

    private var controller: com.google.common.util.concurrent.ListenableFuture<androidx.media3.session.MediaController>? = null

    private fun play(words: List<String>) {
        // Aalap may not be open at all (asked by a key): connect to the player service the way
        // the app's screen does, so the song gets its notification and lock-screen controls.
        if (controller == null) controller = runCatching {
            androidx.media3.session.MediaController.Builder(this,
                androidx.media3.session.SessionToken(this, android.content.ComponentName(this, com.music.aalap.ui.notification.PlaybackService::class.java))).buildAsync()
        }.getOrNull()
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
    private var overlayScope: kotlinx.coroutines.CoroutineScope? = null

    /** Frames every 16 ms from a plain handler (see [showOverlay]). */
    private class HandlerFrameClock(private val handler: Handler) : androidx.compose.runtime.MonotonicFrameClock {
        override suspend fun <R> withFrameNanos(onFrame: (Long) -> R): R = kotlinx.coroutines.suspendCancellableCoroutine { c ->
            val tick = Runnable { c.resumeWith(runCatching { onFrame(System.nanoTime()) }) }
            handler.postDelayed(tick, 16)
            c.invokeOnCancellation { handler.removeCallbacks(tick) }
        }
    }

    /**
     * The voice card over whatever app is on screen (needs "Display over other apps",
     * which Profile → Voice asks for). Without it, the notification shows what's happening.
     */
    private fun showOverlay() {
        if (overlay != null || !android.provider.Settings.canDrawOverlays(this)) return
        runCatching {
            val owner = OverlayOwner().also { it.start() }
            // Its own frame clock: Samsung stops the usual frame ticks for an app in the
            // background, and Compose (the bar, its animations) then never draws past frame one.
            val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main + HandlerFrameClock(main))
            val recomposer = androidx.compose.runtime.Recomposer(scope.coroutineContext)
            scope.launch { recomposer.runRecomposeAndApplyChanges() }
            overlayScope = scope
            val view = androidx.compose.ui.platform.ComposeView(this).apply {
                setParentCompositionContext(recomposer)
                setViewTreeLifecycleOwner(owner)
                setViewTreeSavedStateRegistryOwner(owner)
                // No AalapTheme here: it styles an activity's window, and this window has none.
                // The bar's colours come from Aalap's palette directly.
                setContent { com.music.aalap.ui.components.VoiceOverlay(onCancel = { cancelCard() }, onRetry = { cancelCard() }) }
            }
            val lp = android.view.WindowManager.LayoutParams(
                android.view.WindowManager.LayoutParams.MATCH_PARENT, android.view.WindowManager.LayoutParams.MATCH_PARENT,
                android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or android.view.WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                android.graphics.PixelFormat.TRANSLUCENT,
            )
            getSystemService(android.view.WindowManager::class.java).addView(view, lp)
            overlay = view; overlayOwner = owner
            VoiceUi.overApps.value = true
        }.onFailure { Log.w(tag, "overlay: $it") }
    }

    private fun hideOverlay() {
        overlay?.let { v -> runCatching { getSystemService(android.view.WindowManager::class.java).removeView(v) } }
        overlayOwner?.stop()
        overlayScope?.cancel(); overlayScope = null
        VoiceUi.overApps.value = false
        overlay = null; overlayOwner = null
    }

    /** Tapped outside the card: stop waiting for a request, music back to full. */
    private fun cancelCard() {
        main.removeCallbacks(settle); main.removeCallbacks(noRequest)
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
        controller?.let { androidx.media3.session.MediaController.releaseFuture(it) }
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
        private const val RATE = 16_000
        /** The speech source with the echo canceller attached (the call source on Samsung was all but silent). */
        private val SOURCE = android.media.MediaRecorder.AudioSource.VOICE_RECOGNITION
    }
}

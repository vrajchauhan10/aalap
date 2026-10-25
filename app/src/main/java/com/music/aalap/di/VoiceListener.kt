package com.music.aalap.di

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/**
 * The mic button's listener, inside Aalap (no Google pop-up): it listens once, shows
 * what it hears in the voice overlay and hands the words to [onWords].
 *
 * It never takes the mic from another app: during a call, a WhatsApp voice note or
 * any other recording it doesn't start. The mic is released as soon as you stop
 * speaking, on cancel, after 12 seconds at most, and whenever Aalap leaves the screen.
 * Music playing in Aalap pauses while it listens and comes back if nothing was asked.
 */
class VoiceListener(private val context: Context, private val onWords: (List<String>) -> Unit) {
    private var rec: SpeechRecognizer? = null
    private var pausedMusic = false
    private val main = Handler(Looper.getMainLooper())
    private val timeout = Runnable { if (rec != null) { stop(); fail("Didn't catch that. Tap the mic and try again.") } }

    /** True when another app is using the mic (a call, a voice note, a recording). */
    fun micBusy(): Boolean = runCatching {
        val am = context.getSystemService(AudioManager::class.java)
        am.mode == AudioManager.MODE_IN_CALL || am.mode == AudioManager.MODE_IN_COMMUNICATION ||
            am.mode == AudioManager.MODE_RINGTONE || am.activeRecordingConfigurations.isNotEmpty()
    }.getOrDefault(false)

    val available: Boolean get() = SpeechRecognizer.isRecognitionAvailable(context)

    val listening: Boolean get() = rec != null

    fun start() {
        if (rec != null) return
        // "Hey Aalap" may be holding the mic: that's Aalap itself, so it steps aside first.
        if (HeyAalap.holdingMic) {
            HeyAalap.releaseMic?.invoke()
            VoiceUi.state.value = VoiceUi.State.Listening()
            main.postDelayed({ startNow() }, 350)
            return
        }
        startNow()
    }

    private fun startNow() {
        if (rec != null) return
        if (micBusy()) { fail("The mic is in use by another app (a call or a voice note), so Aalap won't take it. Try again after."); return }
        if (SongPlayer.isPlaying()) { SongPlayer.pause(); pausedMusic = true }
        VoiceUi.state.value = VoiceUi.State.Listening()
        val r = SpeechRecognizer.createSpeechRecognizer(context)
        rec = r
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {
                val s = VoiceUi.state.value as? VoiceUi.State.Listening ?: return
                val level = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
                if (kotlin.math.abs(level - s.level) > 0.05f) VoiceUi.state.value = s.copy(level = level)
            }
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(partial: Bundle?) {
                val words = partial?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                val s = VoiceUi.state.value as? VoiceUi.State.Listening ?: return
                if (words.isNotBlank()) VoiceUi.state.value = s.copy(heard = words)
            }
            override fun onResults(results: Bundle?) {
                val words = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty().filter { it.isNotBlank() }
                stop()
                if (words.isEmpty()) fail("Didn't catch that. Tap the mic and try again.")
                else { pausedMusic = false; onWords(words) }
            }
            override fun onError(error: Int) {
                stop()
                fail(when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Didn't catch that. Tap the mic and try again."
                    SpeechRecognizer.ERROR_AUDIO, SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "The mic is busy in another app. Try again after."
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Aalap needs the microphone permission to listen."
                    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "No internet for voice right now."
                    else -> "Couldn't listen just now. Try again."
                })
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN")
            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            .putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
        runCatching { r.startListening(intent) }.onFailure { stop(); fail("Couldn't listen just now. Try again.") }
        main.postDelayed(timeout, 12_000)
    }

    /** Stops listening and frees the mic. */
    fun stop() {
        main.removeCallbacks(timeout)
        rec?.let { runCatching { it.cancel() }; runCatching { it.destroy() } }
        rec = null
    }

    /** Cancelled (tapped away, left the app): frees the mic and brings the music back. */
    fun cancel() {
        val was = rec != null
        stop()
        if (was) { VoiceUi.state.value = null; resumeMusic() }
    }

    private fun fail(message: String) {
        VoiceUi.state.value = VoiceUi.State.Failed(message)
        resumeMusic()
    }

    private fun resumeMusic() {
        if (pausedMusic) { pausedMusic = false; SongPlayer.play() }
    }
}

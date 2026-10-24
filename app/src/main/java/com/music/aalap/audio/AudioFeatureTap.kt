package com.music.aalap.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Listens to the audio as it plays (passing it through unchanged) and measures the
 * song's tempo and energy ([AudioFeatures]) from about 40 seconds of it, starting 10
 * seconds in (past quiet intros). The result goes to [AudioAnalysis] for the song
 * that's playing. 16-bit PCM only; anything else passes by untouched.
 */
@UnstableApi
class AudioFeatureTap : BaseAudioProcessor() {
    private var sampleRate = 0
    private var channels = 0
    private var features: AudioFeatures? = null
    private var generation = -1
    private var skipFrames = 0L
    private var done = false
    private var mono = FloatArray(4096)

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) return AudioProcessor.AudioFormat.NOT_SET
        sampleRate = inputAudioFormat.sampleRate
        channels = inputAudioFormat.channelCount
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return
        if (AudioAnalysis.enabled && sampleRate > 0 && channels > 0) listen(inputBuffer.duplicate().order(ByteOrder.LITTLE_ENDIAN))
        val out = replaceOutputBuffer(remaining)
        out.put(inputBuffer)
        out.flip()
    }

    private fun listen(buf: ByteBuffer) {
        val gen = AudioAnalysis.generation
        if (gen != generation) {
            // A new song: start over.
            generation = gen
            features = AudioFeatures(sampleRate)
            skipFrames = sampleRate * 10L
            done = false
        }
        if (done) return
        val frames = buf.remaining() / (2 * channels)
        if (mono.size < frames) mono = FloatArray(frames)
        var n = 0
        for (f in 0 until frames) {
            var sum = 0
            for (c in 0 until channels) sum += buf.short.toInt()
            if (skipFrames > 0) { skipFrames--; continue }
            mono[n++] = sum / (channels * 32768f)
        }
        val a = features ?: return
        a.add(mono, n)
        if (a.seconds >= 40) {
            done = true
            a.result()?.let { AudioAnalysis.measured(gen, it) }
        }
    }

    override fun onFlush() { /* a seek: keep listening to the same song */ }

    override fun onReset() { features = null; generation = -1; done = false }
}

/**
 * Which song the audio belongs to, and where its measurements go. The player's audio
 * thread reports through [measured]; the song changes through [newSong].
 */
object AudioAnalysis {
    /** Off switch (also off while nothing needs it). */
    @Volatile var enabled = true
    @Volatile var generation = 0
        private set
    @Volatile private var song: com.music.aalap.data.entity.SongsModel? = null
    /** Where results go (set by the engine): song, result. */
    @Volatile var onMeasured: ((com.music.aalap.data.entity.SongsModel, AudioFeatures.Result) -> Unit)? = null

    fun newSong(s: com.music.aalap.data.entity.SongsModel) {
        if (song?.id == s.id) return
        song = s
        generation++
    }

    fun measured(gen: Int, r: AudioFeatures.Result) {
        if (gen != generation) return
        val s = song ?: return
        onMeasured?.invoke(s, r)
    }
}

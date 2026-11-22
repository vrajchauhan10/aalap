package com.music.aalap.di

import android.content.Context
import android.util.Log
import com.music.aalap.data.entity.SongsModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Measures the next song's tempo and energy before it plays, from the start of it Aalap has
 * already saved for a quick start (no extra download for most songs). Then, if it would jump
 * in pace from the song playing now, the engine's picks ahead are chosen again.
 *
 * Before this, a song's tempo was known only after it had played once with the app open, so
 * the pace rule couldn't see most new songs. Work: one short decode (~25 s of audio) per new
 * song, once ever per song, about 20 s into the song before it; nothing for songs measured
 * before, nothing offline.
 */
object TempoAhead {
    private const val TAG = "TempoAhead"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    @Volatile private var scheduledFor: Int? = null

    /** [current] just started; whatever is queued after it 20 s later is checked. */
    fun schedule(context: Context, current: SongsModel, @Suppress("UNUSED_PARAMETER") ignored: SongsModel?, state: CurrentSongState) {
        if (scheduledFor == current.id && job?.isActive == true) return
        job?.cancel()
        scheduledFor = current.id
        if (com.music.aalap.data.preferences.OfflineState.isOffline) return
        val app = context.applicationContext
        job = scope.launch {
            // Let the song that just started settle first (its own loading comes first); by then
            // the engine has also queued what follows it.
            delay(20_000)
            if (state.songId.value != current.id) return@launch
            val next = kotlinx.coroutines.withContext(Dispatchers.Main) {
                val q = state.queue.value
                q.getOrNull(q.indexOfFirst { it.id == current.id } + 1)
            } ?: return@launch
            if (next.url.isBlank() || next.url.startsWith("content://") || next.url.startsWith("file://")) return@launch
            if (com.music.aalap.data.recommendation.engine.VibeStore.tempoOf(app, next) != null) return@launch
            var url = SongPlayer.knownStreamUrl(next.url)
            if (url == null) {
                SongPlayer.prefetch(next.url, app)
                repeat(20) { if (url == null) { delay(1_000); url = SongPlayer.knownStreamUrl(next.url) } }
            }
            val u = url?.takeIf { it.startsWith("http") && !it.contains(".m3u8") } ?: return@launch
            delay(3_000) // the saved start is written right after the address is found
            val started = System.currentTimeMillis()
            val r = runCatching { decode(app, u) }.onFailure { Log.d(TAG, "couldn't measure ${next.title}: ${it.message}") }.getOrNull() ?: return@launch
            Log.d(TAG, "measured ahead '${next.title}': %.0f bpm, energy %.2f in %d ms".format(r.bpm, r.energy, System.currentTimeMillis() - started))
            com.music.aalap.data.recommendation.engine.VibeStore.saveAudio(app, next, r)
            delay(500)
            if (state.songId.value != current.id) return@launch
            if (!com.music.aalap.data.recommendation.PlayContext.isAuto(next.id)) return@launch
            if (!fits(app, current, next)) {
                Log.d(TAG, "'${next.title}' jumps in pace after '${current.title}': picking again")
                kotlinx.coroutines.withContext(Dispatchers.Main) { SmartPlay.repickAhead(app, state) }
            }
        }
    }

    /** Whether [next] keeps the pace of [current] (as the queue's looser step allows). */
    private fun fits(context: Context, current: SongsModel, next: SongsModel): Boolean = runCatching {
        val db = com.music.aalap.data.recommendation.engine.RecoDb.get(context)
        val (a, b) = db.resolveAll(listOf(current, next).map(com.music.aalap.data.recommendation.engine.SongBridge::toCandidate))
        val va = com.music.aalap.data.recommendation.engine.VibeStore.vibe(context, a)
        val vb = com.music.aalap.data.recommendation.engine.VibeStore.vibe(context, b)
        com.music.aalap.data.recommendation.engine.QueuePicker.flowOk(va, vb, va, strict = false)
    }.getOrDefault(true)

    /** Decodes ~25 s of the saved start (from 4 s in) and measures it. */
    private fun decode(context: Context, url: String): com.music.aalap.audio.AudioFeatures.Result? {
        val ex = android.media.MediaExtractor()
        val src = SongPlayer.introSource(context, url)
        var codec: android.media.MediaCodec? = null
        try {
            ex.setDataSource(src)
            val track = (0 until ex.trackCount).firstOrNull { ex.getTrackFormat(it).getString(android.media.MediaFormat.KEY_MIME)?.startsWith("audio/") == true } ?: return null
            ex.selectTrack(track)
            val fmt = ex.getTrackFormat(track)
            codec = android.media.MediaCodec.createDecoderByType(fmt.getString(android.media.MediaFormat.KEY_MIME)!!)
            codec.configure(fmt, null, null, 0)
            codec.start()
            var rate = fmt.getInteger(android.media.MediaFormat.KEY_SAMPLE_RATE)
            var channels = fmt.getInteger(android.media.MediaFormat.KEY_CHANNEL_COUNT)
            var features = com.music.aalap.audio.AudioFeatures(rate)
            val info = android.media.MediaCodec.BufferInfo()
            var inputDone = false
            var mono = FloatArray(8192)
            val deadline = System.currentTimeMillis() + 15_000
            while (System.currentTimeMillis() < deadline) {
                if (!inputDone) {
                    val i = codec.dequeueInputBuffer(5_000)
                    if (i >= 0) {
                        val buf = codec.getInputBuffer(i)!!
                        val n = ex.readSampleData(buf, 0)
                        if (n < 0) { codec.queueInputBuffer(i, 0, 0, 0, android.media.MediaCodec.BUFFER_FLAG_END_OF_STREAM); inputDone = true }
                        else { codec.queueInputBuffer(i, 0, n, ex.sampleTime, 0); ex.advance() }
                    }
                }
                val o = codec.dequeueOutputBuffer(info, 5_000)
                if (o == android.media.MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    val f = codec.outputFormat
                    rate = f.getInteger(android.media.MediaFormat.KEY_SAMPLE_RATE)
                    channels = f.getInteger(android.media.MediaFormat.KEY_CHANNEL_COUNT)
                    features = com.music.aalap.audio.AudioFeatures(rate)
                } else if (o >= 0) {
                    val out = codec.getOutputBuffer(o)!!.order(java.nio.ByteOrder.LITTLE_ENDIAN)
                    if (info.presentationTimeUs >= 4_000_000 && info.size > 0) {
                        out.position(info.offset); out.limit(info.offset + info.size)
                        val shorts = out.asShortBuffer()
                        val frames = shorts.remaining() / channels
                        if (mono.size < frames) mono = FloatArray(frames)
                        for (fr in 0 until frames) {
                            var sum = 0
                            for (c in 0 until channels) sum += shorts.get().toInt()
                            mono[fr] = sum / (channels * 32768f)
                        }
                        features.add(mono, frames)
                    }
                    codec.releaseOutputBuffer(o, false)
                    if (features.seconds >= 25 || info.flags and android.media.MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                }
            }
            // At least 12 s heard, or the tempo isn't worth trusting.
            return if (features.seconds >= 12) features.result() else null
        } finally {
            runCatching { codec?.stop() }; runCatching { codec?.release() }
            runCatching { ex.release() }; runCatching { src.close() }
        }
    }
}

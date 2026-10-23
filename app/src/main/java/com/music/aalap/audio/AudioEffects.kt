package com.music.aalap.audio

import android.content.Context
import android.media.AudioManager
import android.media.audiofx.BassBoost
import android.media.audiofx.DynamicsProcessing
import android.media.audiofx.Equalizer
import android.os.Build
import android.util.Log
import com.music.aalap.data.preferences.EQ_REFERENCE_HZ
import com.music.aalap.data.preferences.currentEqGains
import com.music.aalap.data.preferences.getBassBoost
import com.music.aalap.data.preferences.isEqEnabled
import com.music.aalap.data.preferences.isNormalizeVolume
import kotlin.math.abs
import kotlin.math.ln

/**
 * The Sound settings (equalizer, bass boost, volume normalisation), applied with
 * Android's audio effects.
 *
 * Every ExoPlayer Aalap creates (the main one, crossfade and lossless-upgrade
 * players) shares one audio session id from [sessionId], so the effects attached
 * here keep working across player swaps without re-attaching.
 */
object AudioEffects {
    private const val TAG = "AudioEffects"

    @Volatile private var session = 0
    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var dynamics: DynamicsProcessing? = null

    /** The shared audio session for all players; effects are attached on first use. */
    @Synchronized
    fun sessionId(context: Context): Int {
        if (session == 0) {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            session = am.generateAudioSessionId()
            apply(context)
        }
        return session
    }

    /** Equalizer band centre frequencies (Hz) on this phone, low to high. */
    fun bandFrequencies(): List<Int> = runCatching {
        val eq = equalizer ?: return emptyList()
        (0 until eq.numberOfBands).map { eq.getCenterFreq(it.toShort()) / 1000 }
    }.getOrDefault(emptyList())

    /** Re-reads the Sound settings and pushes them into the effects. */
    @Synchronized
    fun apply(context: Context) {
        if (session == 0) return
        val ctx = context.applicationContext
        applyEqualizer(ctx)
        applyBassBoost(ctx)
        applyNormalize(ctx)
    }

    private fun applyEqualizer(ctx: Context) {
        val on = isEqEnabled(ctx)
        if (!on) {
            equalizer?.enabled = false
            return
        }
        val eq = equalizer ?: runCatching { Equalizer(0, session) }
            .onFailure { Log.w(TAG, "equalizer unavailable: $it") }.getOrNull()
            ?.also { equalizer = it } ?: return
        val (minMb, maxMb) = eq.bandLevelRange.let { it[0].toInt() to it[1].toInt() }
        val gains = currentEqGains(ctx)
        for (band in 0 until eq.numberOfBands) {
            val hz = eq.getCenterFreq(band.toShort()) / 1000
            val mb = (gainAt(hz, gains) * 100).toInt().coerceIn(minMb, maxMb)
            runCatching { eq.setBandLevel(band.toShort(), mb.toShort()) }
        }
        eq.enabled = true
    }

    /** Gain (dB) for a device band: the preset value of the nearest reference band (log scale). */
    private fun gainAt(hz: Int, gains: List<Float>): Float {
        if (hz <= 0) return gains.first()
        val idx = EQ_REFERENCE_HZ.indices.minByOrNull { abs(ln(hz.toDouble()) - ln(EQ_REFERENCE_HZ[it].toDouble())) } ?: 0
        return gains[idx]
    }

    private fun applyBassBoost(ctx: Context) {
        val strength = getBassBoost(ctx)
        if (strength <= 0) {
            bassBoost?.enabled = false
            return
        }
        val bb = bassBoost ?: runCatching { BassBoost(0, session) }
            .onFailure { Log.w(TAG, "bass boost unavailable: $it") }.getOrNull()
            ?.also { bassBoost = it } ?: return
        if (bb.strengthSupported) runCatching { bb.setStrength(strength.toShort()) }
        bb.enabled = true
    }

    /**
     * "Normalize volume": a gentle compressor + limiter that pulls loud masters down
     * and lifts quiet ones, so songs play at a similar loudness (Android 9+).
     */
    private fun applyNormalize(ctx: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return
        val on = isNormalizeVolume(ctx)
        if (!on) {
            dynamics?.enabled = false
            return
        }
        val dp = dynamics ?: runCatching {
            val config = DynamicsProcessing.Config.Builder(
                DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
                2, false, 0, true, 1, false, 0, true,
            ).build()
            DynamicsProcessing(0, session, config)
        }.onFailure { Log.w(TAG, "dynamics processing unavailable: $it") }.getOrNull()
            ?.also { dynamics = it } ?: return
        runCatching {
            val band = DynamicsProcessing.MbcBand(true, 20_000f, 3f, 200f, 2f, -28f, 0f, -90f, 1f, 0f, 6f)
            val limiter = DynamicsProcessing.Limiter(true, true, 0, 1f, 60f, 10f, -2f, 0f)
            for (ch in 0 until 2) {
                dp.setMbcBandByChannelIndex(ch, 0, band)
                dp.setLimiterByChannelIndex(ch, limiter)
            }
        }.onFailure { Log.w(TAG, "normalize config failed: $it") }
        dp.enabled = true
    }
}

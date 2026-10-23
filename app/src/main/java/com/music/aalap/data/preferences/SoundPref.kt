package com.music.aalap.data.preferences

import android.content.Context

/**
 * Equalizer presets as gains (dB) at five reference frequencies. They are mapped
 * onto whatever bands the phone's equalizer actually has (see AudioEffects).
 */
enum class EqPreset(val label: String, val gainsDb: List<Float>) {
    FLAT("Flat", listOf(0f, 0f, 0f, 0f, 0f)),
    BOLLYWOOD("Bollywood", listOf(4f, 2f, 0f, 2f, 3f)),
    BASS("Bass", listOf(6f, 4f, 0f, 0f, 0f)),
    VOCAL("Vocal", listOf(-2f, 0f, 3f, 3f, 1f)),
    CLASSICAL("Classical", listOf(3f, 2f, -1f, 2f, 3f)),
    DEVOTIONAL("Devotional", listOf(2f, 1f, 2f, 1f, -1f)),
    CUSTOM("Custom", listOf(0f, 0f, 0f, 0f, 0f)),
}

/** Reference centre frequencies (Hz) the preset gains are defined at. */
val EQ_REFERENCE_HZ = listOf(60, 230, 910, 3_600, 14_000)

/** Playback speeds offered in the UI (mainly for podcasts). */
val PLAYBACK_SPEEDS = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)

private const val PREF = "sound_prefs"
private const val KEY_EQ_ON = "eq_enabled"
private const val KEY_PRESET = "eq_preset"
private const val KEY_CUSTOM = "eq_custom_gains"
private const val KEY_BASS = "bass_boost"
private const val KEY_NORMALIZE = "normalize_volume"
private const val KEY_SPEED = "playback_speed"

private fun prefs(c: Context) = c.getSharedPreferences(PREF, Context.MODE_PRIVATE)

fun isEqEnabled(c: Context): Boolean = prefs(c).getBoolean(KEY_EQ_ON, false)
fun setEqEnabled(c: Context, v: Boolean) = prefs(c).edit().putBoolean(KEY_EQ_ON, v).apply()

fun getEqPreset(c: Context): EqPreset =
    runCatching { EqPreset.valueOf(prefs(c).getString(KEY_PRESET, null) ?: "") }.getOrDefault(EqPreset.FLAT)
fun setEqPreset(c: Context, p: EqPreset) = prefs(c).edit().putString(KEY_PRESET, p.name).apply()

/** The user's own 5 gains (dB), used by [EqPreset.CUSTOM]. */
fun getCustomEqGains(c: Context): List<Float> =
    prefs(c).getString(KEY_CUSTOM, null)?.split(',')?.mapNotNull { it.toFloatOrNull() }
        ?.takeIf { it.size == EQ_REFERENCE_HZ.size } ?: EqPreset.FLAT.gainsDb
fun setCustomEqGains(c: Context, gains: List<Float>) =
    prefs(c).edit().putString(KEY_CUSTOM, gains.joinToString(",")).apply()

/** Gains for the selected preset (custom gains when the preset is Custom). */
fun currentEqGains(c: Context): List<Float> =
    getEqPreset(c).let { if (it == EqPreset.CUSTOM) getCustomEqGains(c) else it.gainsDb }

/** Bass boost strength 0..1000 (Android's BassBoost scale). */
fun getBassBoost(c: Context): Int = prefs(c).getInt(KEY_BASS, 0)
fun setBassBoost(c: Context, v: Int) = prefs(c).edit().putInt(KEY_BASS, v.coerceIn(0, 1000)).apply()

fun isNormalizeVolume(c: Context): Boolean = prefs(c).getBoolean(KEY_NORMALIZE, false)
fun setNormalizeVolume(c: Context, v: Boolean) = prefs(c).edit().putBoolean(KEY_NORMALIZE, v).apply()

fun getPlaybackSpeed(c: Context): Float = prefs(c).getFloat(KEY_SPEED, 1f)
fun setPlaybackSpeedPref(c: Context, v: Float) = prefs(c).edit().putFloat(KEY_SPEED, v).apply()

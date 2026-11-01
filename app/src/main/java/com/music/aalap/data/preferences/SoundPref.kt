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

/**
 * Sound settings are kept per output: your Bluetooth buds can have more bass while the
 * phone speaker stays flat. [SoundOutput.current] is the output music is going to now
 * ("" for the phone itself, whose settings are the plain keys every older version used).
 * An output that has never been set up starts from the phone's settings.
 */
object SoundOutput {
    @Volatile var current: String = ""

    /** The key a setting is read from: the output's own once it has one, else the phone's. Pure. */
    fun readKey(base: String, output: String, has: (String) -> Boolean): String =
        if (output.isNotEmpty() && has("$base@$output")) "$base@$output" else base

    /** The key a setting is written to: the output's own. Pure. */
    fun writeKey(base: String, output: String): String = if (output.isEmpty()) base else "$base@$output"
}

private fun rk(c: Context, base: String) = SoundOutput.readKey(base, SoundOutput.current) { prefs(c).contains(it) }

/**
 * Changing one setting for an output copies the phone's other settings to it first, so
 * the output keeps them even if the phone's are changed later.
 */
private fun edit(c: Context, block: android.content.SharedPreferences.Editor.(String) -> Unit) {
    val out = SoundOutput.current
    val p = prefs(c)
    val e = p.edit()
    if (out.isNotEmpty() && PER_OUTPUT.none { p.contains("$it@$out") }) {
        p.all.filterKeys { it in PER_OUTPUT }.forEach { (k, v) ->
            when (v) {
                is Boolean -> e.putBoolean("$k@$out", v)
                is String -> e.putString("$k@$out", v)
                is Int -> e.putInt("$k@$out", v)
            }
        }
    }
    e.block(out)
    e.apply()
}

private val PER_OUTPUT = listOf(KEY_EQ_ON, KEY_PRESET, KEY_CUSTOM, KEY_BASS, KEY_NORMALIZE)

fun isEqEnabled(c: Context): Boolean = prefs(c).getBoolean(rk(c, KEY_EQ_ON), false)
fun setEqEnabled(c: Context, v: Boolean) = edit(c) { putBoolean(SoundOutput.writeKey(KEY_EQ_ON, it), v) }

fun getEqPreset(c: Context): EqPreset =
    runCatching { EqPreset.valueOf(prefs(c).getString(rk(c, KEY_PRESET), null) ?: "") }.getOrDefault(EqPreset.FLAT)
fun setEqPreset(c: Context, p: EqPreset) = edit(c) { putString(SoundOutput.writeKey(KEY_PRESET, it), p.name) }

/** The user's own 5 gains (dB), used by [EqPreset.CUSTOM]. */
fun getCustomEqGains(c: Context): List<Float> =
    prefs(c).getString(rk(c, KEY_CUSTOM), null)?.split(',')?.mapNotNull { it.toFloatOrNull() }
        ?.takeIf { it.size == EQ_REFERENCE_HZ.size } ?: EqPreset.FLAT.gainsDb
fun setCustomEqGains(c: Context, gains: List<Float>) =
    edit(c) { putString(SoundOutput.writeKey(KEY_CUSTOM, it), gains.joinToString(",")) }

/** Gains for the selected preset (custom gains when the preset is Custom). */
fun currentEqGains(c: Context): List<Float> =
    getEqPreset(c).let { if (it == EqPreset.CUSTOM) getCustomEqGains(c) else it.gainsDb }

/** Bass boost strength 0..1000 (Android's BassBoost scale). */
fun getBassBoost(c: Context): Int = prefs(c).getInt(rk(c, KEY_BASS), 0)
fun setBassBoost(c: Context, v: Int) = edit(c) { putInt(SoundOutput.writeKey(KEY_BASS, it), v.coerceIn(0, 1000)) }

fun isNormalizeVolume(c: Context): Boolean = prefs(c).getBoolean(rk(c, KEY_NORMALIZE), false)
fun setNormalizeVolume(c: Context, v: Boolean) = edit(c) { putBoolean(SoundOutput.writeKey(KEY_NORMALIZE, it), v) }

fun getPlaybackSpeed(c: Context): Float = prefs(c).getFloat(KEY_SPEED, 1f)
fun setPlaybackSpeedPref(c: Context, v: Float) = prefs(c).edit().putFloat(KEY_SPEED, v).apply()

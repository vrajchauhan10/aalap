package com.music.aalap.ui.components

/**
 * How the cover's little bars move: one step per beat of the song, from the tempo Aalap
 * measured the first time the song played (stored with the recommendations; nothing is
 * listened to for this). The phase of the beat isn't known, only its speed, so the bars keep
 * the song's pace rather than land exactly on each drum hit. Pure (BeatBarsTest).
 */
object BeatBars {
    /** Slowest and fastest step, whatever the tempo says (a very fast song steps on every
     *  other beat, so the bars glide rather than flicker). */
    const val MIN_STEP_MS = 330L
    const val MAX_STEP_MS = 1_000L
    /** Time between redraws while the bars glide: 25 a second. Each redraw repaints the whole
     *  window (Home's art and shadows): measured on an S24 FE, Home open and playing, 14–19% of
     *  a core with still bars, 49–53% at this rate, 67–69% at the display's 60. */
    const val FRAME_MS = 40L
    /** Step for a song never measured yet: a calm, neutral pace, not a guessed tempo. */
    const val UNKNOWN_STEP_MS = 600L

    /** Time between steps for a tempo in beats per minute (null or nonsense: not measured). */
    fun stepMs(bpm: Double?): Long {
        if (bpm == null || bpm.isNaN() || bpm < 30.0 || bpm > 300.0) return UNKNOWN_STEP_MS
        var beat = 60_000.0 / bpm
        // Faster than the screen should move: every other beat (still on the beat).
        while (beat < MIN_STEP_MS) beat *= 2
        while (beat > MAX_STEP_MS) beat /= 2
        return beat.toLong()
    }

    /**
     * Heights (0..1) of [bars] bars at [step]: the first beat of every four stands tallest, the
     * others vary but stay readable; the same step always gives the same picture.
     */
    fun heights(step: Int, bars: Int = 4, seed: Int = 0): List<Float> {
        val accent = step % 4 == 0
        return List(bars) { i ->
            val r = noise(step * 31 + i * 7 + seed)
            val base = if (accent) 0.7f + 0.3f * r else 0.3f + 0.5f * r
            base.coerceIn(0.25f, 1f)
        }
    }

    /** At rest (paused, loading, interrupted): low and still. */
    val REST = listOf(0.3f, 0.45f, 0.35f, 0.4f)

    /** How long the bars glide after music starts, resumes, changes song or Aalap comes back
     *  on screen; then they hold [HOLD]. Gliding all the time measured +35% of a core. */
    const val GLIDE_MS = 8_000L
    /** Still, but clearly "playing" (taller than [REST]). */
    val HOLD = listOf(0.55f, 0.9f, 0.6f, 0.75f)

    /** Beats of gliding for a step length: about [GLIDE_MS] worth, at least one. */
    fun glideBeats(stepMs: Long): Int = (GLIDE_MS / stepMs.coerceAtLeast(1)).toInt().coerceAtLeast(1)

    private fun noise(n: Int): Float {
        var x = n * 0x45d9f3b
        x = (x ushr 16 xor x) * 0x45d9f3b
        x = x ushr 16 xor x
        return (x and 0xffff) / 65535f
    }
}

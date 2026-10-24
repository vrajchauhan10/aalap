package com.music.aalap.audio

import kotlin.math.ln
import kotlin.math.sqrt

/**
 * What the sound of a song says, measured on the phone while it plays: its tempo
 * (beats per minute) and energy (loudness, how strong the beat is, and tempo; 0..1).
 * Feeds the recommendations (energy, and a smooth flow of tempo from song to song).
 *
 * Loudness is measured on overlapping windows of mono audio every [hop] samples (~6 ms);
 * "onsets" are where it rises (beats landing). The tempo is the beat gap whose repeats
 * line up best, checked at 1–4 gaps at once ("comb"), between 60 and 180 bpm, leaning
 * toward the usual ~120 when two tempos fit equally; in-between strokes (tabla,
 * hi-hats) that are softer than the beat don't read as double time.
 * Pure (no Android), so it can be tested with generated audio.
 */
class AudioFeatures(private val sampleRate: Int, private val window: Int = 1024, private val hop: Int = 256) {

    data class Result(val bpm: Double, val energy: Double, val loudnessDb: Double, val beatStrength: Double)

    private val rms = ArrayList<Float>()
    private val ring = FloatArray(window)
    private var pos = 0
    private var seen = 0L
    private var sinceHop = 0
    private var sumSq = 0.0

    /** Seconds of audio analysed so far. */
    val seconds: Double get() = seen.toDouble() / sampleRate

    /** Adds mono samples (-1..1). */
    fun add(samples: FloatArray, count: Int = samples.size) {
        for (i in 0 until count) {
            val x = samples[i]
            val old = ring[pos]
            ring[pos] = x
            pos = (pos + 1) % window
            sumSq += x * x - old * old
            seen++
            if (++sinceHop == hop) {
                sinceHop = 0
                if (seen >= window) rms += sqrt(maxOf(0.0, sumSq) / window).toFloat()
            }
        }
    }

    fun reset() { rms.clear(); ring.fill(0f); pos = 0; seen = 0; sinceHop = 0; sumSq = 0.0 }

    /** The result, or null with too little audio (under ~8 s) or near silence. */
    fun result(): Result? {
        val rate = sampleRate.toDouble() / hop
        if (rms.size < rate * 8) return null
        val meanRms = rms.average()
        if (meanRms < 1e-4) return null
        // Onset strength: how much the loudness rose over one window's length.
        val step = window / hop
        val n = rms.size
        val onset = DoubleArray(n)
        for (i in step until n) onset[i] = maxOf(0.0, (rms[i] - rms[i - step]).toDouble())
        val mean = onset.average()
        for (i in onset.indices) onset[i] -= mean

        val cache = HashMap<Int, Double>()
        fun ac(l: Int): Double {
            if (l <= 0 || l >= n) return 0.0
            return cache.getOrPut(l) { var c = 0.0; for (i in 0 until n - l) c += onset[i] * onset[i + l]; c / (n - l) }
        }
        // A gap's fit: how well beats line up 1, 2, 3 and 4 gaps later (each at the best
        // neighbouring step, since a real beat gap falls between steps).
        fun near(l: Int) = maxOf(ac(l - 1), ac(l), ac(l + 1))
        fun comb(l: Int) = (near(l) + 0.8 * near(2 * l) + 0.6 * near(3 * l) + 0.5 * near(4 * l)) / 2.9

        val minLag = (rate * 60 / 180).toInt().coerceAtLeast(2)
        val maxLag = (rate * 60 / 60).toInt().coerceAtMost(n / 5)
        if (maxLag <= minLag) return null
        var bestLag = 0; var best = 0.0; var bestWeighted = 0.0
        for (lag in minLag..maxLag) {
            val c = comb(lag)
            val octaves = ln(60.0 * rate / lag / 120.0) / ln(2.0)
            val weighted = c * kotlin.math.exp(-0.5 * octaves * octaves)
            if (weighted > bestWeighted) { bestWeighted = weighted; best = c; bestLag = lag }
        }
        if (bestLag == 0) return null
        // Softer strokes between the beats: the gap from beat to beat repeats more
        // strongly than the gap to the stroke, so a fast reading is really half that.
        if (60.0 * rate / bestLag > 140 && near(2 * bestLag) > 1.15 * near(bestLag)) { bestLag *= 2; best = comb(bestLag) }
        // Refine between steps (parabolic peak) for a steadier bpm.
        var lag = bestLag.toDouble()
        val y0 = ac(bestLag - 1); val y1 = ac(bestLag); val y2 = ac(bestLag + 1)
        val d = y0 - 2 * y1 + y2
        if (d < 0) lag += (0.5 * (y0 - y2) / d).coerceIn(-0.5, 0.5)
        val bpm = 60.0 * rate / lag

        var zero = 0.0
        for (x in onset) zero += x * x
        val beatStrength = if (zero > 0) (near(bestLag) / (zero / n)).coerceIn(0.0, 1.0) else 0.0
        val loudnessDb = 20 * ln(meanRms) / ln(10.0)
        // Energy: loudness (-30 dB quiet … -8 dB loud), a strong beat, and tempo.
        // Songs are mastered about equally loud today, so the beat and tempo say more.
        val loud = ((loudnessDb + 30) / 22).coerceIn(0.0, 1.0)
        val tempo = ((bpm - 70) / 70).coerceIn(0.0, 1.0)
        val energy = (0.3 * loud + 0.35 * beatStrength + 0.35 * tempo).coerceIn(0.0, 1.0)
        return Result(bpm, energy, loudnessDb, beatStrength)
    }
}

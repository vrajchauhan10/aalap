package com.music.aalap.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/** Tempo and energy from generated audio. */
class AudioFeaturesTest {
    private val sr = 44_100

    /** [seconds] of a beat at [bpm]: decaying drum hits over a quiet tone, at [volume]. */
    private fun beat(bpm: Double, seconds: Int, volume: Double, seed: Long = 1): FloatArray {
        val r = Random(seed)
        val n = sr * seconds
        val period = sr * 60.0 / bpm
        return FloatArray(n) { i ->
            val t = (i % period) / sr
            val hit = exp(-t * 30) * sin(2 * PI * 80 * t) * 0.9 + exp(-t * 60) * (r.nextDouble() - 0.5) * 0.6
            val tone = sin(2 * PI * 220 * i / sr) * 0.08
            ((hit + tone) * volume).toFloat()
        }
    }

    /** A 94 bpm beat with softer strokes in between (like tabla): the beat is 94, not 188. */
    @Test fun inBetweenStrokesAreNotDoubleTime() {
        val main = beat(94.0, 30, 0.8)
        val half = beat(94.0, 30, 0.5, seed = 2)
        val shift = (sr * 60.0 / 94 / 2).toInt()
        val mixed = FloatArray(main.size) { i -> main[i] + if (i >= shift) half[i - shift] else 0f }
        val res = analyse(mixed)!!
        assertEquals(94.0, res.bpm, 94 * 0.05)
    }

    private fun analyse(samples: FloatArray) = AudioFeatures(sr).apply { add(samples) }.result()

    @Test fun findsTheTempo() {
        for (bpm in listOf(95.0, 120.0, 150.0)) {
            val res = analyse(beat(bpm, 30, 0.8))!!
            assertEquals("bpm $bpm", bpm, res.bpm, bpm * 0.04)
        }
    }

    @Test fun loudFastIsMoreEnergeticThanQuietSlow() {
        val party = analyse(beat(128.0, 25, 0.9))!!
        val ballad = analyse(beat(72.0, 25, 0.12))!!
        assertTrue("party ${party.energy} > ballad ${ballad.energy}", party.energy > ballad.energy + 0.25)
    }

    @Test fun needsEnoughAudio() {
        assertNull(analyse(beat(120.0, 3, 0.8)))
        assertNull(analyse(FloatArray(sr * 20)))
    }
}

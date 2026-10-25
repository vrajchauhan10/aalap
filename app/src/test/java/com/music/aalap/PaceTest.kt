package com.music.aalap

import com.music.aalap.data.recommendation.engine.Pace
import com.music.aalap.data.recommendation.engine.Vibe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Your pace lately, and mixes leaning towards it. */
class PaceTest {
    private fun song(energy: Double, bpm: Double? = null) = Vibe(emptyMap(), 0.0, audioEnergy = energy, bpm = bpm)

    @Test fun tooFewSongsGiveNoPace() {
        assertNull(Pace.target(List(4) { song(0.8) }))
        assertNull(Pace.target(List(10) { Vibe.UNKNOWN }))
    }

    @Test fun fastHindiRunMakesAFastPace() {
        val t = Pace.target(List(8) { song(0.85, 128.0) })!!
        assertEquals(0.85, t.energy, 0.001)
        assertEquals(128.0, t.bpm!!, 0.001)
        assertEquals("fast and lively", t.label)
    }

    @Test fun newerSongsCountMore() {
        val t = Pace.target(List(5) { song(0.9) } + List(5) { song(0.1) })!!
        assertTrue(t.energy > 0.5)
    }

    @Test fun fastEnglishSongsComeFirstAfterFastHindi() {
        val t = Pace.target(List(8) { song(0.85, 128.0) })!!
        val english = listOf("slow ballad" to song(0.2, 70.0), "mid" to song(0.5, 100.0), "fast" to song(0.88, 126.0), "unknown" to Vibe.UNKNOWN)
        val ranked = Pace.rank(english, t, { it.second }, kotlin.random.Random(1), jitter = 0.0).map { it.first }
        // An unmeasured song sits halfway; a mid-tempo one is further from a fast pace than that.
        assertEquals(listOf("fast", "unknown", "mid", "slow ballad"), ranked)
    }

    @Test fun halfAndDoubleTempoStillMatch() {
        val t = Pace.target(List(8) { song(0.8, 140.0) })!!
        assertTrue(Pace.closeness(t, song(0.8, 70.0)) > 0.95)
    }

    @Test fun noPaceJustShuffles() {
        val items = (1..20).toList()
        assertEquals(items.toSet(), Pace.rank(items, null, { Vibe.UNKNOWN }, kotlin.random.Random(3)).toSet())
    }
}

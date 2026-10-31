package com.music.aalap.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The cover's bars step with the song's measured tempo, within what the screen should do. */
class BeatBarsTest {
    @Test fun stepIsOneBeat() {
        assertEquals(500L, BeatBars.stepMs(120.0))
        assertEquals(750L, BeatBars.stepMs(80.0))
    }

    @Test fun fastSongsStepEveryOtherBeat() {
        // 200 bpm = 300 ms a beat, faster than the bars should redraw: every other beat.
        assertEquals(600L, BeatBars.stepMs(200.0))
    }

    @Test fun slowSongsStepOnHalfBeats() {
        assertEquals(600L, BeatBars.stepMs(50.0)) // 1200 ms a beat → 600 ms
    }

    @Test fun unmeasuredOrNonsenseTempoUsesTheNeutralPace() {
        listOf(null, Double.NaN, 0.0, 10.0, 1000.0).forEach {
            assertEquals(BeatBars.UNKNOWN_STEP_MS, BeatBars.stepMs(it))
        }
    }

    @Test fun everyTempoStaysWithinTheRedrawLimits() {
        var bpm = 30.0
        while (bpm <= 300.0) {
            val s = BeatBars.stepMs(bpm)
            assertTrue("$bpm → $s", s in BeatBars.MIN_STEP_MS..BeatBars.MAX_STEP_MS)
            bpm += 0.5
        }
    }

    @Test fun heightsAreStableBoundedAndAccentTheFirstBeat() {
        assertEquals(BeatBars.heights(5, seed = 9), BeatBars.heights(5, seed = 9))
        repeat(64) { step ->
            val h = BeatBars.heights(step)
            assertEquals(4, h.size)
            h.forEach { assertTrue(it in 0.25f..1f) }
            if (step % 4 == 0) assertTrue(h.all { it >= 0.7f })
        }
    }

    @Test fun glidesForAboutEightSecondsThenHolds() {
        assertEquals(16, BeatBars.glideBeats(500)) // 120 bpm
        assertEquals(13, BeatBars.glideBeats(600))
        assertEquals(1, BeatBars.glideBeats(20_000))
        listOf(BeatBars.MIN_STEP_MS, BeatBars.UNKNOWN_STEP_MS, BeatBars.MAX_STEP_MS).forEach {
            assertTrue(BeatBars.glideBeats(it) * it in 7_000L..BeatBars.GLIDE_MS)
        }
    }

    @Test fun holdPoseLooksPlayingNotPaused() {
        assertTrue(BeatBars.HOLD.zip(BeatBars.REST).all { (h, r) -> h > r })
    }
}

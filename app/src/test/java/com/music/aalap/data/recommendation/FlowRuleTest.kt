package com.music.aalap.data.recommendation

import com.music.aalap.data.api.Discover
import com.music.aalap.data.api.Mood
import com.music.aalap.data.recommendation.engine.Feel
import com.music.aalap.data.recommendation.engine.QueuePicker
import com.music.aalap.data.recommendation.engine.Vibe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FlowRuleTest {
    private fun v(energy: Double?, bpm: Double?, feel: Feel? = null) =
        Vibe(if (feel != null) mapOf(feel to 1.0) else emptyMap(), if (feel != null) 1.0 else 0.0, emptySet(), energy, bpm)

    private val midSlow = v(0.40, 92.0, Feel.ROMANTIC)

    @Test fun energeticAfterMidSlowIsBlocked() {
        assertFalse(QueuePicker.flowOk(midSlow, v(0.92, 128.0, Feel.ENERGETIC), midSlow, strict = true))
        assertFalse(QueuePicker.flowOk(midSlow, v(0.92, 128.0, Feel.ENERGETIC), midSlow, strict = false))
    }

    @Test fun similarPaceFlows() {
        assertTrue(QueuePicker.flowOk(midSlow, v(0.48, 96.0, Feel.ROMANTIC), midSlow, strict = true))
        // Half time is the same pulse: 184 bpm counts as 92.
        assertTrue(QueuePicker.flowOk(midSlow, v(0.45, 184.0, Feel.ROMANTIC), midSlow, strict = true))
    }

    @Test fun tempoJumpBlockedEvenAtSameEnergy() {
        assertFalse(QueuePicker.flowOk(midSlow, v(0.42, 120.0, Feel.ROMANTIC), midSlow, strict = true))
        // The looser step allows up to 20%.
        assertTrue(QueuePicker.flowOk(midSlow, v(0.42, 108.0, Feel.ROMANTIC), midSlow, strict = false))
    }

    @Test fun unknownFailsStrictButPassesLoose() {
        // A song nobody knows the feel of can't prove it fits (a slow song followed a fast one).
        assertFalse(QueuePicker.flowOk(midSlow, v(null, null), midSlow, strict = true))
        assertTrue(QueuePicker.flowOk(midSlow, v(null, null), midSlow, strict = false))
    }

    @Test fun stationSearchesInYourLanguage() {
        assertEquals("romantic punjabi songs", Discover.queryFor(Mood.ROMANTIC, "punjabi"))
        assertEquals("tamil party hits", Discover.queryFor(Mood.PARTY, "tamil"))
        assertEquals("romantic hindi songs", Discover.queryFor(Mood.ROMANTIC, "hindi"))
        assertEquals("english gym workout", Discover.queryFor(Mood.GYM, "english"))
    }

    private fun f(v: Vibe, familiar: Boolean = false, liked: Boolean = false) = com.music.aalap.data.recommendation.engine.Features(
        song = com.music.aalap.data.recommendation.engine.Song("x", com.music.aalap.data.recommendation.engine.Candidate("T", "A")),
        global = 0.5, session = 0.0, vibe = v, language = "hindi", taste = 0.5, habit = 0.0,
        familiar = familiar, liked = liked,
    )

    @Test fun steerCalmerAndEnergy() {
        assertTrue(QueuePicker.steerFits(com.music.aalap.data.recommendation.engine.Steer.CALMER, midSlow, f(v(0.30, 84.0, Feel.SAD))))
        assertFalse(QueuePicker.steerFits(com.music.aalap.data.recommendation.engine.Steer.CALMER, midSlow, f(v(0.60, 110.0, Feel.HAPPY))))
        assertTrue(QueuePicker.steerFits(com.music.aalap.data.recommendation.engine.Steer.ENERGY, midSlow, f(v(0.55, 100.0, Feel.HAPPY))))
        assertFalse(QueuePicker.steerFits(com.music.aalap.data.recommendation.engine.Steer.ENERGY, midSlow, f(v(0.35, 88.0, Feel.SAD))))
    }

    @Test fun steerSameAndNew() {
        assertTrue(QueuePicker.steerFits(com.music.aalap.data.recommendation.engine.Steer.SAME, midSlow, f(v(0.42, 94.0, Feel.ROMANTIC))))
        assertFalse(QueuePicker.steerFits(com.music.aalap.data.recommendation.engine.Steer.SAME, midSlow, f(v(0.9, 128.0, Feel.ENERGETIC))))
        assertTrue(QueuePicker.steerFits(com.music.aalap.data.recommendation.engine.Steer.NEW, midSlow, f(v(0.4, 90.0))))
        assertFalse(QueuePicker.steerFits(com.music.aalap.data.recommendation.engine.Steer.NEW, midSlow, f(v(0.4, 90.0), familiar = true)))
    }

    @Test fun steerLeansOnGuesses() {
        val guessLively = v(null, null).let { Vibe(mapOf(Feel.ENERGETIC to 0.5, Feel.ROMANTIC to 0.5), 0.11, emptySet(), null, null) }
        assertFalse(QueuePicker.steerLeans(com.music.aalap.data.recommendation.engine.Steer.CALMER, v(0.55, 100.0, Feel.ROMANTIC), f(guessLively)))
        assertTrue(QueuePicker.steerLeans(com.music.aalap.data.recommendation.engine.Steer.CALMER, v(0.55, 100.0, Feel.ROMANTIC), f(v(0.30, null, Feel.SAD))))
    }

    // ── radio-style rules (Mango Shake → Dilruba) ──

    private val fast = v(0.93, 140.0, Feel.ENERGETIC)

    @Test fun slowRomanticNeverFollowsAFastSongStrictly() {
        assertFalse(QueuePicker.flowOk(fast, v(0.40, 84.0, Feel.ROMANTIC), fast, strict = true))
        assertFalse(QueuePicker.flowOk(fast, v(0.40, 84.0, Feel.ROMANTIC), fast, strict = false))
        // A song we know nothing about can't sneak in strictly either.
        assertFalse(QueuePicker.flowOk(fast, v(null, null), fast, strict = true))
    }

    @Test fun unknownSongReadsItsFeelFromWhatPlaysAfterIt() {
        val after = listOf(v(0.9, 138.0, Feel.ENERGETIC) to 1.0, v(0.88, 142.0, Feel.PARTY) to 0.8,
            v(0.95, 136.0, Feel.ENERGETIC) to 0.6, v(null, null) to 0.9)
        val guess = QueuePicker.stationFeel(after)!!
        assertTrue(guess.energyKnown)
        assertTrue("energy ${guess.energy}", guess.energy > 0.85)
        assertEquals(Feel.ENERGETIC, guess.dominant)
        assertEquals(138.0, guess.bpm!!, 0.1)
        // Fewer than three known suggestions: no guess.
        assertEquals(null, QueuePicker.stationFeel(after.take(2)))
    }

    @Test fun stationMoodFollowsTheSongAndTheSteer() {
        assertEquals("ENERGETIC", QueuePicker.stationMood(fast, null))
        assertEquals("ROMANTIC", QueuePicker.stationMood(midSlow, null))
        assertEquals("HAPPY", QueuePicker.stationMood(fast, com.music.aalap.data.recommendation.engine.Steer.CALMER))
        assertEquals("CHILL", QueuePicker.stationMood(midSlow, com.music.aalap.data.recommendation.engine.Steer.CALMER))
        assertEquals("PARTY", QueuePicker.stationMood(midSlow, com.music.aalap.data.recommendation.engine.Steer.ENERGY))
        assertEquals(null, QueuePicker.stationMood(null, null))
    }
}

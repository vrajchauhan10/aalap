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

    @Test fun unknownCannotFail() {
        assertTrue(QueuePicker.flowOk(midSlow, v(null, null), midSlow, strict = true))
    }

    @Test fun stationSearchesInYourLanguage() {
        assertEquals("romantic punjabi songs", Discover.queryFor(Mood.ROMANTIC, "punjabi"))
        assertEquals("tamil party hits", Discover.queryFor(Mood.PARTY, "tamil"))
        assertEquals("romantic hindi songs", Discover.queryFor(Mood.ROMANTIC, "hindi"))
        assertEquals("english gym workout", Discover.queryFor(Mood.GYM, "english"))
    }
}

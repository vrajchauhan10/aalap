package com.music.aalap.data.recommendation

import com.music.aalap.data.api.Mood
import com.music.aalap.data.recommendation.engine.Feel
import com.music.aalap.data.recommendation.engine.Vibe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Gym Freaks keeps energetic, fast songs first and leaves calm ones out. */
class GymFitTest {
    private fun t(name: String, energy: Double?, bpm: Double?) = GymFit.Track(name, energy, bpm)

    @Test fun energeticAndFastFits() {
        assertEquals(true, GymFit.fits(0.9, 128.0))
        assertEquals(true, GymFit.fits(0.7, null))
        assertEquals(true, GymFit.fits(null, 140.0))
    }

    @Test fun calmOrSlowDoesNot() {
        assertEquals(false, GymFit.fits(0.2, 70.0)) // sad ballad
        assertEquals(false, GymFit.fits(0.3, 130.0)) // chill, however fast
        assertEquals(false, GymFit.fits(0.6, 80.0)) // mid energy, slow
        assertEquals(false, GymFit.fits(null, 70.0))
    }

    @Test fun heavyHalfTimeBeatStays() {
        assertEquals(true, GymFit.fits(0.85, 85.0))
    }

    @Test fun unknownIsNotJudged() {
        assertNull(GymFit.fits(null, null))
    }

    @Test fun knownFitsFirstThenUnknownAndCalmLeftOut() {
        val big = (1..20).map { t("fast$it", 0.6 + it / 100.0, 125.0) }
        val list = listOf(t("ballad", 0.2, 70.0), t("new", null, null)) + big
        val out = GymFit.order(list)
        assertFalse("ballad" in out)
        assertEquals("fast20", out.first()) // most energetic first
        assertEquals("new", out.last()) // unknown after known fits
    }

    @Test fun shortStationKeepsCalmSongsAtTheEnd() {
        val out = GymFit.order(listOf(t("ballad", 0.2, 70.0), t("banger", 0.95, 130.0), t("new", null, null)))
        assertEquals(listOf("banger", "new", "ballad"), out)
    }

    @Test fun emptyStation() {
        assertTrue(GymFit.order(emptyList<GymFit.Track<String>>()).isEmpty())
    }

    @Test fun gymStationTeachesAnEnergeticFeel() {
        val feel = Vibe.forMoodName(Mood.GYM.name)
        assertTrue((feel[Feel.ENERGETIC] ?: 0.0) >= 0.5)
        assertEquals(1.0, feel.values.sum(), 1e-9)
    }
}

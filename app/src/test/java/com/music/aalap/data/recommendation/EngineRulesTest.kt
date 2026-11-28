package com.music.aalap.data.recommendation

import com.music.aalap.data.api.Discover
import com.music.aalap.data.api.Mood
import com.music.aalap.data.preferences.RecoPrefs
import com.music.aalap.data.recommendation.engine.Anchor
import com.music.aalap.data.recommendation.engine.Candidate
import com.music.aalap.data.recommendation.engine.Features
import com.music.aalap.data.recommendation.engine.Feel
import com.music.aalap.data.recommendation.engine.QueuePicker
import com.music.aalap.data.recommendation.engine.RecoDb
import com.music.aalap.data.recommendation.engine.Song
import com.music.aalap.data.recommendation.engine.Steer
import com.music.aalap.data.recommendation.engine.Trainer
import com.music.aalap.data.recommendation.engine.Vibe
import com.music.aalap.data.recommendation.engine.VibeStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EngineRulesTest {
    private fun v(energy: Double?, bpm: Double?, feel: Feel? = null) =
        Vibe(if (feel != null) mapOf(feel to 1.0) else emptyMap(), if (feel != null) 1.0 else 0.0, emptySet(), energy, bpm)
    private fun f(vibe: Vibe, familiar: Boolean = false, liked: Boolean = false) =
        Features(Song("x", Candidate("t", "a")), 0.5, 0.0, vibe, "hindi", 0.0, 0.0, familiar = familiar, liked = liked)

    // ── Languages ──
    private val mix = mapOf("hindi" to 0.6, "punjabi" to 0.4, "english" to 0.0)

    @Test fun offAndUnknownLanguagesNeverPlay() {
        assertTrue(RecoPrefs.languageAllowed(mix, "hindi"))
        assertTrue(RecoPrefs.languageAllowed(mix, "Punjabi"))
        assertTrue(RecoPrefs.languageAllowed(mix, "urdu")) // sung like Hindi
        assertFalse(RecoPrefs.languageAllowed(mix, "english"))
        assertFalse(RecoPrefs.languageAllowed(mix, "tamil"))
        assertFalse(RecoPrefs.languageAllowed(mix, ""))
        assertTrue(RecoPrefs.languageAllowed(emptyMap(), "english")) // before you choose
    }

    @Test fun gymSearchNamesYourLanguage() {
        assertEquals("hindi gym workout", Discover.queryFor(Mood.GYM, "hindi"))
        assertEquals("punjabi gym workout", Discover.queryFor(Mood.GYM, "punjabi"))
        assertEquals("romantic hindi songs", Discover.queryFor(Mood.ROMANTIC, "hindi"))
        assertEquals("bhakti devotional songs", Discover.queryFor(Mood.DEVOTIONAL, "hindi"))
    }

    // ── Staying with the song you started from ──
    private val romantic = Anchor(v(0.44, 90.0, Feel.ROMANTIC), era = 1)

    @Test fun anchorKeepsEnergyFeelAndEra() {
        assertTrue(QueuePicker.fitsAnchor(romantic, v(0.50, 95.0, Feel.ROMANTIC), era = 1, strict = true, steer = null))
        assertFalse("party after romantic", QueuePicker.fitsAnchor(romantic, v(0.62, 120.0, Feel.PARTY), 1, true, null))
        assertFalse("far in energy", QueuePicker.fitsAnchor(romantic, v(0.80, 100.0, Feel.HAPPY), 1, true, null))
        assertFalse("90s to new jumps two eras", QueuePicker.fitsAnchor(romantic, v(0.45, 90.0, Feel.ROMANTIC), era = 4, strict = true, steer = null))
        assertTrue("one era step is fine", QueuePicker.fitsAnchor(romantic, v(0.45, 90.0, Feel.ROMANTIC), era = 2, strict = true, steer = null))
        assertFalse("unknown era waits after an old song", QueuePicker.fitsAnchor(romantic, v(0.45, 90.0, Feel.ROMANTIC), era = null, strict = true, steer = null))
        // Calmer / More energy move away on purpose.
        assertTrue(QueuePicker.fitsAnchor(romantic, v(0.80, 100.0, Feel.ROMANTIC), 1, true, Steer.ENERGY))
    }

    // ── Steer ranges ──
    private val prev = v(0.70, 120.0, Feel.HAPPY)

    @Test fun steerButtonsHaveExactRanges() {
        assertTrue(QueuePicker.steerFits(Steer.CALMER, prev, f(v(0.58, null, Feel.HAPPY))))
        assertFalse("too small a step", QueuePicker.steerFits(Steer.CALMER, prev, f(v(0.68, null, Feel.HAPPY))))
        assertFalse("too big a drop", QueuePicker.steerFits(Steer.CALMER, prev, f(v(0.40, null, Feel.CHILL))))
        assertTrue("10% slower", QueuePicker.steerFits(Steer.CALMER, prev, f(v(0.69, 108.0, Feel.HAPPY))))
        assertTrue(QueuePicker.steerFits(Steer.ENERGY, prev, f(v(0.82, null, Feel.HAPPY))))
        assertFalse(QueuePicker.steerFits(Steer.ENERGY, prev, f(v(0.98, null, Feel.PARTY))))
        assertTrue(QueuePicker.steerFits(Steer.SAME, prev, f(v(0.74, 125.0, Feel.HAPPY))))
        assertFalse("tempo 15% off", QueuePicker.steerFits(Steer.SAME, prev, f(v(0.72, 138.0, Feel.HAPPY))))
        assertTrue(QueuePicker.steerFits(Steer.NEW, prev, f(v(0.7, null))))
        assertFalse(QueuePicker.steerFits(Steer.NEW, prev, f(v(0.7, null), familiar = true)))
    }

    // ── Labels from the trained playlists ──
    private fun t(tag: String, w: Double = 1.0) = RecoDb.Tag(tag, "era", w, 0)

    @Test fun labelsReadEraGenreAndLanguage() {
        val l = VibeStore.labelsOf(listOf(t("ERA_90"), t("G_SUFI"), t("LANG_hindi", 0.6), t("ROMANTIC")))
        assertEquals(2, l.era); assertEquals(setOf("sufi"), l.genres); assertEquals("hindi", l.language)
        // On a "new this week" playlist: new, even if a decade playlist also has it.
        assertEquals(5, VibeStore.labelsOf(listOf(t("ERA_00", 1.0), t("ERA_NEW", 0.8))).era)
        assertEquals(4, VibeStore.labelsOf(listOf(t("ERA_90", 1.0), t("ERA_10", 0.7))).era) // a 2015 hit on a 90s list
    }

    @Test fun everyLanguageLearnsMoodsErasAndGenres() {
        val feeds = Trainer.feeds("hindi")
        assertTrue(feeds.any { it.query == "90s bollywood songs" && it.tag == "ERA_90" })
        assertTrue(feeds.any { it.query == "hindi rap hip hop" && it.kind == "genre" })
        assertEquals(7, feeds.count { it.kind == "mood" })
        assertEquals(6, feeds.count { it.kind == "era" })
        assertTrue(feeds.any { it.tag == "ERA_5060" })
        assertTrue(Trainer.feeds("punjabi").any { it.query == "90s punjabi songs" })
    }

    // ── Rap after rap, classics after classics ──
    @Test fun strongGenreCarriesOn() {
        assertTrue(QueuePicker.keepsGenre(setOf("rap"), setOf("rap", "pop"), strict = true))
        assertFalse("romantic pop after rap", QueuePicker.keepsGenre(setOf("rap"), setOf("pop"), strict = true))
        assertTrue("unknown genre can't fail", QueuePicker.keepsGenre(setOf("rap"), emptySet(), strict = true))
        assertTrue("looser step lets go", QueuePicker.keepsGenre(setOf("rap"), setOf("pop"), strict = false))
        assertTrue("pop isn't a strong genre", QueuePicker.keepsGenre(setOf("pop"), setOf("folk"), strict = true))
    }

    @Test fun eraAndGenreReadFromSuggestions() {
        assertEquals(0, QueuePicker.stationEra(listOf(0, 0, 1, 0, 0)))
        assertEquals(null, QueuePicker.stationEra(listOf(0, 0, 1, 1, 2))) // 2 of 5 isn't enough
        assertEquals(null, QueuePicker.stationEra(listOf(0, 3)))
        assertEquals(null, QueuePicker.stationEra(listOf(0, 1, 2, 3, 4)))
        assertEquals(setOf("rap"), QueuePicker.stationGenres(listOf(setOf("rap"), setOf("rap", "pop"), setOf("rap"), emptySet())))
    }

    @Test fun devotionalAndRapNeverSlipIntoADanceQueue() {
        val dance = setOf("dance", "pop")
        assertFalse("chant after dance", QueuePicker.keepsGenre(dance, setOf("devotional"), strict = false))
        assertFalse("ghazal after dance", QueuePicker.keepsGenre(dance, setOf("ghazal"), strict = false))
        assertTrue("chant after chant", QueuePicker.keepsGenre(setOf("devotional"), setOf("devotional"), strict = true))
        assertFalse("rap after dance (strict)", QueuePicker.keepsGenre(dance, setOf("rap"), strict = true))
        assertTrue("rap after an unknown genre", QueuePicker.keepsGenre(emptySet(), setOf("rap"), strict = true))
        // A stotram's main feel makes it devotional even without a label.
        assertEquals(setOf("devotional"), Trainer.genresOf(emptySet(), v(0.8, null, Feel.DEVOTIONAL)))
    }

    @Test fun steeringKeepsTheKindOfSong() {
        // More energy from a romantic song may go livelier, but not to a party song.
        assertFalse(QueuePicker.fitsAnchor(romantic, v(0.80, 120.0, Feel.PARTY), 1, true, Steer.ENERGY))
        assertTrue(QueuePicker.fitsAnchor(romantic, v(0.70, 110.0, Feel.ROMANTIC), 1, true, Steer.ENERGY))
    }

    @Test fun unknownEraWaitsAfterAClassic() {
        val classic = Anchor(v(0.5, 100.0, Feel.ROMANTIC), era = 1)
        assertFalse(QueuePicker.fitsAnchor(classic, v(0.5, 100.0, Feel.ROMANTIC), era = null, strict = true, steer = null))
        assertTrue(QueuePicker.fitsAnchor(classic, v(0.5, 100.0, Feel.ROMANTIC), era = null, strict = false, steer = null))
        // After a new song an unknown era is fine.
        assertTrue(QueuePicker.fitsAnchor(Anchor(v(0.5, 100.0, Feel.ROMANTIC), era = 5), v(0.5, 100.0, Feel.ROMANTIC), null, true, null))
    }

    @Test fun onlyConfirmedSongsJoin() {
        val start = Anchor(v(0.5, 100.0, Feel.ROMANTIC), era = 2)
        fun c(vibe: Vibe, era: Int?) = Features(Song("x", Candidate("t", "a")), 0.5, 0.0, vibe, "hindi", 0.0, 0.0, era = era)
        assertTrue(QueuePicker.confirmed(start, c(v(0.5, null, Feel.ROMANTIC), 2)))
        assertFalse("era unknown", QueuePicker.confirmed(start, c(v(0.5, null, Feel.ROMANTIC), null)))
        assertFalse("feel unknown", QueuePicker.confirmed(start, c(v(null, null), 2)))
        // A start with nothing known asks nothing.
        assertTrue(QueuePicker.confirmed(Anchor(null), c(v(null, null), null)))
    }
}

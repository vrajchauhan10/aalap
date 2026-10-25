package com.music.aalap

import com.music.aalap.data.recommendation.engine.Candidate
import com.music.aalap.data.recommendation.engine.Feedback
import com.music.aalap.data.recommendation.engine.Feel
import com.music.aalap.data.recommendation.engine.Song
import com.music.aalap.data.recommendation.engine.Vibe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** "More / less like this" shown as artists, kinds of song and pace. */
class FeedbackSummaryTest {
    private fun song(id: String, artists: String) = Song(id, Candidate(title = id, artists = artists))
    private fun vibe(feel: Feel, energy: Double, bpm: Double) = Vibe(mapOf(feel to 1.0), 0.9, audioEnergy = energy, bpm = bpm)

    @Test fun artistsFeelsAndPace() {
        val songs = mapOf(
            "a" to song("a", "Arijit Singh, Pritam"), "b" to song("b", "Arijit Singh"), "c" to song("c", "Diljit Dosanjh"),
        )
        val vibes = mapOf("a" to vibe(Feel.ROMANTIC, 0.4, 90.0), "b" to vibe(Feel.ROMANTIC, 0.35, 96.0), "c" to vibe(Feel.PARTY, 0.9, 128.0))
        val s = Feedback.summarise(listOf("a", "b", "c"), songs, vibes)
        assertEquals(3, s.songs)
        assertEquals("Arijit Singh", s.artists.first().second)
        assertEquals(2, s.artists.first().third)
        assertEquals(Feel.ROMANTIC to 2, s.feels.first())
        assertEquals(96.0, s.bpm!!, 0.01)
    }

    @Test fun nothingYet() {
        val s = Feedback.summarise(emptyList(), emptyMap(), emptyMap())
        assertEquals(0, s.songs)
        assertNull(s.pace)
    }
}

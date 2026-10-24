package com.music.aalap.data.recommendation

import com.music.aalap.data.recommendation.engine.Candidate
import com.music.aalap.data.recommendation.engine.Feedback
import com.music.aalap.data.recommendation.engine.Feel
import com.music.aalap.data.recommendation.engine.LanguageGuess
import com.music.aalap.data.recommendation.engine.PickState
import com.music.aalap.data.recommendation.engine.QueuePicker
import com.music.aalap.data.recommendation.engine.Session
import com.music.aalap.data.recommendation.engine.SessionSong
import com.music.aalap.data.recommendation.engine.Song
import com.music.aalap.data.recommendation.engine.Targets
import com.music.aalap.data.recommendation.engine.TasteProfile
import com.music.aalap.data.recommendation.engine.Vibe
import com.music.aalap.data.recommendation.engine.Features
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The 5.0 pieces on their own: listening length, feedback, smooth flow, language guess, Blend codes. */
class Reco5UnitTest {

    @Test fun listeningLength() {
        assertEquals(1.0, TasteProfile.playWeight(false, 200_000, 1.0), 1e-9)
        assertTrue(TasteProfile.playWeight(false, 100_000, 0.5) in 0.6..0.7)
        assertEquals(-0.9, TasteProfile.playWeight(true, 5_000, 0.03), 1e-9)
        assertEquals(-0.6, TasteProfile.playWeight(true, 25_000, 0.1), 1e-9)
    }

    private fun song(id: String, artist: String) = Song(id, Candidate(title = id, artists = artist))
    private fun vibe(f: Feel) = Vibe.of(mapOf(f to 3.0))

    @Test fun feedback() {
        val sad = song("s1", "Sad Singer")
        val fb = Feedback.build(listOf("LESS" to "s1", "BLOCK_ARTIST" to "blocked guy", "BLOCK_SONG" to "x"), mapOf("s1" to sad), mapOf("s1" to vibe(Feel.SAD)))
        assertTrue(fb.blocked(song("y", "Blocked Guy")))
        assertTrue(fb.blocked(song("z", "Pritam, Blocked Guy")))                     // featured, not first
        assertTrue(fb.blocked(song("x", "Anyone")))
        assertTrue(fb.factor(song("s2", "Sad Singer"), vibe(Feel.SAD)) < 0.4)        // same artist and feel
        assertTrue(fb.factor(song("s3", "Other"), vibe(Feel.SAD)) < 0.7)             // same feel
        assertEquals(1.0, fb.factor(song("s4", "Other"), vibe(Feel.PARTY)), 1e-9)     // untouched
        val more = Feedback.build(listOf("MORE" to "p1"), mapOf("p1" to song("p1", "Party Guy")), mapOf("p1" to vibe(Feel.PARTY)))
        assertTrue(more.factor(song("p2", "Party Guy"), vibe(Feel.PARTY)) > 1.2)
    }

    private fun f(id: String, e: Double, bpm: Double) = Features(
        song = song(id, "artist $id"), global = 0.8, session = 0.5, vibe = Vibe.of(mapOf(Feel.HAPPY to 3.0)).withAudio(e, bpm),
        language = "", taste = 0.5, habit = 0.0,
    )

    @Test fun smoothFlow() {
        val prev = Vibe.of(mapOf(Feel.HAPPY to 3.0)).withAudio(0.4, 100.0)
        val st = PickState(lastVibe = prev)
        val sess = Session(listOf(SessionSong("a", "x", prev, "", 1.0)))
        val near = QueuePicker.score(f("near", 0.45, 102.0), sess, false, st, Targets())
        val jump = QueuePicker.score(f("jump", 0.95, 150.0), sess, false, st, Targets())
        val half = QueuePicker.score(f("half", 0.45, 51.0), sess, false, st, Targets())   // half time flows
        assertTrue(near.final > jump.final)
        assertTrue("energy-jump" in jump.tags && "tempo-jump" in jump.tags)
        assertTrue("tempo-jump" !in half.tags)
    }

    @Test fun languageGuess() {
        assertEquals("punjabi", LanguageGuess.of("Jatt Da Muqabla"))
        assertEquals("punjabi", LanguageGuess.of("Sohneya Ve"))
        assertEquals("gujarati", LanguageGuess.of("Chogada", "Garba Night"))
        assertEquals("hindi", LanguageGuess.of("Tum Hi Ho"))
        assertEquals("hindi", LanguageGuess.of("Dil Ka Kya Hai"))
        assertEquals("english", LanguageGuess.of("The Night We Met"))
        assertEquals("hindi", LanguageGuess.of("तुम ही हो"))
        assertEquals("", LanguageGuess.of("Kesariya"))
    }

    @Test fun blendCode() {
        val card = Blend.TasteCard("Vraj", listOf("Arijit Singh", "Diljit Dosanjh"), mapOf("ROMANTIC" to 1.0, "PARTY" to 0.7), listOf("Hindi"),
            listOf(Triple("Kesariya", "Pritam, Arijit Singh", "abc123"), Triple("Lover", "Diljit Dosanjh", "")))
        val code = Blend.encode(card)
        assertTrue(code.startsWith(Blend.PREFIX) && code.length < 600)
        assertEquals(card, Blend.decode("My Aalap taste: open Aalap…\n\n$code\nthanks!"))
        assertNull(Blend.decode("no code here"))
        assertNull(Blend.decode(Blend.PREFIX + "garbage!!"))
    }
}

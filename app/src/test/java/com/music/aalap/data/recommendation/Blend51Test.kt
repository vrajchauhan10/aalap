package com.music.aalap.data.recommendation

import com.music.aalap.ui.components.PosterRotation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 5.1: the Blend match score and the poster rotation. */
class Blend51Test {
    private fun card(artists: List<String>, feels: Map<String, Double>, langs: List<String>, songs: List<String> = emptyList(), id: String = "x") =
        Blend.TasteCard("Friend", artists, feels, langs, songs.map { Triple(it, "A", "") }, id)

    @Test fun matchScoreFollowsTaste() {
        val me = card(listOf("Arijit Singh", "Pritam", "Atif Aslam", "Mohit Chauhan", "Shreya Ghoshal"), mapOf("ROMANTIC" to 1.0, "SAD" to 0.7), listOf("Hindi"), listOf("Tum Hi Ho", "Kesariya", "Raabta"))
        val twin = card(listOf("Arijit Singh", "Pritam", "Atif Aslam", "Shreya Ghoshal", "KK"), mapOf("ROMANTIC" to 0.9, "SAD" to 0.8), listOf("Hindi"), listOf("Tum Hi Ho", "Kesariya", "Raabta"))
        val some = card(listOf("Diljit Dosanjh", "Arijit Singh", "AP Dhillon"), mapOf("ROMANTIC" to 0.6, "PARTY" to 1.0), listOf("Punjabi", "Hindi"))
        val opposite = card(listOf("Metallica", "Slayer", "Iron Maiden"), mapOf("ENERGETIC" to 1.0), listOf("English"))
        val s1 = Blend.match(me, twin).score; val s2 = Blend.match(me, some).score; val s3 = Blend.match(me, opposite).score
        assertTrue("twin $s1", s1 >= 85)
        assertTrue("some $s2", s2 in 30..69)
        assertTrue("opposite $s3", s3 < 30)
        assertEquals(listOf("Arijit Singh", "Pritam", "Atif Aslam", "Shreya Ghoshal"), Blend.match(me, twin).sharedArtists)
        assertTrue(kotlin.math.abs(Blend.match(me, some).score - Blend.match(some, me).score) <= 6)   // about the same both ways
        assertEquals("Soulmates", Blend.band(90).first); assertEquals("Opposites attract", Blend.band(10).first)
    }

    @Test fun codeKeepsTheFriendId() {
        val c = card(listOf("A"), mapOf("PARTY" to 1.0), listOf("Hindi"), id = "abc123")
        assertEquals("abc123", Blend.decode(Blend.encode(c))?.id)
    }

    @Test fun postersDontRepeatSoon() {
        val rnd = java.util.Random(7)
        var previous = emptyList<Int>()
        val shown = ArrayList<Int>()
        repeat(6) {
            val round = PosterRotation.newRound(80, previous, rnd)
            assertEquals(80, round.toSet().size)                         // every poster once per round
            if (previous.isNotEmpty()) assertTrue(round != previous)    // a different order each round
            shown += round; previous = round
        }
        // Across round boundaries too, a poster never comes back within 50 opens.
        val lastSeen = HashMap<Int, Int>()
        var minGap = Int.MAX_VALUE
        shown.forEachIndexed { i, p -> lastSeen[p]?.let { minGap = minOf(minGap, i - it) }; lastSeen[p] = i }
        assertTrue("min gap $minGap", minGap >= 50)
    }
}

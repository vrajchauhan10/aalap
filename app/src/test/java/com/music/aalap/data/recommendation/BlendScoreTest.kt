package com.music.aalap.data.recommendation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 5.2: a Blend's score rises with listening, the same on both phones. */
class BlendScoreTest {
    private val today = 20_000L

    @Test fun listeningRaisesTheScore() {
        val match = 56
        assertEquals(56, BlendScore.score(match, emptyMap(), today))
        // 5 songs each, 2 of them the same: up by about 10.
        val week = mapOf(today to BlendScore.Day(setOf("a", "b", "c", "d", "e"), setOf("a", "b", "x", "y", "z")))
        val s = BlendScore.score(match, week, today)
        assertTrue("5 each: $s", s in 64..70)
        // More listening, higher; never past 99.
        val lots = mapOf(today to BlendScore.Day((1..60).map { "m$it" }.toSet(), (1..60).map { "m$it" }.toSet()))
        assertTrue(BlendScore.score(match, lots, today) in 95..99)
        // A week later it has faded back.
        assertEquals(56, BlendScore.score(match, week, today + 7))
    }

    @Test fun sameOnBothPhones() {
        val rec = BlendSync.Shared(
            "a_b", listOf("a", "b"), mapOf("a" to "A", "b" to "B"), emptyMap(), today, emptyList(),
            mapOf(today to mapOf("a" to setOf("s1", "s2"), "b" to setOf("s2", "s3"))), emptyMap(), true,
        )
        assertEquals(rec.points("a", today), BlendSync.Shared::points.let { rec.points("b", today) }.let { BlendScore.Points(it.theirs, it.mine, it.together) })
        assertEquals(rec.score("a", today), rec.score("b", today))
    }

    @Test fun pointsToTheNextBand() {
        val need = BlendScore.pointsFor(56, 70)!!
        assertTrue(BlendScore.score(56, need) >= 70)
        assertTrue(BlendScore.score(56, need - 1) < 70)
        assertEquals(70, BlendScore.nextBand(56))
    }

    @Test fun recordRoundTrip() {
        val rec = BlendSync.Shared(
            "a_b", listOf("a", "b"), mapOf("a" to "Vraj", "b" to "Rahul"), mapOf("a" to "AALAP-BLEND:x"), today,
            listOf(BlendSync.Song("Tum Hi Ho", "Arijit Singh", "Aashiqui 2", "c", "sid1", 250_000, "a")),
            mapOf(today to mapOf("a" to setOf("sid1"))), mapOf(today to 61), true,
        )
        assertEquals(rec, BlendSync.fromMap("a_b", BlendSync.toMap(rec)))
    }
}

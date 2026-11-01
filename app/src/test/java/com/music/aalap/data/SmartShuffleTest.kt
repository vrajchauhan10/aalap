package com.music.aalap.data

import com.music.aalap.data.preferences.SmartShuffle
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.random.Random

/** Shuffle plays what you haven't heard lately first, and never drops a song. */
class SmartShuffleTest {
    private val hour = 3_600_000L
    private val now = 10_000 * hour

    @Test fun recentAndTiredSongsGoToTheEndInOrder() {
        val heard = mapOf("b" to now - 2 * hour, "c" to now - 30 * hour, "d" to now - 100 * hour)
        val tired = setOf("e")
        repeat(20) { seed ->
            val out = SmartShuffle.order(listOf("a", "b", "c", "d", "e", "f"), { heard[it] }, { it in tired }, now, Random(seed))
            assertEquals(setOf("a", "d", "f"), out.take(3).toSet()) // fresh, incl. heard 4 days ago
            assertEquals(listOf("c", "b"), out.subList(3, 5)) // heard longest ago first
            assertEquals("e", out.last())
        }
    }

    @Test fun keepsEverySongOnce() {
        val songs = (1..50).map { "s$it" }
        val out = SmartShuffle.order(songs, { if (it.length == 2) now - hour else null }, { it == "s10" }, now)
        assertEquals(songs.sorted(), out.sorted())
    }

    @Test fun nothingHeardIsAPlainShuffle() {
        val songs = (1..30).map { "s$it" }
        val out = SmartShuffle.order(songs, { null }, { false }, now, Random(1))
        assertEquals(songs.shuffled(Random(1)), out)
    }
}

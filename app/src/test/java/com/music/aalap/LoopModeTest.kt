package com.music.aalap

import com.music.aalap.di.RepeatMode
import org.junit.Assert.assertEquals
import org.junit.Test

/** What plays when a song ends, in every loop mode. */
class LoopModeTest {

    /** Plays a 3-song queue from the first song for [songs] song-ends; returns what played. */
    private fun play(mode: RepeatMode, songs: Int): Pair<List<String>, RepeatMode> {
        var m = mode; var cur = 0
        val out = mutableListOf("A")
        repeat(songs) {
            val (next, after) = RepeatMode.afterEnd(m, cur, 3)
            m = after
            if (next == null) { out += "radio"; return out to m }
            cur = next; out += "ABC"[cur].toString()
        }
        return out to m
    }

    @Test fun off() = assertEquals(listOf("A", "B", "C", "radio"), play(RepeatMode.OFF, 5).first)

    @Test fun songForever() = assertEquals(listOf("A", "A", "A", "A"), play(RepeatMode.SONG, 3).first)

    @Test fun songOnce() {
        val (played, mode) = play(RepeatMode.SONG_ONCE, 5)
        assertEquals(listOf("A", "A", "B", "C", "radio"), played)
        assertEquals(RepeatMode.OFF, mode)
    }

    @Test fun playlistForever() = assertEquals(listOf("A", "B", "C", "A", "B", "C", "A"), play(RepeatMode.LIST, 6).first)

    @Test fun playlistOnce() {
        val (played, mode) = play(RepeatMode.LIST_ONCE, 9)
        assertEquals(listOf("A", "B", "C", "A", "B", "C", "radio"), played)
        assertEquals(RepeatMode.OFF, mode)
    }

    @Test fun emptyQueue() = assertEquals(null, RepeatMode.afterEnd(RepeatMode.LIST, -1, 0).first)
}

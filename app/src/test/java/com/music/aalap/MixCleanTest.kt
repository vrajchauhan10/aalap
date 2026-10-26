package com.music.aalap

import com.music.aalap.data.recommendation.engine.MixClean
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Every mix: each song once, and the song itself rather than an altered take. */
class MixCleanTest {
    private data class S(val title: String, val singer: String, val album: String = "")

    private fun clean(list: List<S>) = MixClean.clean(list, { it.title }, { it.singer }, { it.album }).map { it.title }

    @Test fun sameSongListedTwiceIsOnce() {
        val out = clean(listOf(S("Kesariya", "Pritam, Arijit Singh"), S("Kesariya (From \"Brahmastra\")", "Pritam, Arijit Singh"), S("Apna Bana Le", "Sachin-Jigar")))
        assertEquals(listOf("Kesariya", "Apna Bana Le"), out)
    }

    @Test fun alteredTakesGo() {
        val out = clean(listOf(S("Tum Hi Ho", "Arijit Singh"), S("Tum Hi Ho (Lofi Flip)", "Arijit Singh, Lofi Boy"), S("Raataan Lambiyan - Slowed + Reverb", "Jubin Nautiyal"), S("Believer", "Imagine Dragons")))
        assertEquals(listOf("Tum Hi Ho", "Believer"), out)
    }

    @Test fun anAllLofiListStays() {
        val list = listOf(S("Song A (Lofi)", "X"), S("Song B (Lofi)", "Y"), S("Song C", "Z"))
        assertEquals(3, clean(list).size)
    }

    @Test fun differentSongsSameNameDifferentArtistStay() {
        assertEquals(2, clean(listOf(S("Tere Liye", "Atif Aslam"), S("Tere Liye", "Lata Mangeshkar"))).size)
    }

    @Test fun alteredByWord() {
        assertTrue(MixClean.altered("Channa Mereya - Unplugged Remix"))
        assertFalse(MixClean.altered("Kesariya"))
        assertFalse(MixClean.altered("Discover Your Love"))
    }
}

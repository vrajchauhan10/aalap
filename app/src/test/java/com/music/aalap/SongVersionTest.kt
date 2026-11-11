package com.music.aalap

import com.music.aalap.data.recommendation.engine.SongVersion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The same take of a song, every time: unplugged when asked, never an unasked remix. */
class SongVersionTest {
    @Test fun kinds() {
        assertEquals(setOf("unplugged"), SongVersion.of("Paro (Unplugged)"))
        assertEquals(setOf("remix"), SongVersion.of("KALYANI - Remix"))
        assertEquals(setOf("reprise"), SongVersion.of("Bulleya (Reprise)"))
        assertEquals(setOf("lofi", "slowed"), SongVersion.of("Raataan Lambiyan (Lofi Flip) Slowed + Reverb"))
        assertEquals(emptySet<String>(), SongVersion.of("Paro"))
    }

    @Test fun wholeWordsOnly() {
        assertEquals(emptySet<String>(), SongVersion.of("Deliverance"))
        assertEquals(emptySet<String>(), SongVersion.of("Believer"))
        assertEquals(emptySet<String>(), SongVersion.of("Discover Your Love"))
        assertEquals(setOf("live"), SongVersion.of("Kesariya (Live at Mumbai)"))
    }

    @Test fun sameTake() {
        assertTrue(SongVersion.same("Paro (Unplugged)", "Paro - Unplugged"))
        assertFalse(SongVersion.same("Paro (Unplugged)", "Paro"))
        assertFalse(SongVersion.same("KALYANI - Remix", "KALYANI"))
        assertFalse(SongVersion.same("Bulleya", "Bulleya (Reprise)"))
        assertTrue(SongVersion.same("Kesariya (From \"Brahmastra\")", "Kesariya"))
        assertFalse(SongVersion.same("The Ghost Intervention - Demo", "The Ghost Intervention"))
        assertTrue(SongVersion.same("The Ghost Intervention - Demo", "The Ghost Intervention (Demo)"))
        assertEquals(emptySet<String>(), SongVersion.of("Democracy"))
    }
}

class RecordingLengthTest {
    @org.junit.Test fun lengths() {
        val v = com.music.aalap.data.recommendation.engine.SongVersion
        org.junit.Assert.assertFalse(v.differentRecording(262_000, 265_000))   // same song, encoder padding
        org.junit.Assert.assertFalse(v.differentRecording(262_000, 272_000))   // 10 s: within 12 s
        org.junit.Assert.assertTrue(v.differentRecording(262_000, 330_000))    // music video with intro
        org.junit.Assert.assertTrue(v.differentRecording(262_000, 30_000))     // a clip
        org.junit.Assert.assertFalse(v.differentRecording(0, 30_000))          // unknown length: no verdict
        org.junit.Assert.assertFalse(v.differentRecording(600_000, 650_000))   // long song: within a tenth
    }
}

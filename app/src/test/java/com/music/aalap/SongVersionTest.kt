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
    }
}

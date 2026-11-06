package com.music.aalap

import com.music.aalap.ui.components.shortArtists
import org.junit.Assert.assertEquals
import org.junit.Test

class ShortArtistsTest {
    @Test fun short() {
        assertEquals("A, B", shortArtists("A, B"))
        assertEquals("A, B & 2 more", shortArtists("A, B, C, D"))
        assertEquals("A", shortArtists("A, A"))
        assertEquals("", shortArtists(""))
    }
}

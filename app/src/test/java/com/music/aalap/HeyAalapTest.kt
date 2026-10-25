package com.music.aalap

import com.music.aalap.di.HeyAalap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** How "Hey Aalap" is recognised in what the speech engine heard. */
class HeyAalapTest {
    @Test fun wakeWords() {
        for (t in listOf("hey aalap", "Hey Alap", "ok aalap", "okay alaap", "hey all up", "hey a lap", "hey olap", "OK Aalap."))
            assertTrue(t, HeyAalap.hear(t).woke)
        for (t in listOf("hey apple", "hey alexa", "aalap", "play aalap songs", "hey", "hey lap top", "okay a laptop"))
            assertFalse(t, HeyAalap.hear(t).woke)
    }

    @Test fun requestInOneGo() {
        assertEquals("play kesariya", HeyAalap.hear("hey aalap play kesariya").request)
        assertEquals("some party songs", HeyAalap.hear("so ok alap some party songs").request)
        assertEquals("", HeyAalap.hear("hey aalap").request)
    }
}

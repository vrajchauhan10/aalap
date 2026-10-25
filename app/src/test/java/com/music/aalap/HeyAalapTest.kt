package com.music.aalap

import com.music.aalap.di.HeyAalap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** How "Hey Aalap" is recognised in what the speech engine heard. */
class HeyAalapTest {
    @Test fun wakeWords() {
        for (t in listOf("hey aalap", "Hey Alap", "ok aalap", "okay alaap", "hey all up", "hey a lap", "hey olap", "OK Aalap.", "ki HAL aap play kesariya", "haal aap", "hey all app", "hey alarm play kesariya", "hey all of play kesariya", "ke all of play kesariya", "a alarm play kesariya"))
            assertTrue(t, HeyAalap.hear(t).woke)
        for (t in listOf("hey apple", "hey alexa", "aalap", "play aalap songs", "hey", "hey lap top", "okay a laptop", "hello world", "the hall was empty", "help me", "set an alarm for six", "all of us play together"))
            assertFalse(t, HeyAalap.hear(t).woke)
    }

    @Test fun requestInOneGo() {
        assertEquals("play kesariya", HeyAalap.hear("hey aalap play kesariya").request)
        assertEquals("some party songs", HeyAalap.hear("so ok alap some party songs").request)
        assertEquals("", HeyAalap.hear("hey aalap").request)
    }
}

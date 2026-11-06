package com.music.aalap

import com.music.aalap.ui.screens.shortCount
import org.junit.Assert.assertEquals
import org.junit.Test

class ShortCountTest {
    @Test fun readable() {
        assertEquals("347M", shortCount(347_802_860))
        assertEquals("1.5B", shortCount(1_520_000_000))
        assertEquals("2B", shortCount(2_000_000_000))
        assertEquals("45K", shortCount(45_200))
        assertEquals("999", shortCount(999))
    }
}

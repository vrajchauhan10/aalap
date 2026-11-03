package com.music.aalap.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Test

/** The phone's text size is followed, inside the range the layouts are built for. */
class FontFitTest {
    @Test fun normalSizesAreKept() {
        assertEquals(1f, FontFit.scale(1f))
        assertEquals(1.15f, FontFit.scale(1.15f))
        assertEquals(0.9f, FontFit.scale(0.9f))
    }

    @Test fun extremesAreFitted() {
        assertEquals(1.3f, FontFit.scale(2f)) // Android 14's largest
        assertEquals(0.85f, FontFit.scale(0.7f))
        assertEquals(1f, FontFit.scale(Float.NaN))
    }
}

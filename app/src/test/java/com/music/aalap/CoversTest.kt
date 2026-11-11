package com.music.aalap

import com.music.aalap.data.api.Covers
import org.junit.Assert.assertEquals
import org.junit.Test

class CoversTest {
    @Test fun large() {
        assertEquals("https://lh3.googleusercontent.com/abc=w1080-h1080-l90-rj", Covers.hd("https://lh3.googleusercontent.com/abc=w120-h120-l90-rj"))
        assertEquals("https://i.scdn.co/image/ab67616d0000b273xyz", Covers.hd("https://i.scdn.co/image/ab67616d00001e02xyz"))
        assertEquals("https://c.saavncdn.com/1/a-500x500.jpg", Covers.hd("https://c.saavncdn.com/1/a-150x150.jpg"))
        assertEquals("https://i.ytimg.com/vi/x/hqdefault.jpg", Covers.hd("https://i.ytimg.com/vi/x/mqdefault.jpg"))
        assertEquals("", Covers.hd(""))
    }
}

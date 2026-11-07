package com.music.aalap

import com.music.aalap.di.CappedMap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CappedMapTest {
    @Test fun staysBoundedAndKeepsNewest() {
        val m = CappedMap<Int, Int>(100)
        for (i in 0 until 1000) m[i] = i
        assertTrue(m.size <= 100)
        assertEquals(999, m[999])
    }
}
